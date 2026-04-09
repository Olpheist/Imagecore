package com.green.imagecore.controller.subscription;

import com.green.imagecore.controller.BaseControllerTest;
import com.green.imagecore.entities.User;
import com.green.imagecore.service.UserService;
import com.green.imagecore.service.subscription.StripeService;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BillingController.class)
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-that-is-long-enough-for-hmac")
class BillingControllerTest extends BaseControllerTest {

    @MockitoBean
    private StripeService stripeService;

    @MockitoBean
    private UserService userService;

    @Test
    @WithMockUser(username = "testuser")
    void createCheckout_returns200() throws Exception {
        Long userId = 1L;

        User user = new User();
        user.setId(userId);
        user.setUsername("testuser");

        Session session = mock(Session.class);
        when(session.getUrl()).thenReturn("https://checkout.stripe.com/test-session");

        when(userService.findByUsername("testuser")).thenReturn(user);
        when(stripeService.createCheckoutSession(userId)).thenReturn(session);

        mockMvc.perform(post("/api/billing/checkout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://checkout.stripe.com/test-session"));

        verify(userService, times(1)).findByUsername("testuser");
        verify(stripeService, times(1)).createCheckoutSession(userId);
    }

    @Test
    @WithMockUser(username = "testuser")
    void cancelSubscription_returns200() throws Exception {
        Long userId = 1L;

        User user = new User();
        user.setId(userId);
        user.setUsername("testuser");

        when(userService.findByUsername("testuser")).thenReturn(user);

        mockMvc.perform(post("/api/billing/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Subscription will cancel at period end."));

        verify(userService, times(1)).findByUsername("testuser");
        verify(stripeService, times(1)).cancelSubscription(userId);
    }

    @Test
    @WithMockUser(username = "testuser")
    void resumeSubscription_returns200() throws Exception {
        Long userId = 1L;

        User user = new User();
        user.setId(userId);
        user.setUsername("testuser");

        when(userService.findByUsername("testuser")).thenReturn(user);

        mockMvc.perform(post("/api/billing/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Subscription auto renew has been resumed."));

        verify(userService, times(1)).findByUsername("testuser");
        verify(stripeService, times(1)).resumeSubscription(userId);
    }

    @Test
    void createCheckout_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/billing/checkout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancelSubscription_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/billing/cancel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resumeSubscription_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/billing/resume"))
                .andExpect(status().isUnauthorized());
    }
}