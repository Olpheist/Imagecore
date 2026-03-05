// src/models/dicom.ts

export interface DicomSeries {
  id: string
  userId: number
  studyUid: string
  seriesUid: string
  description: string
  seriesDescription: string
  bodyPart: string
  instanceCount: number
  studyDate: string
  physician: string
  patientId: string
  imageUrl: string | null   // null locally, presigned S3 URL if we want to provide this
  wadoRsRoot?: string     // base URL for WADO-RS / AWS HealthImaging
  numberOfFrames?: number // frame count for multi-frame series
}

export type ViewerTool = 'scroll' | 'wwwc' | 'zoom' | 'pan'

export type ViewportLayout = '1x1' | '1x2' | '2x2'