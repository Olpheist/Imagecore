package com.green.imagecore.repositories;

import com.green.imagecore.entities.PipelineStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PipelineStepRepository extends JpaRepository<PipelineStep, Long> {

    List<PipelineStep> findByPipelineIdOrderByStepOrder(Long pipelineId);

    Optional<PipelineStep> findByAnalysisJobId(Long analysisJobId);
}
