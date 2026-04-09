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
        String username = authentication.getName();
        Long userId = userService.findByUsername(username).getId();
        Session session = stripeService.createCheckoutSession(userId);
        return new CheckoutSessionResponse(session.getUrl());
    }

    public record CheckoutSessionResponse(
            String url
    ) {}
}