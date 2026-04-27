package com.green.imagecore.controller;

import com.green.imagecore.entities.User;
import com.green.imagecore.service.GoogleAuthService;
import com.green.imagecore.service.JwtService;
import com.green.imagecore.service.UserAuthenticationService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class GoogleAuthController {

    private final GoogleAuthService googleAuthService;
    private final UserAuthenticationService userAuthenticationService;
    private final JwtService jwtService;

    @Value("${app.google.client-id}")
    private String googleClientId;

    @Value("${app.jwt.access-ttl-minutes}")
    private int jwtExpirationInMinutes;

    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    @GetMapping("/google/client-id")
    public ResponseEntity<GoogleClientIdResponse> clientId() {
        return ResponseEntity.ok(new GoogleClientIdResponse(googleClientId));
    }

    public record GoogleClientIdResponse(String clientId) {}

    @PostMapping("/google")
    public ResponseEntity<AuthController.AuthResponse> googleLogin(
            @Valid @RequestBody GoogleLoginRequest req,
            HttpServletResponse response
    ) {
        User user = googleAuthService.authenticateWithGoogle(req.idToken());

        UserDetails userDetails = userAuthenticationService.toUserDetails(user);
        String token = jwtService.generateToken(userDetails, user.getId().toString());

        ResponseCookie cookie = ResponseCookie.from("access_token", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .sameSite("Lax")
                .maxAge(jwtExpirationInMinutes * 60L)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(new AuthController.AuthResponse(
                user.getId(), user.getEmail(), user.getUsername()
        ));
    }

    public record GoogleLoginRequest(@NotBlank String idToken) {}
}
