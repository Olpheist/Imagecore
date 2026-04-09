package com.green.imagecore.service.subscription;

import com.green.imagecore.config.StripeConfig;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StripeService {

    private final StripeConfig stripeConfig;

    public Session createCheckoutSession(Long userId) throws StripeException {
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(stripeConfig.getSuccessUrl())
                .setCancelUrl(stripeConfig.getCancelUrl())
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPrice(stripeConfig.getProMonthlyPriceId())
                                .setQuantity(1L)
                                .build()
                )
                .putMetadata("userId", String.valueOf(userId))
                .build();

        return Session.create(params);
    }
}