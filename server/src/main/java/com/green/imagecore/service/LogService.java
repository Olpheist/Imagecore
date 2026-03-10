package com.green.imagecore.service;

import com.green.imagecore.entities.Log;
import com.green.imagecore.repositories.LogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
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
    public Page<Log> search(
            String username,
            String logLevel,
            String method,
            String path,
            Integer status,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        Specification<Log> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (username != null && !username.isBlank()) {
                predicates.add(cb.equal(root.get("username"), username));
            }

            if (logLevel != null && !logLevel.isBlank()) {
                predicates.add(cb.equal(root.get("logLevel"), logLevel));
            }

            if (method != null && !method.isBlank()) {
                predicates.add(cb.equal(root.get("method"), method));
            }

            if (path != null && !path.isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("path")),
                        "%" + path.toLowerCase() + "%"
                ));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }

            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return logRepository.findAll(spec, pageable);
    }
}