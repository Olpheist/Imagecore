package com.green.imagecore.service;

import com.green.imagecore.entities.Log;
import com.green.imagecore.repositories.LogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogServiceTest {

    @Mock
    private LogRepository logRepository;

    @InjectMocks
    private LogService logService;

    @Test
    void save_ReturnsSavedLog() {
        Log log = new Log();
        log.setId(1L);

        when(logRepository.save(log)).thenReturn(log);

        Log result = logService.save(log);

        assertEquals(log, result);
        verify(logRepository).save(log);
    }

    @Test
    void search_ReturnsMatchingLogs() {
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to   = Instant.parse("2024-01-31T23:59:59Z");

        Log log1 = new Log();
        log1.setId(1L);
        Log log2 = new Log();
        log2.setId(2L);

        when(logRepository.search("alice", "ERROR", from, to)).thenReturn(List.of(log1, log2));

        List<Log> result = logService.search("alice", "ERROR", from, to);

        assertEquals(2, result.size());
        verify(logRepository).search("alice", "ERROR", from, to);
    }

    @Test
    void search_ReturnsEmptyList_WhenNoMatches() {
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to   = Instant.parse("2024-01-31T23:59:59Z");

        when(logRepository.search("nobody", "DEBUG", from, to)).thenReturn(List.of());

        List<Log> result = logService.search("nobody", "DEBUG", from, to);

        assertTrue(result.isEmpty());
        verify(logRepository).search("nobody", "DEBUG", from, to);
    }

    @Test
    void search_WithNullFilters_DelegatesToRepository() {
        when(logRepository.search(null, null, null, null)).thenReturn(List.of());

        List<Log> result = logService.search(null, null, null, null);

        assertTrue(result.isEmpty());
        verify(logRepository).search(null, null, null, null);
    }
}