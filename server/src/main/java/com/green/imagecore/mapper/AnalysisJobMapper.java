package com.green.imagecore.mapper;

import com.green.imagecore.dto.AnalysisJobDto;
import com.green.imagecore.entities.AnalysisJob;

import java.util.Collections;
import java.util.List;

public class AnalysisJobMapper {

    public static AnalysisJobDto toDto(AnalysisJob job) {
        if (job == null) return null;

        AnalysisJobDto dto = new AnalysisJobDto();
        dto.setId(job.getId());
        dto.setImageId(job.getImage().getId());
        dto.setToolId(job.getTool().getToolId());
        dto.setToolName(job.getTool().getName());
        dto.setStatus(job.getStatus().name());
        dto.setEcsTaskArn(job.getEcsTaskArn());
        dto.setCreatedAt(job.getCreatedAt() != null ? job.getCreatedAt().toString() : null);

        return dto;
    }

    public static List<AnalysisJobDto> toDtos(List<AnalysisJob> jobs) {
        if (jobs == null) return Collections.emptyList();

        return jobs.stream()
                .map(AnalysisJobMapper::toDto)
                .toList();
    }
}
