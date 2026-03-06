package com.green.imagecore.controller;

import com.green.imagecore.entities.Log;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LogController.class)
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-that-is-long-enough-for-hmac")
class LogControllerTest extends BaseControllerTest {

    private static final String FROM = "2026-01-01T00:00:00Z";
    private static final String TO = "2026-01-02T00:00:00Z";

    @Test
    @WithMockUser(roles = "ADMIN")
    void getLogs_asAdmin_returns200() throws Exception {
        Log log = new Log();
        log.setId(1L);
        log.setLogLevel("INFO");
        log.setUsername("john");
        log.setMessage("Something happened");
        log.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        when(logService.search(any(), any(), any(), any())).thenReturn(List.of(log));

        mockMvc.perform(get("/api/logs")
                        .param("from", FROM)
                        .param("to", TO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].logLevel").value("INFO"))
                .andExpect(jsonPath("$[0].username").value("john"))
                .andExpect(jsonPath("$[0].message").value("Something happened"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getLogs_withQueryParams_passesThemToService() throws Exception {
        when(logService.search(eq("john"), eq("INFO"), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/logs")
                        .param("username", "john")
                        .param("logLevel", "INFO")
                        .param("from", FROM)
                        .param("to", TO))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "CLINICIAN")
    void getLogs_asClinician_returns403() throws Exception {
        mockMvc.perform(get("/api/logs")
                        .param("from", FROM)
                        .param("to", TO))
                .andExpect(status().isForbidden());
    }

    @Test
    void getLogs_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/logs")
                        .param("from", FROM)
                        .param("to", TO))
                .andExpect(status().isUnauthorized());
    }
}