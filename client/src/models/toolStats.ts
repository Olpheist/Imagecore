export interface RecentJobDto {
  id: number
  status: string
  durationSeconds: number | null
  createdAt: string
}

export interface ToolStatsDto {
  totalRuns: number
  completedRuns: number
  failedRuns: number
  pendingOrRunningRuns: number
  successRatePct: number
  avgCompletionSeconds: number | null
  lastRunAt: string | null
  recentJobs: RecentJobDto[]
}
