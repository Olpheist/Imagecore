package com.green.imagecore.repositories;

import com.green.imagecore.entities.Pipeline;
import com.green.imagecore.entities.PipelineStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PipelineRepository extends JpaRepository<Pipeline, Long> {

    Optional<Pipeline> findFirstByImageIdAndUserIdOrderByCreatedAtDesc(Long imageId, Long userId);

    List<Pipeline> findByStatusIn(List<PipelineStatus> statuses);
}
