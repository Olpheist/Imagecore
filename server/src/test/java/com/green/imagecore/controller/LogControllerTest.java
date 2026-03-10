package com.green.imagecore.controller;

import com.green.imagecore.entities.Log;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        log.setMethod("GET");
        log.setPath("/api/tools");
        log.setStatus(200);
        log.setDurationMs(42);
        log.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        PageRequest pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Log> page = new PageImpl<>(List.of(log), pageable, 1);

        when(logService.search(
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(Instant.parse(FROM)),
                eq(Instant.parse(TO)),
                eq(pageable)
        )).thenReturn(page);

        mockMvc.perform(get("/api/logs")
                        .param("from", FROM)
                        .param("to", TO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].logLevel").value("INFO"))
                .andExpect(jsonPath("$.content[0].username").value("john"))
                .andExpect(jsonPath("$.content[0].method").value("GET"))
                .andExpect(jsonPath("$.content[0].path").value("/api/tools"))
                .andExpect(jsonPath("$.content[0].status").value(200))
                .andExpect(jsonPath("$.content[0].durationMs").value(42))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getLogs_withQueryParams_passesThemToService() throws Exception {
        PageRequest pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Log> page = Page.empty(pageable);

        when(logService.search(
                eq("john"),
                eq("INFO"),
                eq("GET"),
                eq("/api/logs"),
                eq(200),
                eq(Instant.parse(FROM)),
                eq(Instant.parse(TO)),
                eq(pageable)
        )).thenReturn(page);

        mockMvc.perform(get("/api/logs")
                        .param("username", "john")
                        .param("logLevel", "INFO")
                        .param("method", "GET")
                        .param("path", "/api/logs")
                        .param("status", "200")
                        .param("from", FROM)
                        .param("to", TO))
                .andExpect(status().isOk());

        verify(logService).search(
                eq("john"),
                eq("INFO"),
                eq("GET"),
                eq("/api/logs"),
                eq(200),
                eq(Instant.parse(FROM)),
                eq(Instant.parse(TO)),
                eq(pageable)
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getLogs_withPageAndSize_passesThemToService() throws Exception {
        PageRequest pageable = PageRequest.of(1, 25, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Log> page = Page.empty(pageable);

        when(logService.search(
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(Instant.parse(FROM)),
                eq(Instant.parse(TO)),
                eq(pageable)
        )).thenReturn(page);

        mockMvc.perform(get("/api/logs")
                        .param("from", FROM)
                        .param("to", TO)
                        .param("page", "1")
                        .param("size", "25"))
                .andExpect(status().isOk());

        verify(logService).search(
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(Instant.parse(FROM)),
                eq(Instant.parse(TO)),
                eq(pageable)
        );
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

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteAllLogs_asAdmin_returns204() throws Exception {
        mockMvc.perform(delete("/api/logs"))
                .andExpect(status().isNoContent());

        verify(logService).deleteAll();
    }

    @Test
    @WithMockUser(roles = "CLINICIAN")
    void deleteAllLogs_asClinician_returns403() throws Exception {
        mockMvc.perform(delete("/api/logs"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteAllLogs_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/logs"))
                .andExpect(status().isUnauthorized());
    }
}