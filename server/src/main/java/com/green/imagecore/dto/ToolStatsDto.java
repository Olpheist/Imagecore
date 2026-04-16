package com.green.imagecore.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class ToolStatsDto {
    private long totalRuns;
    private long completedRuns;
    private long failedRuns;
    private long pendingOrRunningRuns;
    private double successRatePct;
    private Double avgCompletionSeconds;
    private Instant lastRunAt;
    private List<RecentJobDto> recentJobs;
}
