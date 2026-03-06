package com.green.imagecore.controller;

import com.green.imagecore.dto.LogDto;
import com.green.imagecore.mapper.LogMapper;
import com.green.imagecore.service.LogService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/logs")
public class LogController {
    private final LogService logService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<LogDto>> logs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String logLevel,
            @RequestParam Instant from,
            @RequestParam Instant to
    ) {
        return ResponseEntity.ok(LogMapper.toDtos(logService.search(username, logLevel, from, to)));
    }
}
