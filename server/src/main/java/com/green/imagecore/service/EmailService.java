package com.green.imagecore.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class EmailService {
    private final RestClient client;
    private final boolean enabled;

    @Autowired
    public EmailService(@Value("${app.send-grid.api-key:disabled}") String apiKey) {
        this.enabled = !apiKey.equals("disabled");
        this.client = enabled ? RestClient.builder()
                .baseUrl("https://api.sendgrid.com/v3")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build() : null;
    }

    // private constructor for testing
    EmailService(RestClient client, boolean enabled) {
        this.client = client;
        this.enabled = enabled;
    }

    public void send(String to, String subject, String body) {
        if (!enabled) {
            log.info("Email disabled. Would send to={} subject={} body={}", to, subject, body);
            return;
        }

        Map<String, Object> payload = Map.of(
                "personalizations", List.of(Map.of("to", List.of(Map.of("email", to)))),
                "from", Map.of("email", "noreply@imagecore.org"),
                "subject", subject,
                "content", List.of(Map.of("type", "text/plain", "value", body)),
                "tracking_settings", Map.of(
                        "click_tracking", Map.of(
                                "enable", false,
                                "enable_text", false
                        )
                )
        );

        client.post()
                .uri("/mail/send")
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        String body = """
            Click the link below to reset your password:

            %s

            If you did not request this, ignore this email.
            """.formatted(resetLink);

        send(toEmail, "Reset your ImageCore password", body);
    }
}
