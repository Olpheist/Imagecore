package com.green.imagecore.controller.subscription;

import com.green.imagecore.config.StripeConfig;
import com.green.imagecore.controller.BaseControllerTest;
import com.green.imagecore.service.subscription.StripeWebhookService;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StripeWebhookController.class)
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-that-is-long-enough-for-hmac")
class StripeWebhookControllerTest extends BaseControllerTest {

    @MockitoBean
    private StripeConfig stripeConfig;

    @MockitoBean
    private StripeWebhookService stripeWebhookService;

    @Test
    void handleWebhook_validSignature_returns200() throws Exception {
        String payload = "{\"type\":\"checkout.session.completed\"}";
        String signature = "test-signature";
        String webhookSecret = "whsec_test_secret";

        Event event = mock(Event.class);

        when(stripeConfig.getWebhookSecret()).thenReturn(webhookSecret);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(payload, signature, webhookSecret))
                    .thenReturn(event);

            mockMvc.perform(post("/api/stripe/webhook")
                            .contentType("application/json")
                            .content(payload)
                            .header("Stripe-Signature", signature))
                    .andExpect(status().isOk())
                    .andExpect(content().string("received"));

            verify(stripeConfig, times(1)).getWebhookSecret();
            verify(stripeWebhookService, times(1)).handleEvent(event, payload);
        }
    }

    @Test
    void handleWebhook_invalidSignature_returns400() throws Exception {
        String payload = "{\"type\":\"checkout.session.completed\"}";
        String signature = "bad-signature";
        String webhookSecret = "whsec_test_secret";

        when(stripeConfig.getWebhookSecret()).thenReturn(webhookSecret);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(payload, signature, webhookSecret))
                    .thenThrow(new com.stripe.exception.SignatureVerificationException(
                            "Invalid signature",
                            signature
                    ));

            mockMvc.perform(post("/api/stripe/webhook")
                            .contentType("application/json")
                            .content(payload)
                            .header("Stripe-Signature", signature))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string("Invalid signature"));

            verify(stripeConfig, times(1)).getWebhookSecret();
            verify(stripeWebhookService, never()).handleEvent(any(), anyString());
        }
    }

    @Test
    void handleWebhook_missingSignatureHeader_returns400() throws Exception {
        String payload = "{\"type\":\"checkout.session.completed\"}";

        mockMvc.perform(post("/api/stripe/webhook")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(stripeConfig);
        verifyNoInteractions(stripeWebhookService);
    }
}