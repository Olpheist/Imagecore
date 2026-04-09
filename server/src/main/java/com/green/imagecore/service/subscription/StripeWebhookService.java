package com.green.imagecore.service.subscription;

import com.green.imagecore.entities.subscription.BillingEvent;
import com.green.imagecore.entities.subscription.SubscriptionStatus;
import com.green.imagecore.entities.subscription.SubscriptionTier;
import com.green.imagecore.entities.subscription.SubscriptionTierCode;
import com.green.imagecore.entities.subscription.UserSubscription;
import com.green.imagecore.repositories.subscription.BillingEventRepository;
import com.green.imagecore.repositories.subscription.SubscriptionTierRepository;
import com.green.imagecore.repositories.subscription.UserSubscriptionRepository;
import com.green.imagecore.service.UserService;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.InvoiceLineItem;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    private final BillingEventRepository billingEventRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionTierRepository subscriptionTierRepository;
    private final UserService userService;

    @Transactional
    public void handleEvent(Event event, String rawPayload) {
        log.info("Received Stripe event id={} type={}", event.getId(), event.getType());

        if (event.getId() != null && billingEventRepository.existsByProviderEventId(event.getId())) {
            log.info("Stripe event {} already processed; skipping", event.getId());
            return;
        }

        BillingEvent billingEvent = BillingEvent.builder()
                .providerEventId(event.getId())
                .eventType(event.getType())
                .payload(rawPayload)
                .build();

        billingEventRepository.save(billingEvent);
        log.info("Saved billing event row for Stripe event id={} type={}", event.getId(), event.getType());

        try {
            switch (event.getType()) {
                case "checkout.session.completed" -> handleCheckoutSessionCompleted(event, billingEvent);
                case "customer.subscription.created", "customer.subscription.updated" -> handleSubscriptionUpsert(event, billingEvent);
                case "customer.subscription.deleted" -> handleSubscriptionDeleted(event, billingEvent);
                case "invoice.paid" -> handleInvoicePaid(event, billingEvent);
                case "invoice.payment_failed" -> handleInvoicePaymentFailed(event, billingEvent);
                default -> log.info("Ignoring unhandled Stripe event type={}", event.getType());
            }

            billingEvent.setProcessedAt(Instant.now());
            billingEventRepository.save(billingEvent);
            log.info("Marked Stripe event id={} type={} as processed", event.getId(), event.getType());
        } catch (RuntimeException e) {
            log.error("Failed processing Stripe event id={} type={}", event.getId(), event.getType(), e);
            throw e;
        }
    }

    private void handleCheckoutSessionCompleted(Event event, BillingEvent billingEvent) {
        log.info("Handling checkout.session.completed for event id={}", event.getId());

        Session session = deserializeStripeObject(event, Session.class)
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to deserialize Stripe checkout session for event " + event.getId()
                ));

        String providerSubscriptionId = session.getSubscription();
        if (providerSubscriptionId == null || providerSubscriptionId.isBlank()) {
            log.warn("checkout.session.completed missing subscription id for session id={}", session.getId());
            return;
        }

        Long userId = getUserIdFromCheckoutSession(session);
        if (userId == null) {
            log.warn("checkout.session.completed missing userId metadata for session id={}", session.getId());
            return;
        }

        SubscriptionTier proTier = subscriptionTierRepository.findByCode(SubscriptionTierCode.PRO)
                .orElseThrow(() -> new IllegalStateException("PRO subscription tier not found"));

        UserSubscription userSubscription = userSubscriptionRepository
                .findByUserId(userId)
                .orElseGet(UserSubscription::new);

        userSubscription.setUser(userService.findById(userId));
        userSubscription.setTier(proTier);
        userSubscription.setProviderCustomerId(session.getCustomer());
        userSubscription.setProviderSubscriptionId(providerSubscriptionId);
        userSubscription.setAutoRenew(true);

        try {
            Subscription subscription = Subscription.retrieve(providerSubscriptionId);

            if (subscription.getItems() != null
                    && subscription.getItems().getData() != null
                    && !subscription.getItems().getData().isEmpty()) {
                SubscriptionItem firstItem = subscription.getItems().getData().get(0);

                if (firstItem.getPrice() != null) {
                    userSubscription.setProviderPriceId(firstItem.getPrice().getId());
                }

                userSubscription.setCurrentPeriodStart(toInstant(firstItem.getCurrentPeriodStart()));
                userSubscription.setCurrentPeriodEnd(toInstant(firstItem.getCurrentPeriodEnd()));
            }

            userSubscription.setCancelAt(toInstant(subscription.getCancelAt()));
            userSubscription.setCanceledAt(toInstant(subscription.getCanceledAt()));
            userSubscription.setEndedAt(toInstant(subscription.getEndedAt()));
            userSubscription.setAutoRenew(!Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()));
        } catch (StripeException e) {
            log.warn(
                    "Unable to retrieve Stripe subscription {} during checkout.session.completed",
                    providerSubscriptionId,
                    e
            );
        }

        userSubscriptionRepository.save(userSubscription);
        billingEvent.setUserSubscription(userSubscription);

        log.info(
                "Upgraded user subscription id={} to PRO after Stripe subscription {} was completed",
                userSubscription.getId(),
                session.getSubscription()
        );
    }

    private void handleSubscriptionUpsert(Event event, BillingEvent billingEvent) {
        log.info("Handling subscription upsert for event id={} type={}", event.getId(), event.getType());

        Subscription subscription = deserializeStripeObject(event, Subscription.class)
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to deserialize Stripe subscription for event " + event.getId()
                ));

        Long currentPeriodStart = null;
        Long currentPeriodEnd = null;
        String providerPriceId = null;

        if (subscription.getItems() != null
                && subscription.getItems().getData() != null
                && !subscription.getItems().getData().isEmpty()) {
            SubscriptionItem firstItem = subscription.getItems().getData().get(0);
            currentPeriodStart = firstItem.getCurrentPeriodStart();
            currentPeriodEnd = firstItem.getCurrentPeriodEnd();
            if (firstItem.getPrice() != null) {
                providerPriceId = firstItem.getPrice().getId();
            }
        }

        Optional<UserSubscription> existingSubscription = userSubscriptionRepository
                .findByProviderSubscriptionId(subscription.getId());

        if (existingSubscription.isEmpty()) {
            log.warn(
                    "Skipping subscription event because UserSubscription not created yet: {}",
                    subscription.getId()
            );
            return;
        }

        UserSubscription userSubscription = existingSubscription.get();

        userSubscription.setProviderCustomerId(subscription.getCustomer());
        userSubscription.setProviderPriceId(providerPriceId);
        userSubscription.setAutoRenew(!Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()));
        userSubscription.setCurrentPeriodStart(toInstant(currentPeriodStart));
        userSubscription.setCurrentPeriodEnd(toInstant(currentPeriodEnd));
        userSubscription.setCancelAt(toInstant(subscription.getCancelAt()));
        userSubscription.setCanceledAt(toInstant(subscription.getCanceledAt()));
        userSubscription.setEndedAt(toInstant(subscription.getEndedAt()));

        userSubscriptionRepository.save(userSubscription);
        billingEvent.setUserSubscription(userSubscription);
    }

    private void handleSubscriptionDeleted(Event event, BillingEvent billingEvent) {
        log.info("Handling customer.subscription.deleted for event id={}", event.getId());

        Subscription subscription = deserializeStripeObject(event, Subscription.class)
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to deserialize Stripe subscription deletion for event " + event.getId()
                ));

        UserSubscription userSubscription = userSubscriptionRepository
                .findByProviderSubscriptionId(subscription.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No UserSubscription found for deleted provider subscription id " + subscription.getId()
                ));

        SubscriptionTier freeTier = subscriptionTierRepository.findByCode(SubscriptionTierCode.FREE)
                .orElseThrow(() -> new IllegalStateException("FREE subscription tier not found"));

        userSubscription.setTier(freeTier);
        userSubscription.setProviderCustomerId(subscription.getCustomer());
        userSubscription.setProviderSubscriptionId(null);
        userSubscription.setProviderPriceId(null);
        userSubscription.setCurrentPeriodStart(null);
        userSubscription.setCurrentPeriodEnd(null);
        userSubscription.setAutoRenew(false);
        userSubscription.setCancelAt(toInstant(subscription.getCancelAt()));
        userSubscription.setCanceledAt(toInstant(subscription.getCanceledAt()));
        userSubscription.setEndedAt(
                toInstant(subscription.getEndedAt()) != null ? toInstant(subscription.getEndedAt()) : Instant.now()
        );

        userSubscriptionRepository.save(userSubscription);
        billingEvent.setUserSubscription(userSubscription);

        log.info(
                "Downgraded user subscription id={} to FREE after Stripe subscription {} was deleted",
                userSubscription.getId(),
                subscription.getId()
        );
    }

    private void handleInvoicePaid(Event event, BillingEvent billingEvent) {
        log.info("Handling invoice.paid for event id={}", event.getId());

        Invoice invoice = deserializeStripeObject(event, Invoice.class)
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to deserialize Stripe invoice for event " + event.getId()
                ));

        String providerSubscriptionId = getInvoiceSubscriptionId(invoice);

        if (providerSubscriptionId == null || providerSubscriptionId.isBlank()) {
            log.warn("invoice.paid missing subscription id for invoice id={}", invoice.getId());
            return;
        }

        UserSubscription userSubscription = userSubscriptionRepository
                .findByProviderSubscriptionId(providerSubscriptionId)
                .orElseThrow(() -> new IllegalStateException(
                        "No UserSubscription found for invoice subscription id " + providerSubscriptionId
                ));

        userSubscription.setProviderCustomerId(invoice.getCustomer());

        if (invoice.getLines() != null
                && invoice.getLines().getData() != null
                && !invoice.getLines().getData().isEmpty()) {
            InvoiceLineItem firstLine = invoice.getLines().getData().get(0);
            if (firstLine.getPeriod() != null) {
                userSubscription.setCurrentPeriodStart(toInstant(firstLine.getPeriod().getStart()));
                userSubscription.setCurrentPeriodEnd(toInstant(firstLine.getPeriod().getEnd()));
            }
        }

        userSubscriptionRepository.save(userSubscription);
        billingEvent.setUserSubscription(userSubscription);
    }

    private void handleInvoicePaymentFailed(Event event, BillingEvent billingEvent) {
        log.info("Handling invoice.payment_failed for event id={}", event.getId());

        Invoice invoice = deserializeStripeObject(event, Invoice.class)
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to deserialize Stripe failed invoice for event " + event.getId()
                ));

        String providerSubscriptionId = getInvoiceSubscriptionId(invoice);

        log.info(
                "Deserialized failed Invoice id={} customer={} subscription={} status={} billingReason={} attemptCount={}",
                invoice.getId(),
                invoice.getCustomer(),
                providerSubscriptionId,
                invoice.getStatus(),
                invoice.getBillingReason(),
                invoice.getAttemptCount()
        );

        if (providerSubscriptionId == null || providerSubscriptionId.isBlank()) {
            log.warn("invoice.payment_failed missing subscription id for invoice id={}", invoice.getId());
            return;
        }

        UserSubscription userSubscription = userSubscriptionRepository
                .findByProviderSubscriptionId(providerSubscriptionId)
                .orElseThrow(() -> new IllegalStateException(
                        "No UserSubscription found for failed invoice subscription id " + providerSubscriptionId
                ));

        userSubscription.setProviderCustomerId(invoice.getCustomer());

        userSubscriptionRepository.save(userSubscription);
        billingEvent.setUserSubscription(userSubscription);

        log.info(
                "Marked subscription {} as past due after failed payment attemptCount={}",
                providerSubscriptionId,
                invoice.getAttemptCount()
        );
    }

    private <T extends StripeObject> Optional<T> deserializeStripeObject(Event event, Class<T> clazz) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

        Optional<StripeObject> stripeObjectOptional = deserializer.getObject();
        if (stripeObjectOptional.isPresent() && clazz.isInstance(stripeObjectOptional.get())) {
            return Optional.of(clazz.cast(stripeObjectOptional.get()));
        }

        try {
            StripeObject unsafeObject = deserializer.deserializeUnsafe();
            if (clazz.isInstance(unsafeObject)) {
                return Optional.of(clazz.cast(unsafeObject));
            }
        } catch (EventDataObjectDeserializationException e) {
            log.warn(
                    "Unable to deserialize Stripe event id={} type={} as {}. Raw object: {}",
                    event.getId(),
                    event.getType(),
                    clazz.getSimpleName(),
                    deserializer.getRawJson(),
                    e
            );
        }

        return Optional.empty();
    }

    private String getInvoiceSubscriptionId(Invoice invoice) {
        if (invoice == null || invoice.getParent() == null) {
            return null;
        }

        Invoice.Parent parent = invoice.getParent();

        if (parent.getSubscriptionDetails() == null) {
            return null;
        }

        return parent.getSubscriptionDetails().getSubscription();
    }

    private Long getUserIdFromCheckoutSession(Session session) {
        if (session.getMetadata() == null) {
            return null;
        }

        String userId = session.getMetadata().get("userId");
        if (userId == null || userId.isBlank()) {
            return null;
        }

        return Long.parseLong(userId);
    }

    private Instant toInstant(Long epochSeconds) {
        return epochSeconds == null ? null : Instant.ofEpochSecond(epochSeconds);
    }
}