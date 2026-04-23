package com.green.imagecore.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleTokenVerifierTest {

    private static final String CLIENT_ID = "expected-client-id";
    private static final String BASE_URL = "https://oauth2.googleapis.com";

    private MockRestServiceServer mockServer;
    private GoogleTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        verifier = new GoogleTokenVerifier(builder, CLIENT_ID);
    }

    @Test
    void verify_ValidToken_ReturnsGoogleTokenInfo() {
        String tokenInfoJson = """
                {
                  "sub": "google-sub-123",
                  "email": "user@gmail.com",
                  "name": "Test User",
                  "aud": "expected-client-id"
                }
                """;

        mockServer.expect(requestToUriTemplate(BASE_URL + "/tokeninfo?id_token={token}", "valid.token"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(tokenInfoJson, MediaType.APPLICATION_JSON));

        GoogleTokenInfo result = verifier.verify("valid.token");

        assertEquals("google-sub-123", result.sub());
        assertEquals("user@gmail.com", result.email());
        assertEquals("Test User", result.name());
        mockServer.verify();
    }

    @Test
    void verify_GoogleReturnsError_ThrowsUnauthorized() {
        mockServer.expect(requestToUriTemplate(BASE_URL + "/tokeninfo?id_token={token}", "bad.token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> verifier.verify("bad.token"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void verify_AudienceMismatch_ThrowsUnauthorized() {
        String tokenInfoJson = """
                {
                  "sub": "google-sub-123",
                  "email": "user@gmail.com",
                  "name": "Test User",
                  "aud": "some-other-client-id"
                }
                """;

        mockServer.expect(requestToUriTemplate(BASE_URL + "/tokeninfo?id_token={token}", "mismatched.token"))
                .andRespond(withSuccess(tokenInfoJson, MediaType.APPLICATION_JSON));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> verifier.verify("mismatched.token"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void verify_NetworkFailure_ThrowsUnauthorized() {
        mockServer.expect(requestToUriTemplate(BASE_URL + "/tokeninfo?id_token={token}", "any.token"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> verifier.verify("any.token"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }
}
