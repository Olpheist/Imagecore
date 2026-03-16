package com.green.imagecore.service;

import com.green.imagecore.entities.Log;
import com.green.imagecore.repositories.LogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        Instant to = Instant.parse("2024-01-31T23:59:59Z");
        Pageable pageable = PageRequest.of(0, 50);

        Log log1 = new Log();
        log1.setId(1L);

        Log log2 = new Log();
        log2.setId(2L);

        Page<Log> page = new PageImpl<>(List.of(log1, log2), pageable, 2);

        when(logRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<Log>>any(),
                eq(pageable)
        )).thenReturn(page);

        Page<Log> result = logService.search(
                "alice",
                "ERROR",
                "GET",
                "/api/users",
                500,
                from,
                to,
                pageable
        );

        assertEquals(2, result.getContent().size());
        assertEquals(2, result.getTotalElements());

        verify(logRepository).findAll(
                org.mockito.ArgumentMatchers.<Specification<Log>>any(),
                eq(pageable)
        );
    }

    @Test
    void search_ReturnsEmptyPage_WhenNoMatches() {
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to = Instant.parse("2024-01-31T23:59:59Z");
        Pageable pageable = PageRequest.of(0, 50);

        Page<Log> page = new PageImpl<>(List.of(), pageable, 0);

        when(logRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<Log>>any(),
                eq(pageable)
        )).thenReturn(page);

        Page<Log> result = logService.search(
                "nobody",
                "DEBUG",
                null,
                null,
                null,
                from,
                to,
                pageable
        );

        assertTrue(result.getContent().isEmpty());
        assertEquals(0, result.getTotalElements());

        verify(logRepository).findAll(
                org.mockito.ArgumentMatchers.<Specification<Log>>any(),
                eq(pageable)
        );
    }

    @Test
    void search_WithNullFilters_DelegatesToRepository() {
        Pageable pageable = PageRequest.of(0, 50);
        Page<Log> page = new PageImpl<>(List.of(), pageable, 0);

        when(logRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<Log>>any(),
                eq(pageable)
        )).thenReturn(page);

        Page<Log> result = logService.search(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                pageable
        );

        assertTrue(result.getContent().isEmpty());

        verify(logRepository).findAll(
                org.mockito.ArgumentMatchers.<Specification<Log>>any(),
                eq(pageable)
        );
    }

    @Test
    void deleteAll_DeletesAllLogsInBatch() {
        logService.deleteAll();

        verify(logRepository).deleteAllInBatch();
    }
}