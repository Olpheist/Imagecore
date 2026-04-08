package com.green.imagecore.repositories;

import com.green.imagecore.entities.AnalysisJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {

    List<AnalysisJob> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<AnalysisJob> findByImageIdOrderByCreatedAtDesc(Long imageId);
}
