package com.green.imagecore.bdd;

import com.green.imagecore.service.JwtService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Step Definitions for JWT Verification.
 * This class bridges the Gherkin feature files with the backend's JwtService.
 * It validates that the issued tokens adhere to HIPAA security standards and
 * contain the necessary claims for Role-Based Access Control (RBAC).
 *
 * Note: This class is intentionally not annotated with @Component to avoid
 * duplicate bean registration conflicts between Cucumber and Spring's auto-detection.
 */
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class JwtTokenClaimsSteps {

    @Autowired
    private JwtService jwtService;

    @Value("${app.jwt.secret}")
    private String secret;

    private UserDetails testUser;
    private String testUserId;
    private String jwtToken;
    private Claims decodedClaims;
    private Exception decodeException;

    /**
     * Initializes a mock user context for the test scenario.
     * Maps the user's primary ID, email, and roles into a Spring Security UserDetails object.
     */
    @Given("a test user with id {int}, email {string}, username {string}, and roles:")
    public void a_test_user(Integer id, String email, String username, DataTable rolesTable) {
        this.testUserId = id.toString();

        // Map Gherkin table roles into GrantedAuthorities for the Security Principal
        List<SimpleGrantedAuthority> authorities = rolesTable.asList().stream()
                .map(SimpleGrantedAuthority::new) // Matches "PATIENT" from feature
                .collect(Collectors.toList());

        // Construct the test user principal
        this.testUser = new User(email, "password", authorities);
    }

    /**
     * Triggers the JwtService to generate a signed token.
     * Immediately attempts to decode the token to verify its structure.
     */
    @When("I issue an access token for that user")
    public void i_issue_an_access_token() {
        this.jwtToken = jwtService.generateToken(testUser, testUserId);
        tryDecode(jwtToken);
    }

    /**
     * Asserts that the token signature matches the shared secret.
     */
    @Then("the token should be cryptographically verifiable")
    public void token_should_be_verifiable() {
        assertNotNull(decodedClaims);
        assertNull(decodeException);
    }

    /**
     * Generic step to verify the value of any String-based claim in the JWT.
     */
    @Then("the token claim {string} should equal {string}")
    public void claim_should_equal_string(String claim, String expectedValue) {
        assertEquals(expectedValue, decodedClaims.get(claim, String.class));
    }

    /**
     * Generic step to verify the value of any Integer-based claim (like the UID).
     */
    @Then("the token claim {string} should equal {int}")
    public void claim_should_equal_int(String claim, Integer expectedValue) {
        Object value = decodedClaims.get(claim);
        assertEquals(expectedValue.toString(), value.toString());
    }

    /**
     * Verifies that the 'roles' claim contains the expected authorities.
     */
    @Then("the token claim {string} should contain:")
    @SuppressWarnings("unchecked") // Specifically target the List cast
    public void claim_should_contain(String claim, DataTable expectedRoles) {
        List<String> actualRoles = decodedClaims.get(claim, List.class);
        expectedRoles.asList().forEach(role -> assertTrue(actualRoles.contains(role)));
    }

    /**
     * Ensures that 'iat' (Issued At) and 'exp' (Expiration) claims are present.
     */
    @Then("the token should include standard time claims")
    public void should_include_time_claims() {
        assertNotNull(decodedClaims.getIssuedAt());
        assertNotNull(decodedClaims.getExpiration());
    }

    /**
     * Validates that the token's lifetime is restricted to a short window (e.g., 15 minutes),
     * which is a critical security requirement for HIPAA compliance.
     */
    @Then("the token expiration should be within {int} minutes from now")
    public void expiration_within_minutes(Integer minutes) {
        Date exp = decodedClaims.getExpiration();
        long diff = exp.getTime() - System.currentTimeMillis();
        assertTrue(diff > 0 && diff <= (long) minutes * 60 * 1000);
    }

    /**
     * Simulates a malicious actor modifying the token's signature.
     */
    @When("I tamper with the token")
    public void i_tamper_with_the_token() {
        this.jwtToken = jwtToken.substring(0, jwtToken.length() / 2);
        tryDecode(jwtToken);
    }

    /**
     * Asserts that the decoding process failed, which happens if the signature is invalid.
     */
    @Then("decoding the token should fail")
    public void decoding_the_token_should_fail() {
        assertNotNull(decodeException);
    }

    /**
     * Helper method to parse the JWT string using the same secret used by the application.
     * Captured exceptions are used to verify failure scenarios (like tampering).
     */
    private void tryDecode(String token) {
        try {
            this.decodedClaims = Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            this.decodeException = null;
        } catch (Exception e) {
            this.decodeException = e;
            this.decodedClaims = null;
        }
    }
}