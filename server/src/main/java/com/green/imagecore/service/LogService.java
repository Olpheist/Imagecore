package com.green.imagecore.service;

import com.green.imagecore.entities.Log;
import com.green.imagecore.repositories.LogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
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
    public List<Log> getByUser(String username) {
        return logRepository.findByUsernameOrderByCreatedAtDesc(username);
    }

    @Transactional(readOnly = true)
    public List<Log> getByLevel(String logLevel) {
        return logRepository.findByLogLevelOrderByCreatedAtDesc(logLevel);
    }

    @Transactional(readOnly = true)
    public List<Log> getByDateRange(Date from, Date to) {
        return logRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);
    }

    @Transactional(readOnly = true)
    public List<Log> getByUserAndLevel(String username, String logLevel) {
        return logRepository.findByUsernameAndLogLevelOrderByCreatedAtDesc(username, logLevel);
    }
}
