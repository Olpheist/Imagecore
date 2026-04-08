// src/models/dicom.ts

export interface DicomImageDto {
  id: number
  filename: string
  fileSize: number
  fileCount: number
  uploadedAt: string
  importStatus: string
  imageSetId: string | null
  studyInstanceUid: string | null
  seriesInstanceUid: string | null
  sopInstanceUid: string | null
  studyDescription: string | null
  seriesDescription: string | null
  bodyPart: string | null
  modality: string | null
  patientId: string | null
  studyDate: string | null
  physician: string | null
  instanceNumber: number | null
  frameCount: number | null
  /** All SOP UIDs in this image set sorted by InstanceNumber. Populated for batch uploads. */
  sopInstanceUids: string[] | null
}

export interface DicomImageSetGroup {
  key: string                      // imageSetId or "__pending__{id}" — stable row key
  imageSetId: string | null
  displayName: string              // seriesDescription ?? studyDescription ?? imageSetId ?? filename
  modality: string | null
  bodyPart: string | null
  studyDate: string | null
  instanceCount: number            // actual DICOM slice count from HealthImaging (frame_count sum)
  status: string                   // most advanced importStatus in the group
  seriesInstanceUid: string | null
  studyInstanceUid: string | null
  imageIds: number[]               // DB record IDs in this group — used for deletion
}

export interface DicomSeries {
  id: string
  userId: number
  studyUid: string
  seriesUid: string
  sopInstanceUid: string
  description: string
  seriesDescription: string
  modality: string
  bodyPart: string
  instanceCount: number
  studyDate: string
  physician: string
  patientId: string
  imageUrl: string | null
  imageSetId?: string | null  // AWS HealthImaging imageSetId, used as fallback when studyUid is absent
  localFiles?: string[]       // array of local .dcm paths for dev testing (wadouri per file)
  wadoRsRoot?: string         // base URL for WADO-RS / AWS HealthImaging DICOMweb proxy
  numberOfFrames?: number   // total frame count across all instances in this series
  // Ordered list of SOP instance UIDs for series spread across multiple DB records
  // (one upload per slice). When present, one image ID is built per entry using frames/1.
  // When absent (true multi-frame single-SOP), numberOfFrames drives frame ID generation.
  sopInstanceUids?: string[]
}

export type ViewerTool = 'scroll' | 'wwwc' | 'zoom' | 'pan'

export type ViewportLayout = '1x1' | '1x2' | '2x2'