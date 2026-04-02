package com.green.imagecore.controller;

import com.green.imagecore.entities.User;
import com.green.imagecore.service.PasswordResetService;
import com.green.imagecore.service.UserService;
import com.green.imagecore.service.JwtService;
import com.green.imagecore.service.UserAuthenticationService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

/**
 * Controller handling authentication and authorization requests.
 * This implementation provides a stateless authentication mechanism by issuing
 * JSON Web Tokens (JWT) instead of relying on traditional HTTP sessions.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final UserAuthenticationService userAuthenticationService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final PasswordResetService passwordResetService;

    @Value("${app.jwt.access-ttl-minutes}")
    private int jwtExpirationInMinutes;

    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    /**
     * Data Transfer Object for authentication responses.
     * Encapsulates the JWT access token along with basic user profile information
     * required by the Nuxt frontend for state management.
     */
    public record AuthResponse(
            Long id,
            String email,
            String username
    ) {}

    /**
     * Handles new user registration.
     * After persisting the user, it immediately generates a JWT to allow the user
     * to access protected resources without a separate login step.
     *
     * @param req Validated registration details.
     * @return AuthResponse containing the new user's profile and access token.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req, HttpServletResponse response) {
        if (!req.password().equals(req.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        // Persist the user via the domain service (handles password hashing)
        User u = userService.register(req.email(), req.username(), req.password());

        // Convert the domain entity into a Security UserDetails object to ensure
        // that all granted authorities/roles are correctly formatted for the JWT.
        UserDetails userDetails = userAuthenticationService.toUserDetails(u);

        // Issue the token containing the user's ID and assigned roles
        String token = jwtService.generateToken(userDetails, u.getId().toString());

        addAccessTokenCookie(response, token);

        return ResponseEntity.ok(new AuthResponse(u.getId(), u.getEmail(), u.getUsername()));
    }

    /**
     * Authenticates user credentials and issues a signed JWT.
     * This replaces the traditional session-based login. The server remains stateless;
     * the client is responsible for storing and sending the token in subsequent requests.
     *
     * @param req Validated login credentials.
     * @return AuthResponse containing the access token if authentication is successful.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletResponse response) {
        // Utilize the AuthenticationManager to verify the username and password against the database.
        // This triggers the UserAuthenticationService to load authorities.
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password())
        );

        // Retrieve the database entity to get the unique primary key (UID)
        User u = userService.findByUsername(req.username());

        // Extract the authenticated principal (UserDetails) to get roles for the JWT claims
        UserDetails userDetails = (UserDetails) auth.getPrincipal();

        // Generate the token. The backend will verify this token's signature on every future request.
        String token = jwtService.generateToken(userDetails, u.getId().toString());

        addAccessTokenCookie(response, token);

        return ResponseEntity.ok(new AuthResponse(u.getId(), u.getEmail(), u.getUsername()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        clearAccessTokenCookie(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest body) {
        passwordResetService.requestReset(body.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest body) {
        if (!body.newPassword().equals(body.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        passwordResetService.resetPassword(body.token(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    private void addAccessTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("access_token", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .sameSite("Lax")
                .maxAge(jwtExpirationInMinutes * 60L)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearAccessTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .sameSite("Lax")
                .maxAge(0)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    // Request Data Transfer Objects (DTOs)

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank String username,
            @Size(min = 10, max = 64) @NotBlank String password,
            @NotBlank String confirmPassword
    ) {}

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {}

    public record ForgotPasswordRequest(
            @NotBlank @Email String email
    ) {}

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 10, max = 64) String newPassword,
            @NotBlank String confirmPassword
    ) {}
}