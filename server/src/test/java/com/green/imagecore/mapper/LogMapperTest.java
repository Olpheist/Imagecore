package com.green.imagecore.mapper;

import com.green.imagecore.dto.LogDto;
import com.green.imagecore.entities.Log;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogMapperTest {

    // --- toDto ---

    @Test
    void toDto_nullLog_returnsNull() {
        assertNull(LogMapper.toDto(null));
    }

    @Test
    void toDto_validLog_mapsCorrectly() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        Log log = new Log();
        log.setId(1L);
        log.setCreatedAt(now);
        log.setLogLevel("INFO");
        log.setUsername("john");
        log.setMessage("Something happened");

        LogDto dto = LogMapper.toDto(log);

        assertEquals(1L, dto.getId());
        assertEquals(now, dto.getCreatedAt());
        assertEquals("INFO", dto.getLogLevel());
        assertEquals("john", dto.getUsername());
        assertEquals("Something happened", dto.getMessage());
    }

    @Test
    void toDto_nullOptionalFields_mapsCorrectly() {
        Log log = new Log();
        log.setId(2L);
        log.setLogLevel("WARN");
        log.setMessage("No user");

        LogDto dto = LogMapper.toDto(log);

        assertEquals(2L, dto.getId());
        assertNull(dto.getUsername());
        assertNull(dto.getCreatedAt());
    }

    // --- toDtos ---

    @Test
    void toDtos_nullList_returnsEmptyList() {
        assertTrue(LogMapper.toDtos(null).isEmpty());
    }

    @Test
    void toDtos_emptyList_returnsEmptyList() {
        assertTrue(LogMapper.toDtos(List.of()).isEmpty());
    }

    @Test
    void toDtos_validList_mapsAllLogs() {
        Log first = new Log();
        first.setId(1L);
        first.setLogLevel("INFO");
        first.setMessage("First");

        Log second = new Log();
        second.setId(2L);
        second.setLogLevel("ERROR");
        second.setMessage("Second");

        List<LogDto> dtos = LogMapper.toDtos(List.of(first, second));

        assertEquals(2, dtos.size());
        assertEquals(1L, dtos.get(0).getId());
        assertEquals(2L, dtos.get(1).getId());
        assertEquals("INFO", dtos.get(0).getLogLevel());
        assertEquals("ERROR", dtos.get(1).getLogLevel());
    }
}