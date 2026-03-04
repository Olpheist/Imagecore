package com.green.imagecore.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EmailServiceTest {
    private MockRestServiceServer server;
    private EmailService emailService;

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.sendgrid.com/v3");

        server = MockRestServiceServer.bindTo(builder).build();

        emailService = new EmailService(builder.build(), true);
    }

    @Test
    void send_shouldCallSendGrid() {
        server.expect(requestTo("https://api.sendgrid.com/v3/mail/send"))
                .andExpect(method(HttpMethod.POST))                                    // ✅ Fixed import
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess());

        emailService.send("user@test.com", "Test Subject", "Test Body");

        server.verify();
    }

    @Test
    void sendPasswordResetEmail_shouldSendResetEmail() {
        server.expect(requestTo("https://api.sendgrid.com/v3/mail/send"))
                .andExpect(method(HttpMethod.POST))                                    // ✅ Fixed import
                .andRespond(withSuccess());

        emailService.sendPasswordResetEmail(
                "user@test.com",
                "https://imagecore.org/reset-password?token=abc123"
        );

        server.verify();
    }

    @Test
    void sendPasswordResetEmail_devProfile_shouldNotCallSendGrid() {
        ReflectionTestUtils.setField(emailService, "enabled", false);

        emailService.sendPasswordResetEmail(
                "user@test.com",
                "https://imagecore.org/reset-password?token=abc123"
        );

        server.verify();
    }
}