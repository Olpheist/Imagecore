package com.green.imagecore.controller;

import com.green.imagecore.service.StripeService;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final StripeService stripeService;

    @PostMapping("/checkout")
    public CheckoutSessionResponse createCheckout(Authentication authentication) throws Exception {
        String username = authentication.getName();
        Session session = stripeService.createCheckoutSession(username);
        return new CheckoutSessionResponse(session.getUrl());
    }

    public record CheckoutSessionResponse(
            String url
    ) {}
}