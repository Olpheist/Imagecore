export interface PipelineStepDto {
  id: number
  stepOrder: number
  toolId: number
  toolName: string
  inputImageId: number | null
  analysisJobId: number | null
  outputImageId: number | null
  status: string
}

export interface PipelineDto {
  id: number
  imageId: number
  status: string
  createdAt: string
  steps: PipelineStepDto[]
}
