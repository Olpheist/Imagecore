package com.green.imagecore.repositories;

import com.green.imagecore.entities.Log;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;

public interface LogRepository extends JpaRepository<Log, Long> {
    List<Log> findByUsernameOrderByCreatedAtDesc(String username);
    List<Log> findByLogLevelOrderByCreatedAtDesc(String logLevel);
    List<Log> findByCreatedAtBetweenOrderByCreatedAtDesc(Date from, Date to);
    List<Log> findByUsernameAndLogLevelOrderByCreatedAtDesc(String username, String logLevel);
}
