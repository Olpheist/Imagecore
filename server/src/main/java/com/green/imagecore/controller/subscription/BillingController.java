package com.green.imagecore.controller.subscription;

import com.green.imagecore.service.UserService;
import com.green.imagecore.service.subscription.StripeService;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final StripeService stripeService;
    private final UserService userService;

    @PostMapping("/checkout")
    public CheckoutSessionResponse createCheckout(Authentication authentication) throws Exception {
        Long userId = getCurrentUserId(authentication);
        Session session = stripeService.createCheckoutSession(userId);
        return new CheckoutSessionResponse(session.getUrl());
    }

    @PostMapping("/cancel")
    public BillingActionResponse cancelSubscription(Authentication authentication) throws Exception {
        Long userId = getCurrentUserId(authentication);
        stripeService.cancelSubscription(userId);
        return new BillingActionResponse("Subscription will cancel at period end.");
    }

    @PostMapping("/resume")
    public BillingActionResponse resumeSubscription(Authentication authentication) throws Exception {
        Long userId = getCurrentUserId(authentication);
        stripeService.resumeSubscription(userId);
        return new BillingActionResponse("Subscription auto renew has been resumed.");
    }

    private Long getCurrentUserId(Authentication authentication) {
        String username = authentication.getName();
        return userService.findByUsername(username).getId();
    }

    public record CheckoutSessionResponse(
            String url
    ) {}

    public record BillingActionResponse(
            String message
    ) {}
}