package com.green.imagecore.integration;

import com.green.imagecore.entities.Log;
import com.green.imagecore.repositories.LogRepository;
import com.green.imagecore.service.LogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Testcontainers
class LogIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestEntityManager em;

    @Autowired
    private LogRepository logRepository;

    private LogService logService;

    private final Pageable pageable = PageRequest.of(0, 50);

    @BeforeEach
    void setUp() {
        logService = new LogService(logRepository);

        logRepository.deleteAllInBatch();
        em.flush();
        em.clear();

        // All logs get createdAt = now() from Postgres default (insertable = false)

        Log log1 = new Log();
        log1.setUsername("alice");
        log1.setLogLevel("ERROR");
        log1.setMethod("GET");
        log1.setPath("/api/users");
        log1.setStatus(500);
        log1.setDurationMs(10);
        em.persist(log1);

        Log log2 = new Log();
        log2.setUsername("bob");
        log2.setLogLevel("INFO");
        log2.setMethod("POST");
        log2.setPath("/api/orders");
        log2.setStatus(200);
        log2.setDurationMs(5);
        em.persist(log2);

        Log log3 = new Log();
        log3.setUsername("alice");
        log3.setLogLevel("INFO");
        log3.setMethod("GET");
        log3.setPath("/api/products");
        log3.setStatus(200);
        log3.setDurationMs(8);
        em.persist(log3);

        em.flush();
        em.clear();
    }

    @Test
    void search_FiltersByUsername() {
        Page<Log> result = logService.search("alice", null, null, null, null, null, null, pageable);

        assertEquals(2, result.getTotalElements());
        assertTrue(result.getContent().stream().allMatch(l -> l.getUsername().equals("alice")));
    }

    @Test
    void search_FiltersByLogLevel() {
        Page<Log> result = logService.search(null, "ERROR", null, null, null, null, null, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("ERROR", result.getContent().get(0).getLogLevel());
    }

    @Test
    void search_FiltersByMethod() {
        Page<Log> result = logService.search(null, null, "GET", null, null, null, null, pageable);

        assertEquals(2, result.getTotalElements());
        assertTrue(result.getContent().stream().allMatch(l -> l.getMethod().equals("GET")));
    }

    @Test
    void search_FiltersByPathCaseInsensitive() {
        Page<Log> result = logService.search(null, null, null, "/API/USERS", null, null, null, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("/api/users", result.getContent().get(0).getPath());
    }

    @Test
    void search_FiltersByStatus() {
        Page<Log> result = logService.search(null, null, null, null, 200, null, null, pageable);

        assertEquals(2, result.getTotalElements());
        assertTrue(result.getContent().stream().allMatch(l -> l.getStatus() == 200));
    }

    @Test
    void search_FiltersByFromDate_IncludesRecentLogs() {
        // from = 1 hour ago, all 3 logs were just inserted so all should match
        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Page<Log> result = logService.search(null, null, null, null, null, from, null, pageable);

        assertEquals(3, result.getTotalElements());
    }

    @Test
    void search_FiltersByFromDate_ExcludesFutureLogs() {
        // from = 1 hour from now, no logs should match since they were just inserted
        Instant from = Instant.now().plus(1, ChronoUnit.HOURS);
        Page<Log> result = logService.search(null, null, null, null, null, from, null, pageable);

        assertEquals(0, result.getTotalElements());
    }

    @Test
    void search_FiltersByToDate_IncludesRecentLogs() {
        // to = 1 hour from now, all 3 logs were just inserted so all should match
        Instant to = Instant.now().plus(1, ChronoUnit.HOURS);
        Page<Log> result = logService.search(null, null, null, null, null, null, to, pageable);

        assertEquals(3, result.getTotalElements());
    }

    @Test
    void search_FiltersByToDate_ExcludesFutureCutoff() {
        // to = 1 hour ago, no logs should match since they were just inserted
        Instant to = Instant.now().minus(1, ChronoUnit.HOURS);
        Page<Log> result = logService.search(null, null, null, null, null, null, to, pageable);

        assertEquals(0, result.getTotalElements());
    }

    @Test
    void search_FiltersByDateRange_WindowAroundNow() {
        // window from 1 hour ago to 1 hour from now captures all 3 logs
        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant to = Instant.now().plus(1, ChronoUnit.HOURS);
        Page<Log> result = logService.search(null, null, null, null, null, from, to, pageable);

        assertEquals(3, result.getTotalElements());
    }

    @Test
    void search_FiltersByDateRange_NoMatches() {
        // window entirely in the past captures nothing
        Instant from = Instant.parse("2020-01-01T00:00:00Z");
        Instant to = Instant.parse("2020-12-31T23:59:59Z");
        Page<Log> result = logService.search(null, null, null, null, null, from, to, pageable);

        assertEquals(0, result.getTotalElements());
    }

    @Test
    void search_CombinesMultipleFilters() {
        Page<Log> result = logService.search("alice", "ERROR", "GET", null, null, null, null, pageable);

        assertEquals(1, result.getTotalElements());
        Log log = result.getContent().get(0);
        assertEquals("alice", log.getUsername());
        assertEquals("ERROR", log.getLogLevel());
    }

    @Test
    void search_WithAllNullFilters_ReturnsAll() {
        Page<Log> result = logService.search(null, null, null, null, null, null, null, pageable);

        assertEquals(3, result.getTotalElements());
    }

    @Test
    void search_WithBlankUsername_ReturnsAll() {
        Page<Log> result = logService.search("   ", null, null, null, null, null, null, pageable);

        assertEquals(3, result.getTotalElements());
    }

    @Test
    void search_WithNoMatches_ReturnsEmptyPage() {
        Page<Log> result = logService.search("nobody", null, null, null, null, null, null, pageable);

        assertTrue(result.getContent().isEmpty());
        assertEquals(0, result.getTotalElements());
    }
}