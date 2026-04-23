package com.green.imagecore.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.green.imagecore.entities.User;
import com.green.imagecore.service.GoogleAuthService;
import com.green.imagecore.service.JwtService;
import com.green.imagecore.service.UserAuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GoogleAuthController.class)
@TestPropertySource(properties = "app.google.client-id=test-client-id-123")
class GoogleAuthControllerTest extends BaseControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private GoogleAuthService googleAuthService;

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void googleLogin_Success_Returns200AndSetsCookie() throws Exception {
        User user = new User();
        user.setId(42L);
        user.setEmail("user@gmail.com");
        user.setUsername("user");

        when(googleAuthService.authenticateWithGoogle("valid.id.token")).thenReturn(user);
        when(userAuthenticationService.toUserDetails(user)).thenReturn(
                org.springframework.security.core.userdetails.User
                        .withUsername("user").password("").roles("CLINICIAN").build()
        );
        when(jwtService.generateToken(any(UserDetails.class), anyString())).thenReturn("mock-jwt");

        var request = new GoogleAuthController.GoogleLoginRequest("valid.id.token");

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(cookie().value("access_token", "mock-jwt"))
                .andExpect(jsonPath("$.id", is(42)))
                .andExpect(jsonPath("$.email", is("user@gmail.com")))
                .andExpect(jsonPath("$.username", is("user")));
    }

    @Test
    void googleLogin_InvalidToken_Returns401() throws Exception {
        when(googleAuthService.authenticateWithGoogle(anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token"));

        var request = new GoogleAuthController.GoogleLoginRequest("bad.token");

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void googleLogin_BlankIdToken_Returns400() throws Exception {
        var request = new GoogleAuthController.GoogleLoginRequest("");

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clientId_ReturnsConfiguredClientId() throws Exception {
        mockMvc.perform(get("/api/auth/google/client-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId", is("test-client-id-123")));
    }
}
