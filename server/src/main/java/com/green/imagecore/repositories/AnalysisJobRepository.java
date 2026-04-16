package com.green.imagecore.repositories;

import com.green.imagecore.entities.AnalysisJob;
import com.green.imagecore.entities.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {

    List<AnalysisJob> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<AnalysisJob> findByImageIdOrderByCreatedAtDesc(Long imageId);

    List<AnalysisJob> findByStatusIn(List<JobStatus> statuses);

    void deleteByImageId(Long imageId);

    @Query(value = """
            SELECT
                COUNT(*),
                COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END),
                COUNT(CASE WHEN status = 'FAILED' THEN 1 END),
                COUNT(CASE WHEN status IN ('PENDING','SUBMITTED','RUNNING') THEN 1 END),
                AVG(CASE WHEN status = 'COMPLETED'
                        THEN EXTRACT(EPOCH FROM (updated_at - created_at)) END),
                MAX(created_at)
            FROM analysis_jobs
            WHERE tool_id = :toolId
            """, nativeQuery = true)
    List<Object[]> findAggregateStatsByToolId(@Param("toolId") Long toolId);

    List<AnalysisJob> findTop10ByToolToolIdOrderByCreatedAtDesc(Long toolId);
}
