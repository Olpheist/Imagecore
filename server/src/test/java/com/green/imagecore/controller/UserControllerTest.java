package com.green.imagecore.controller;

import com.green.imagecore.entities.User;
import com.green.imagecore.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService)).build();
    }

    @Test
    void me() throws Exception {
        String username = "clinician_jane";
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername(username);
        mockUser.setEmail("jane@imagecore.com");
        mockUser.setEnabled(true);
        mockUser.setUserRoles(Collections.emptySet());

        // Create a mock auth object to simulate a logged-in session
        Authentication auth = new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());

        when(userService.findByUsernameWithRoles(username)).thenReturn(mockUser);

        mockMvc.perform(get("/api/user/me")
                        .principal(auth)) // Pass the mock security principal
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value("jane@imagecore.com"))
                .andExpect(jsonPath("$.id").value(1));
    }
}