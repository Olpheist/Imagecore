package com.green.imagecore.service;

import com.green.imagecore.dto.ToolStatsDto;
import com.green.imagecore.entities.AnalysisJob;
import com.green.imagecore.entities.JobStatus;
import com.green.imagecore.entities.Tool;
import com.green.imagecore.entities.User;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.repositories.AnalysisJobRepository;
import com.green.imagecore.repositories.ToolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ToolStatsService Unit Tests")
class ToolStatsServiceTest {

    @Mock
    private ToolRepository toolRepository;

    @Mock
    private AnalysisJobRepository analysisJobRepository;

    @InjectMocks
    private ToolStatsService toolStatsService;

    private Tool tool;
    private User owner;

    private Authentication ownerAuth;
    private Authentication adminAuth;
    private Authentication otherAuth;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setUsername("dr.smith");

        tool = new Tool();
        tool.setToolId(10L);
        tool.setName("image-slice");
        tool.setCategory("reporting");
        tool.setCreatedBy(owner);

        ownerAuth = new UsernamePasswordAuthenticationToken(
                "dr.smith", null,
                List.of(new SimpleGrantedAuthority("ROLE_CLINICIAN"))
        );

        adminAuth = new UsernamePasswordAuthenticationToken(
                "admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        otherAuth = new UsernamePasswordAuthenticationToken(
                "dr.jones", null,
                List.of(new SimpleGrantedAuthority("ROLE_CLINICIAN"))
        );
    }

    private Object[] aggregateRow(long total, long completed, long failed, long pending,
                                  Double avgSeconds, Instant lastRunAt) {
        return new Object[]{
                total, completed, failed, pending,
                avgSeconds,
                lastRunAt != null ? Timestamp.from(lastRunAt) : null
        };
    }

    private AnalysisJob completedJob(long id, Instant createdAt, Instant updatedAt) {
        AnalysisJob job = new AnalysisJob();
        job.setId(id);
        job.setStatus(JobStatus.COMPLETED);
        job.setCreatedAt(createdAt);
        job.setUpdatedAt(updatedAt);
        return job;
    }

    private AnalysisJob failedJob(long id, Instant createdAt) {
        AnalysisJob job = new AnalysisJob();
        job.setId(id);
        job.setStatus(JobStatus.FAILED);
        job.setCreatedAt(createdAt);
        job.setUpdatedAt(createdAt);
        return job;
    }

    @Nested
    @DisplayName("getStats()")
    class GetStats {

        @Test
        @DisplayName("owner gets stats for their tool")
        void ownerGetsStats() {
            Instant now = Instant.now();
            Instant twoMinsAgo = now.minusSeconds(120);

            when(toolRepository.findById(10L)).thenReturn(Optional.of(tool));
            when(analysisJobRepository.findAggregateStatsByToolId(10L))
                    .thenReturn(List.<Object[]>of(aggregateRow(5L, 4L, 1L, 0L, 100.0, twoMinsAgo)));
            when(analysisJobRepository.findTop10ByToolIdOrderByCreatedAtDesc(10L))
                    .thenReturn(List.of(completedJob(1L, twoMinsAgo, now)));

            ToolStatsDto stats = toolStatsService.getStats(10L, ownerAuth);

            assertThat(stats.getTotalRuns()).isEqualTo(5);
            assertThat(stats.getCompletedRuns()).isEqualTo(4);
            assertThat(stats.getFailedRuns()).isEqualTo(1);
            assertThat(stats.getPendingOrRunningRuns()).isEqualTo(0);
            assertThat(stats.getSuccessRatePct()).isEqualTo(80.0);
            assertThat(stats.getAvgCompletionSeconds()).isEqualTo(100.0);
            assertThat(stats.getLastRunAt()).isEqualTo(twoMinsAgo);
            assertThat(stats.getRecentJobs()).hasSize(1);
            assertThat(stats.getRecentJobs().get(0).getId()).isEqualTo(1L);
            assertThat(stats.getRecentJobs().get(0).getDurationSeconds()).isEqualTo(120.0);
        }

        @Test
        @DisplayName("admin gets stats for any tool")
        void adminGetsStats() {
            when(toolRepository.findById(10L)).thenReturn(Optional.of(tool));
            when(analysisJobRepository.findAggregateStatsByToolId(10L))
                    .thenReturn(List.<Object[]>of(aggregateRow(0L, 0L, 0L, 0L, null, null)));
            when(analysisJobRepository.findTop10ByToolIdOrderByCreatedAtDesc(10L))
                    .thenReturn(List.of());

            ToolStatsDto stats = toolStatsService.getStats(10L, adminAuth);

            assertThat(stats.getTotalRuns()).isEqualTo(0);
        }

        @Test
        @DisplayName("non-owner non-admin throws AccessDeniedException")
        void nonOwnerNonAdminIsRejected() {
            when(toolRepository.findById(10L)).thenReturn(Optional.of(tool));

            assertThatThrownBy(() -> toolStatsService.getStats(10L, otherAuth))
                    .isInstanceOf(AccessDeniedException.class);

            verifyNoInteractions(analysisJobRepository);
        }

        @Test
        @DisplayName("unknown tool throws ResourceNotFoundException")
        void unknownToolThrows() {
            when(toolRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> toolStatsService.getStats(99L, ownerAuth))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("tool with zero runs returns zero stats and empty recent jobs")
        void zeroRunsReturnsZeroStats() {
            when(toolRepository.findById(10L)).thenReturn(Optional.of(tool));
            when(analysisJobRepository.findAggregateStatsByToolId(10L))
                    .thenReturn(List.<Object[]>of(aggregateRow(0L, 0L, 0L, 0L, null, null)));
            when(analysisJobRepository.findTop10ByToolIdOrderByCreatedAtDesc(10L))
                    .thenReturn(List.of());

            ToolStatsDto stats = toolStatsService.getStats(10L, ownerAuth);

            assertThat(stats.getTotalRuns()).isEqualTo(0);
            assertThat(stats.getSuccessRatePct()).isEqualTo(0.0);
            assertThat(stats.getAvgCompletionSeconds()).isNull();
            assertThat(stats.getLastRunAt()).isNull();
            assertThat(stats.getRecentJobs()).isEmpty();
        }

        @Test
        @DisplayName("failed job has null durationSeconds in recent runs")
        void failedJobHasNullDuration() {
            Instant now = Instant.now();

            when(toolRepository.findById(10L)).thenReturn(Optional.of(tool));
            when(analysisJobRepository.findAggregateStatsByToolId(10L))
                    .thenReturn(List.<Object[]>of(aggregateRow(1L, 0L, 1L, 0L, null, now)));
            when(analysisJobRepository.findTop10ByToolIdOrderByCreatedAtDesc(10L))
                    .thenReturn(List.of(failedJob(5L, now)));

            ToolStatsDto stats = toolStatsService.getStats(10L, ownerAuth);

            assertThat(stats.getRecentJobs().get(0).getDurationSeconds()).isNull();
        }
    }
}
