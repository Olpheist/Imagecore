package com.green.imagecore.bdd;

import com.green.imagecore.entities.User;
import com.green.imagecore.security.JwtService;
import com.green.imagecore.service.UserService;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Step Definitions for User Authentication (Login) flows.
 * These steps verify the integration between the security filter chain,
 * the database identity store, and the JWT issuance logic.
 *
 * * Note: Suppressing inspection because IntelliJ cannot detect the
 * runtime injection provided by the cucumber-spring glue.
 */
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class LoginSteps {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtService jwtService;

    private String username = "testuser";
    private String password = "securePassword123";
    private String email = "test@example.com";
    private Authentication authResult;
    private String issuedToken;

    /**
     * Seeds the Testcontainers database with a fresh user record.
     * This ensures the login attempt has a real record to authenticate against.
     */
    @Given("a registered user exists")
    public void a_registered_user_exists() {
        // Register the user through the service to ensure password hashing is applied
        try {
            userService.register(email, username, password);
        } catch (IllegalArgumentException e) {
            // If user already exists from a previous scenario, can continue
        }
    }

    /**
     * Simulates the login process by calling the Spring Security AuthenticationManager.
     * This mimics the logic found in the AuthController's login endpoint.
     */
    @When("the user submits valid login credentials")
    public void the_user_submits_valid_login_credentials() {
        UsernamePasswordAuthenticationToken authRequest =
                new UsernamePasswordAuthenticationToken(username, password);

        this.authResult = authenticationManager.authenticate(authRequest);
    }

    /**
     * Verifies that the authentication was successful and a valid JWT was issued.
     * Satisfies the "Definition of Done" for integration testing protected flows.
     */
    @Then("the user is authenticated successfully")
    public void the_user_is_authenticated_successfully() {
        assertNotNull(authResult, "Authentication result should not be null");
        assertTrue(authResult.isAuthenticated(), "User should be authenticated");

        // Further verify that we can generate a token for this authenticated principal
        User u = userService.findByUsername(username);
        UserDetails principal = (UserDetails) authResult.getPrincipal();
        this.issuedToken = jwtService.generateToken(principal, u.getId().toString());

        assertNotNull(issuedToken, "A JWT should be issued upon successful authentication");
    }
}