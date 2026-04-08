export interface AnalysisJobDto {
  id: number
  imageId: number
  toolId: number
  toolName: string
  status: string
  ecsTaskArn: string | null
  createdAt: string
}
