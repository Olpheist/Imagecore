package com.green.imagecore.service.subscription;

import com.green.imagecore.config.StripeConfig;
import com.green.imagecore.entities.subscription.BillingEvent;
import com.green.imagecore.entities.subscription.SubscriptionStatus;
import com.green.imagecore.entities.subscription.SubscriptionTier;
import com.green.imagecore.entities.subscription.SubscriptionTierCode;
import com.green.imagecore.entities.subscription.UserSubscription;
import com.green.imagecore.repositories.subscription.BillingEventRepository;
import com.green.imagecore.repositories.subscription.SubscriptionTierRepository;
import com.green.imagecore.repositories.subscription.UserSubscriptionRepository;
import com.green.imagecore.service.UserService;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    private final BillingEventRepository billingEventRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionTierRepository subscriptionTierRepository;
    private final UserService userService;
    private final StripeConfig stripeConfig;

    @Transactional
    public void handleEvent(Event event, String rawPayload) {
        if (event.getId() != null && billingEventRepository.existsByProviderEventId(event.getId())) {
            log.info("Skipping already-processed Stripe event: {}", event.getId());
            return;
        }

        BillingEvent billingEvent = new BillingEvent();
        billingEvent.setProviderEventId(event.getId());
        billingEvent.setEventType(event.getType());
        billingEvent.setPayload(rawPayload);
        billingEventRepository.save(billingEvent);

        log.info("Processing Stripe event: type={} id={}", event.getType(), event.getId());

        switch (event.getType()) {
            case "checkout.session.completed":
                handleCheckoutSessionCompleted(event, billingEvent);
                break;
            case "customer.subscription.updated":
                handleSubscriptionUpdated(event, billingEvent);
                break;
            case "customer.subscription.deleted":
                handleSubscriptionDeleted(event, billingEvent);
                break;
            case "invoice.paid":
            case "invoice.payment_succeeded":
                handleInvoicePaid(event, billingEvent);
                break;
            case "invoice.payment_failed":
                handleInvoicePaymentFailed(event, billingEvent);
                break;
            default:
                log.debug("Unhandled Stripe event type: {}", event.getType());
                break;
        }

        billingEvent.setProcessedAt(Instant.now());
        billingEventRepository.save(billingEvent);
    }

    private void handleCheckoutSessionCompleted(Event event, BillingEvent billingEvent) {
        Session session = (Session) deserialize(event);
        if (session == null) {
            log.error("Unable to deserialize checkout.session.completed for event {}", event.getId());
            return;
        }

        String userIdValue = session.getMetadata() != null ? session.getMetadata().get("userId") : null;
        if (userIdValue == null) {
            throw new IllegalStateException("Missing userId in Stripe checkout session metadata");
        }

        String stripeSubscriptionId = session.getSubscription();
        if (stripeSubscriptionId == null || stripeSubscriptionId.isBlank()) {
            throw new IllegalStateException("Missing Stripe subscription id on checkout session");
        }

        Long userId = Long.valueOf(userIdValue);

        SubscriptionTier proTier = subscriptionTierRepository.findByCode(SubscriptionTierCode.PRO)
                .orElseThrow(() -> new IllegalStateException("PRO subscription tier not found"));

        UserSubscription sub = userSubscriptionRepository.findByProviderSubscriptionId(stripeSubscriptionId)
                .orElseGet(UserSubscription::new);

        sub.setUser(userService.findById(userId));
        sub.setTier(proTier);
        sub.setStatus(SubscriptionStatus.INCOMPLETE);
        sub.setProviderCustomerId(session.getCustomer());
        sub.setProviderSubscriptionId(stripeSubscriptionId);
        sub.setProviderPriceId(stripeConfig.getProMonthlyPriceId());
        sub.setAutoRenew(true);
        sub.setActive(false);

        userSubscriptionRepository.save(sub);
        billingEvent.setUserSubscription(sub);
    }

    private void handleInvoicePaid(Event event, BillingEvent billingEvent) {
        Invoice invoice = (Invoice) deserialize(event);
        if (invoice == null) {
            log.warn("Could not deserialize invoice for event {}", event.getId());
            return;
        }

        String subscriptionId = extractSubscriptionId(invoice);
        if (subscriptionId == null) {
            log.warn("invoice event missing subscription id for event {}", event.getId());
            return;
        }

        userSubscriptionRepository.findByProviderSubscriptionId(subscriptionId)
                .ifPresentOrElse(sub -> {
                    activateSubscription(sub);

                    sub.setStatus(SubscriptionStatus.ACTIVE);
                    sub.setActive(true);
                    sub.setAutoRenew(true);

                    if (invoice.getCustomer() != null) {
                        sub.setProviderCustomerId(invoice.getCustomer());
                    }

                    if (invoice.getLines() != null && !invoice.getLines().getData().isEmpty()) {
                        var line = invoice.getLines().getData().get(0);

                        if (line.getPrice() != null && line.getPrice().getId() != null) {
                            sub.setProviderPriceId(line.getPrice().getId());
                        }

                        if (line.getPeriod() != null) {
                            sub.setCurrentPeriodStart(Instant.ofEpochSecond(line.getPeriod().getStart()));
                            sub.setCurrentPeriodEnd(Instant.ofEpochSecond(line.getPeriod().getEnd()));
                        }
                    }

                    userSubscriptionRepository.save(sub);
                    billingEvent.setUserSubscription(sub);
                }, () -> log.warn("No local UserSubscription found for Stripe subscription {}", subscriptionId));
    }

    private void handleInvoicePaymentFailed(Event event, BillingEvent billingEvent) {
        Invoice invoice = (Invoice) deserialize(event);
        if (invoice == null) {
            log.warn("Could not deserialize invoice for event {}", event.getId());
            return;
        }

        String subscriptionId = extractSubscriptionId(invoice);
        if (subscriptionId == null) {
            log.warn("invoice.payment_failed missing subscription id for event {}", event.getId());
            return;
        }

        userSubscriptionRepository.findByProviderSubscriptionId(subscriptionId)
                .ifPresent(sub -> {
                    sub.setStatus(SubscriptionStatus.PAST_DUE);
                    sub.setActive(false);
                    userSubscriptionRepository.save(sub);
                    billingEvent.setUserSubscription(sub);

                    log.warn("Payment failed for subscription id={} userId={}", sub.getId(), sub.getUser().getId());
                });
    }

    private void handleSubscriptionUpdated(Event event, BillingEvent billingEvent) {
        Subscription stripeSubscription = (Subscription) deserialize(event);
        if (stripeSubscription == null) {
            log.error("Unable to deserialize customer.subscription.updated for event {}", event.getId());
            return;
        }

        userSubscriptionRepository.findByProviderSubscriptionId(stripeSubscription.getId())
                .ifPresent(sub -> {
                    syncSubscriptionFromStripe(sub, stripeSubscription);

                    SubscriptionStatus mappedStatus = mapStripeStatus(stripeSubscription.getStatus());
                    if (mappedStatus != null) {
                        sub.setStatus(mappedStatus);

                        if (mappedStatus == SubscriptionStatus.ACTIVE || mappedStatus == SubscriptionStatus.TRIALING) {
                            activateSubscription(sub);
                            sub.setActive(true);
                        } else {
                            sub.setActive(false);
                        }
                    }

                    userSubscriptionRepository.save(sub);
                    billingEvent.setUserSubscription(sub);
                });
    }

    private void handleSubscriptionDeleted(Event event, BillingEvent billingEvent) {
        Subscription stripeSubscription = (Subscription) deserialize(event);
        if (stripeSubscription == null) {
            log.error("Unable to deserialize customer.subscription.deleted for event {}", event.getId());
            return;
        }

        userSubscriptionRepository.findByProviderSubscriptionId(stripeSubscription.getId())
                .ifPresent(sub -> {
                    syncSubscriptionFromStripe(sub, stripeSubscription);
                    sub.setStatus(SubscriptionStatus.CANCELED);
                    sub.setActive(false);
                    sub.setAutoRenew(false);
                    sub.setCanceledAt(Instant.now());
                    sub.setEndedAt(stripeSubscription.getEndedAt() != null
                            ? Instant.ofEpochSecond(stripeSubscription.getEndedAt())
                            : Instant.now());

                    userSubscriptionRepository.save(sub);
                    billingEvent.setUserSubscription(sub);
                });
    }

    private void syncSubscriptionFromStripe(UserSubscription sub, Subscription stripeSubscription) {
        if (stripeSubscription.getCustomer() != null) {
            sub.setProviderCustomerId(stripeSubscription.getCustomer());
        }

        sub.setProviderSubscriptionId(stripeSubscription.getId());

        if (stripeSubscription.getItems() != null
                && stripeSubscription.getItems().getData() != null
                && !stripeSubscription.getItems().getData().isEmpty()
                && stripeSubscription.getItems().getData().get(0).getPrice() != null) {
            sub.setProviderPriceId(stripeSubscription.getItems().getData().get(0).getPrice().getId());
        }

        if (stripeSubscription.getCurrentPeriodStart() != null) {
            sub.setCurrentPeriodStart(Instant.ofEpochSecond(stripeSubscription.getCurrentPeriodStart()));
        }

        if (stripeSubscription.getCurrentPeriodEnd() != null) {
            sub.setCurrentPeriodEnd(Instant.ofEpochSecond(stripeSubscription.getCurrentPeriodEnd()));
        }

        boolean cancelAtPeriodEnd = Boolean.TRUE.equals(stripeSubscription.getCancelAtPeriodEnd());
        sub.setAutoRenew(!cancelAtPeriodEnd);

        if (stripeSubscription.getCancelAt() != null) {
            sub.setCancelAt(Instant.ofEpochSecond(stripeSubscription.getCancelAt()));
        }

        if (stripeSubscription.getCanceledAt() != null) {
            sub.setCanceledAt(Instant.ofEpochSecond(stripeSubscription.getCanceledAt()));
        }

        if (stripeSubscription.getEndedAt() != null) {
            sub.setEndedAt(Instant.ofEpochSecond(stripeSubscription.getEndedAt()));
        }
    }

    private void activateSubscription(UserSubscription sub) {
        userSubscriptionRepository.findByUserIdAndActiveTrue(sub.getUser().getId())
                .ifPresent(existing -> {
                    if (sub.getId() == null || !existing.getId().equals(sub.getId())) {
                        existing.setActive(false);

                        if (existing.getStatus() == SubscriptionStatus.ACTIVE
                                || existing.getStatus() == SubscriptionStatus.TRIALING
                                || existing.getStatus() == SubscriptionStatus.INCOMPLETE) {
                            existing.setStatus(SubscriptionStatus.CANCELED);
                        }

                        existing.setAutoRenew(false);

                        if (existing.getEndedAt() == null) {
                            existing.setEndedAt(Instant.now());
                        }

                        userSubscriptionRepository.save(existing);
                        userSubscriptionRepository.flush();
                    }
                });
    }

    private String extractSubscriptionId(Invoice invoice) {
        if (invoice.getSubscription() != null && !invoice.getSubscription().isBlank()) {
            return invoice.getSubscription();
        }

        if (invoice.getLines() != null && invoice.getLines().getData() != null) {
            for (var line : invoice.getLines().getData()) {
                if (line.getSubscription() != null && !line.getSubscription().isBlank()) {
                    return line.getSubscription();
                }
            }
        }

        return null;
    }

    private StripeObject deserialize(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

        if (deserializer.getObject().isPresent()) {
            return deserializer.getObject().get();
        }

        try {
            return deserializer.deserializeUnsafe();
        } catch (Exception e) {
            log.error(
                    "Failed to deserialize Stripe event object: type={} id={} error={}",
                    event.getType(),
                    event.getId(),
                    e.getMessage()
            );
            return null;
        }
    }

    private SubscriptionStatus mapStripeStatus(String stripeStatus) {
        if (stripeStatus == null) {
            return null;
        }

        return switch (stripeStatus) {
            case "active" -> SubscriptionStatus.ACTIVE;
            case "past_due" -> SubscriptionStatus.PAST_DUE;
            case "canceled" -> SubscriptionStatus.CANCELED;
            case "trialing" -> SubscriptionStatus.TRIALING;
            case "incomplete" -> SubscriptionStatus.INCOMPLETE;
            case "incomplete_expired" -> SubscriptionStatus.EXPIRED;
            default -> {
                log.warn("Unrecognized Stripe subscription status: {}", stripeStatus);
                yield null;
            }
        };
    }
}