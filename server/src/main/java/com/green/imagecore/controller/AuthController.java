package com.green.imagecore.controller;

import com.green.imagecore.entities.User;
import com.green.imagecore.service.UserService;
import com.green.imagecore.security.JwtService;
import com.green.imagecore.service.UserAuthenticationService; // Crucial import
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Controller handling authentication and authorization requests.
 * This implementation provides a stateless authentication mechanism by issuing
 * JSON Web Tokens (JWT) instead of relying on traditional HTTP sessions.
 */
@RestController
@AllArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final UserAuthenticationService userAuthenticationService; // Inject this
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    /**
     * Data Transfer Object for authentication responses.
     * Encapsulates the JWT access token along with basic user profile information
     * required by the Nuxt frontend for state management.
     */
    public record AuthResponse(
            String token,
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
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        // Persist the user via the domain service (handles password hashing)
        User u = userService.register(req.email(), req.username(), req.password());

        // Convert the domain entity into a Security UserDetails object to ensure
        // that all granted authorities/roles are correctly formatted for the JWT.
        UserDetails userDetails = userAuthenticationService.loadUserByUsername(u.getUsername());

        // Issue the token containing the user's ID and assigned roles
        String token = jwtService.generateToken(userDetails, u.getId().toString());

        return ResponseEntity.ok(new AuthResponse(token, u.getId(), u.getEmail(), u.getUsername()));
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
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
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

        return ResponseEntity.ok(new AuthResponse(token, u.getId(), u.getEmail(), u.getUsername()));
    }

    /**
     * Performs a stateless logout.
     * Since no session is maintained on the server, this endpoint simply returns a success
     * status. The client-side application must delete the JWT to effectively log out.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }

    // Request Data Transfer Objects (DTOs)

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank String username,
            @NotBlank String password
    ) {}

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {}
}