package com.green.imagecore.controller;

import com.green.imagecore.entities.User;
import com.green.imagecore.entities.subscription.SubscriptionTier;
import com.green.imagecore.entities.subscription.SubscriptionTierCode;
import com.green.imagecore.entities.subscription.UserSubscription;
import com.green.imagecore.service.UserService;
import com.green.imagecore.service.subscription.StripeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-that-is-long-enough-for-hmac")
class UserControllerTest extends BaseControllerTest {

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private StripeService stripeService;


    @Test
    @WithMockUser(username = "clinician_jane")
    void me() throws Exception {
        String username = "clinician_jane";
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername(username);
        mockUser.setEmail("jane@imagecore.com");
        mockUser.setEnabled(true);
        mockUser.setUserRoles(Collections.emptySet());

        UserSubscription sub = new UserSubscription();

        SubscriptionTier tier = new SubscriptionTier();
        tier.setCode(SubscriptionTierCode.FREE);

        sub.setTier(tier);
        sub.setAutoRenew(false);

        mockUser.setUserSubscription(sub);

        when(userService.findByUsernameWithRoles(username)).thenReturn(mockUser);

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value("jane@imagecore.com"))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllUsers_asAdmin_returns200() throws Exception {
        when(userService.findAllWithRoles()).thenReturn(List.of());

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void getAllUsers_asUser_returns403() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllUsers_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRoles_asAdmin_returns200() throws Exception {
        Long userId = 1L;
        List<Long> roleIds = List.of(2L, 3L);

        User updatedUser = new User();
        updatedUser.setId(userId);
        updatedUser.setUsername("clinician_jane");
        updatedUser.setEmail("jane@imagecore.com");
        updatedUser.setEnabled(true);
        updatedUser.setUserRoles(Collections.emptySet());

        UserSubscription sub = new UserSubscription();

        SubscriptionTier tier = new SubscriptionTier();
        tier.setCode(SubscriptionTierCode.FREE);

        sub.setTier(tier);
        sub.setAutoRenew(false);

        updatedUser.setUserSubscription(sub);

        when(userService.updateUserRoles(userId, roleIds)).thenReturn(updatedUser);

        mockMvc.perform(put("/api/users/{id}/roles", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\": [2, 3]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.username").value("clinician_jane"));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void updateRoles_asUser_returns403() throws Exception {
        mockMvc.perform(put("/api/users/1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\": [2, 3]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateRoles_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/users/1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\": [2, 3]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deleteUser_asAdmin_returns204() throws Exception {
        Long currentUserId = 1L;
        Long targetUserId = 2L;

        User currentUser = new User();
        currentUser.setId(currentUserId);
        currentUser.setUsername("admin");

        when(userService.findByUsername("admin")).thenReturn(currentUser);

        mockMvc.perform(delete("/api/users/{id}", targetUserId))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deleteUser_cannotDeleteSelf_returns400() throws Exception {
        Long userId = 1L;

        User currentUser = new User();
        currentUser.setId(userId);
        currentUser.setUsername("admin");

        when(userService.findByUsername("admin")).thenReturn(currentUser);

        mockMvc.perform(delete("/api/users/{id}", userId))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void deleteUser_asUser_returns403() throws Exception {
        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteUserSubscription_asAdmin_returns204() throws Exception {
        Long userId = 1L;

        mockMvc.perform(delete("/api/users/{id}/subscription", userId))
                .andExpect(status().isNoContent());

        verify(stripeService, times(1)).deleteSubscription(userId);
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void deleteUserSubscription_asUser_returns403() throws Exception {
        mockMvc.perform(delete("/api/users/1/subscription"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteUserSubscription_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/users/1/subscription"))
                .andExpect(status().isUnauthorized());
    }
}