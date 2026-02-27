package com.green.imagecore.controller;

import com.green.imagecore.config.RequestLogFilter;
import com.green.imagecore.config.SecurityConfig;
import com.green.imagecore.entities.User;
import com.green.imagecore.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import org.springframework.http.MediaType;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, RequestLogFilter.class})
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-that-is-long-enough-for-hmac")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;


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
}