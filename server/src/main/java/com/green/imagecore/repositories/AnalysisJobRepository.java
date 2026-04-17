package com.green.imagecore.repositories;

import com.green.imagecore.entities.AnalysisJob;
import com.green.imagecore.entities.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {

    List<AnalysisJob> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<AnalysisJob> findByImageIdOrderByCreatedAtDesc(Long imageId);

    List<AnalysisJob> findByStatusIn(List<JobStatus> statuses);

    // Bulk delete avoids loading entities into the Hibernate session, preventing stale-state
    // conflicts when the DB's ON DELETE CASCADE would otherwise delete the same rows first.
    @Modifying
    @Query("DELETE FROM AnalysisJob a WHERE a.image.id = :imageId")
    void deleteByImageId(@Param("imageId") Long imageId);
}
