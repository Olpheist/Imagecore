package com.green.imagecore.mapper;

import com.green.imagecore.dto.LogDto;
import com.green.imagecore.entities.Log;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class LogMapper {

    private LogMapper() {}

    public static LogDto toDto(Log log) {
        if (log == null) return null;

        LogDto dto = new LogDto();
        dto.setId(log.getId());
        dto.setCreatedAt(log.getCreatedAt());
        dto.setLogLevel(log.getLogLevel());
        dto.setUsername(log.getUsername());
        dto.setMessage(log.getMessage());

        return dto;
    }

    public static List<LogDto> toDtos(List<Log> logs) {
        if (logs == null) return Collections.emptyList();

        return logs.stream()
                .map(LogMapper::toDto)
                .collect(Collectors.toList());
    }
}