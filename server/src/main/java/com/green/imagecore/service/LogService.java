package com.green.imagecore.service;

import com.green.imagecore.entities.Log;
import com.green.imagecore.repositories.LogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LogService {
    private final LogRepository logRepository;

    @Transactional
    public Log save(Log log) {
        return logRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<Log> search(String username, String logLevel, Instant from, Instant to) {
        return logRepository.search(username, logLevel, from, to);
    }
}
