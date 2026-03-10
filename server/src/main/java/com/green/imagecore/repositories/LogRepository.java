package com.green.imagecore.repositories;

import com.green.imagecore.entities.Log;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LogRepository extends JpaRepository<Log, Long>, JpaSpecificationExecutor<Log> {
}