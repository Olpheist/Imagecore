package com.green.imagecore.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GoogleTokenVerifier {

    private final RestClient restClient;
    private final String googleClientId;

    @Autowired
    public GoogleTokenVerifier(@Value("${app.google.client-id}") String googleClientId) {
        this(RestClient.builder(), googleClientId);
    }

    // Package-private for testing via MockRestServiceServer
    GoogleTokenVerifier(RestClient.Builder builder, String googleClientId) {
        this.restClient = builder.baseUrl("https://oauth2.googleapis.com").build();
        this.googleClientId = googleClientId;
    }

    public GoogleTokenInfo verify(String idToken) {
        GoogleTokenInfoResponse response;
        try {
            response = restClient.get()
                    .uri("/tokeninfo?id_token={token}", idToken)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (req, res) -> {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token");
                    })
                    .body(GoogleTokenInfoResponse.class);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google token verification failed");
        }

        if (response == null || !googleClientId.equals(response.aud())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token audience mismatch");
        }

        return new GoogleTokenInfo(response.sub(), response.email(), response.name());
    }

    private record GoogleTokenInfoResponse(String sub, String email, String name, String aud) {}
}
