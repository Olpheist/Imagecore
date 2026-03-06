package com.green.imagecore.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.green.imagecore.entities.User;
import com.green.imagecore.exception.GlobalExceptionHandler;
import com.green.imagecore.service.JwtService;
import com.green.imagecore.service.PasswordResetService;
import com.green.imagecore.service.UserService;
import com.green.imagecore.service.UserAuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@WebMvcTest(AuthController.class)
class AuthControllerTest extends BaseControllerTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UserService userService;
    @MockitoBean
    private UserAuthenticationService userAuthenticationService;
    @MockitoBean
    private AuthenticationManager authenticationManager;
    @MockitoBean
    private PasswordResetService passwordResetService;
    @MockitoBean
    private JwtService jwtService;

    @Test
    void register_Success() throws Exception {
        // happy path
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("newuser");

        when(userService.register(anyString(), anyString(), anyString())).thenReturn(mockUser);
        when(jwtService.generateToken(any(), anyString())).thenReturn("mock-token");

        var request = new AuthController.RegisterRequest("new@test.com", "newuser", "password10", "password10");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-token"));
    }

    @Test
    void register_Failure_UserExists() throws Exception {
        // sad path
        when(userService.register(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("Username already in use"));

        var request = new AuthController.RegisterRequest(
                "duplicate@test.com",
                "exists",
                "password10",
                "password10"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Username already in use")));
    }

    @Test
    void register_Failure_PasswordsDoNotMatch_Returns400() throws Exception {
        var request = new AuthController.RegisterRequest(
                "new@test.com",
                "newuser",
                "password10",
                "different10"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Passwords do not match")));
    }

    @Test
    void register_Failure_PasswordTooShort_Returns400() throws Exception {
        var request = new AuthController.RegisterRequest(
                "new@test.com",
                "newuser",
                "short",
                "short"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
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
    void login_Failure_InvalidCredentials() throws Exception {
        // sad path
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Invalid username or password"));

        var request = new AuthController.LoginRequest("dr_smith", "wrong_password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("Invalid username or password")));
    }

    @Test
    void forgotPassword_Success_Returns204() throws Exception {
        doNothing().when(passwordResetService).requestReset("user@test.com");

        var request = new AuthController.ForgotPasswordRequest("user@test.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(passwordResetService, times(1)).requestReset("user@test.com");
    }

    @Test
    void forgotPassword_Failure_InvalidEmail_Returns400() throws Exception {
        // invalid email format -> bean validation should trip
        var request = new AuthController.ForgotPasswordRequest("not-an-email");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(passwordResetService, never()).requestReset(anyString());
    }

    @Test
    void forgotPassword_Failure_BlankEmail_Returns400() throws Exception {
        var request = new AuthController.ForgotPasswordRequest("");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(passwordResetService, never()).requestReset(anyString());
    }

    @Test
    void resetPassword_Success_Returns204() throws Exception {
        doNothing().when(passwordResetService).resetPassword("token-123", "password10");

        var request = new AuthController.ResetPasswordRequest("token-123", "password10", "password10");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(passwordResetService, times(1)).resetPassword("token-123", "password10");
    }

    @Test
    void resetPassword_Failure_PasswordsDoNotMatch_Returns400() throws Exception {
        var request = new AuthController.ResetPasswordRequest("token-123", "password10", "different10");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Passwords do not match")));

        verify(passwordResetService, never()).resetPassword(anyString(), anyString());
    }

    @Test
    void resetPassword_Failure_PasswordTooShort_Returns400() throws Exception {
        // @Size(min = 10) should fail
        var request = new AuthController.ResetPasswordRequest("token-123", "short", "short");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(passwordResetService, never()).resetPassword(anyString(), anyString());
    }

    @Test
    void resetPassword_Failure_InvalidToken_Returns400() throws Exception {
        doThrow(new IllegalArgumentException("Invalid or expired token"))
                .when(passwordResetService).resetPassword("bad-token", "password10");

        var request = new AuthController.ResetPasswordRequest("bad-token", "password10", "password10");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Invalid or expired token")));

        verify(passwordResetService, times(1)).resetPassword("bad-token", "password10");
    }
}