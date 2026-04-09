package com.green.imagecore.service.subscription;

import com.green.imagecore.config.StripeConfig;
import com.green.imagecore.entities.subscription.UserSubscription;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.repositories.subscription.UserSubscriptionRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StripeService {

    private final StripeConfig stripeConfig;
    private final UserSubscriptionRepository userSubscriptionRepository;

    @Transactional
    public Session createCheckoutSession(Long userId) throws StripeException {
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(stripeConfig.getSuccessUrl() + "?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(stripeConfig.getCancelUrl())
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPrice(stripeConfig.getProMonthlyPriceId())
                                .setQuantity(1L)
                                .build()
                )
                .putMetadata("userId", String.valueOf(userId))
                .setSubscriptionData(
                        SessionCreateParams.SubscriptionData.builder()
                                .putMetadata("userId", String.valueOf(userId))
                                .build()
                )
                .build();

        return Session.create(params);
    }

    @Transactional
    public void cancelSubscription(Long userId) throws StripeException {
        UserSubscription userSubscription = getUserSubscription(userId);
        String providerSubscriptionId = getProviderSubscriptionId(userId);

        Subscription subscription = Subscription.retrieve(providerSubscriptionId);

        Subscription updated = subscription.update(
                java.util.Map.of("cancel_at_period_end", true)
        );

        userSubscription.setAutoRenew(false);
        if (updated.getCancelAt() != null) {
            userSubscription.setCancelAt(java.time.Instant.ofEpochSecond(updated.getCancelAt()));
        }
        userSubscriptionRepository.save(userSubscription);
    }

    @Transactional
    public void resumeSubscription(Long userId) throws StripeException {
        UserSubscription userSubscription = getUserSubscription(userId);
        String providerSubscriptionId = getProviderSubscriptionId(userId);

        Subscription subscription = Subscription.retrieve(providerSubscriptionId);

        subscription.update(
                java.util.Map.of("cancel_at_period_end", false)
        );

        userSubscription.setAutoRenew(true);
        userSubscription.setCancelAt(null);
        userSubscriptionRepository.save(userSubscription);
    }

    @Transactional
    public void deleteSubscription(Long userId) throws StripeException {
        UserSubscription userSubscription = getUserSubscription(userId);
        String providerSubscriptionId = getProviderSubscriptionId(userId);

        Subscription subscription = Subscription.retrieve(providerSubscriptionId);
        Subscription deleted = subscription.cancel();
        userSubscription.setAutoRenew(false);
        userSubscription.setCancelAt(null);
        userSubscription.setCanceledAt(java.time.Instant.now());

        if (deleted.getEndedAt() != null) {
            userSubscription.setEndedAt(java.time.Instant.ofEpochSecond(deleted.getEndedAt()));
        } else {
            userSubscription.setEndedAt(java.time.Instant.now());
        }

        userSubscriptionRepository.save(userSubscription);
    }

    private String getProviderSubscriptionId(Long userId) {
        UserSubscription userSubscription = getUserSubscription(userId);

        String providerSubscriptionId = userSubscription.getProviderSubscriptionId();
        if (providerSubscriptionId == null || providerSubscriptionId.isBlank()) {
            throw new ResourceNotFoundException("No Stripe subscription found for user " + userId);
        }

        return providerSubscriptionId;
    }

    private UserSubscription getUserSubscription(Long userId) {
        return userSubscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found for user " + userId));
    }
}