package com.green.imagecore.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.green.imagecore.entities.User;
import com.green.imagecore.service.JwtService;
import com.green.imagecore.service.UserService;
import com.green.imagecore.service.UserAuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserService userService;
    @Mock
    private UserAuthenticationService userAuthenticationService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AuthController(userService, userAuthenticationService, authenticationManager, jwtService)
        ).build();
    }

    @Test
    void register_Success() throws Exception {
        // happy path
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("newuser");

        when(userService.register(anyString(), anyString(), anyString())).thenReturn(mockUser);
        when(jwtService.generateToken(any(), anyString())).thenReturn("mock-token");

        var request = new AuthController.RegisterRequest("new@test.com", "newuser", "password");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-token"));
    }

    @Test
    void register_Failure_UserExists() {
        // sad path
        when(userService.register(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("Username already in use"));

        var request = new AuthController.RegisterRequest("duplicate@test.com", "exists", "password");

        Exception exception = assertThrows(Exception.class, () -> {
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));
        });

        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertEquals("Username already in use", exception.getCause().getMessage());
    }

    @Test
    void login_Success() throws Exception {
        // happy path
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("dr_smith");

        var auth = new UsernamePasswordAuthenticationToken(null, null, java.util.List.of());

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(userService.findByUsername("dr_smith")).thenReturn(mockUser);
        when(jwtService.generateToken(any(), anyString())).thenReturn("valid-jwt");

        var request = new AuthController.LoginRequest("dr_smith", "password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("valid-jwt"));
    }

    @Test
    void login_Failure_InvalidCredentials() {
        // sad path
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Invalid username or password"));

        var request = new AuthController.LoginRequest("dr_smith", "wrong_password");

        Exception exception = assertThrows(Exception.class, () -> {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));
        });

        assertTrue(exception.getCause() instanceof BadCredentialsException);
        assertEquals("Invalid username or password", exception.getCause().getMessage());
    }
}