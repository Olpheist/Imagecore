package com.green.imagecore.repositories;

import com.green.imagecore.entities.Log;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LogRepository extends JpaRepository<Log, Long> {
    @Query("""
    SELECT l FROM Log l
    WHERE (:username IS NULL OR l.username = :username)
      AND (:logLevel IS NULL OR l.logLevel = :logLevel)
      AND (:from IS NULL OR l.createdAt >= :from)
      AND (:to IS NULL OR l.createdAt <= :to)
    ORDER BY l.createdAt DESC
""")
    List<Log> search(
            @Param("username") String username,
            @Param("logLevel") String logLevel,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}
