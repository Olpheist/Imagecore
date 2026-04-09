package com.green.imagecore.controller;

import com.green.imagecore.config.StripeConfig;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final StripeConfig stripeConfig;

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature
    ) {
        Event event;

        try {
            event = Webhook.constructEvent(
                    payload,
                    signature,
                    stripeConfig.getWebhookSecret()
            );
        } catch (SignatureVerificationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        switch (event.getType()) {
            case "checkout.session.completed":
                System.out.println("checkout completed");
                break;
            case "customer.subscription.deleted":
                System.out.println("subscription deleted");
                break;
            case "invoice.paid":
                System.out.println("invoice paid");
                break;
            default:
                System.out.println("Unhandled event: " + event.getType());
        }

        return ResponseEntity.ok("received");
    }
}