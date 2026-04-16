package com.green.imagecore.service;

import com.green.imagecore.dto.RecentJobDto;
import com.green.imagecore.dto.ToolStatsDto;
import com.green.imagecore.entities.AnalysisJob;
import com.green.imagecore.entities.JobStatus;
import com.green.imagecore.entities.Tool;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.repositories.AnalysisJobRepository;
import com.green.imagecore.repositories.ToolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ToolStatsService {

    private final ToolRepository toolRepository;
    private final AnalysisJobRepository analysisJobRepository;

    @Transactional(readOnly = true)
    public ToolStatsDto getStats(Long toolId, Authentication auth) {
        Tool tool = toolRepository.findById(toolId)
                .orElseThrow(() -> new ResourceNotFoundException("Tool not found with id: " + toolId));

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> Objects.equals(a.getAuthority(), "ROLE_ADMIN"));
        boolean isOwner = tool.getCreatedBy().getUsername().equals(auth.getName());

        if (!isAdmin && !isOwner) {
            throw new AccessDeniedException("You do not have permission to view stats for this tool.");
        }

        List<Object[]> rows = analysisJobRepository.findAggregateStatsByToolId(toolId);
        Object[] row = rows.get(0);

        long totalRuns            = ((Number) row[0]).longValue();
        long completedRuns        = ((Number) row[1]).longValue();
        long failedRuns           = ((Number) row[2]).longValue();
        long pendingOrRunningRuns = ((Number) row[3]).longValue();
        Double avgCompletionSeconds = row[4] != null ? ((Number) row[4]).doubleValue() : null;
        Instant lastRunAt = row[5] != null ? ((Timestamp) row[5]).toInstant() : null;

        double successRatePct = totalRuns == 0 ? 0.0 : (double) completedRuns / totalRuns * 100.0;

        List<RecentJobDto> recentJobs = analysisJobRepository
                .findTop10ByToolIdOrderByCreatedAtDesc(toolId)
                .stream()
                .map(this::toRecentJobDto)
                .toList();

        ToolStatsDto dto = new ToolStatsDto();
        dto.setTotalRuns(totalRuns);
        dto.setCompletedRuns(completedRuns);
        dto.setFailedRuns(failedRuns);
        dto.setPendingOrRunningRuns(pendingOrRunningRuns);
        dto.setSuccessRatePct(successRatePct);
        dto.setAvgCompletionSeconds(avgCompletionSeconds);
        dto.setLastRunAt(lastRunAt);
        dto.setRecentJobs(recentJobs);
        return dto;
    }

    private RecentJobDto toRecentJobDto(AnalysisJob job) {
        Double duration = job.getStatus() == JobStatus.COMPLETED
                ? (double) Duration.between(job.getCreatedAt(), job.getUpdatedAt()).getSeconds()
                : null;

        RecentJobDto dto = new RecentJobDto();
        dto.setId(job.getId());
        dto.setStatus(job.getStatus().name());
        dto.setDurationSeconds(duration);
        dto.setCreatedAt(job.getCreatedAt());
        return dto;
    }
}
