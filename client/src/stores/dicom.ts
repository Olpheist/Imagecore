import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { DicomSeries, DicomImageDto } from '@/models/dicom'
import { useApiFetch } from '@/composables/useApiFetch'

/**
 * Returns the API base URL used by the DICOMweb proxy.
 * Must match the backend's /api/dicomweb endpoints.
 */
function getDicomWebRoot(): string {
  return import.meta.dev ? 'http://localhost:8080/api/dicomweb' : '/api/dicomweb'
}

/**
 * Dev-only: loads a local DICOM series from client/public/dicom-samples/.
 *
 * To use:
 *  1. Create client/public/dicom-samples/ and drop .dcm files into it.
 *  2. Create client/public/dicom-samples/index.json listing the filenames, e.g.:
 *     ["slice_001.dcm", "slice_002.dcm", "slice_003.dcm"]
 *  3. The viewer will load them in order using wadouri: scheme.
 */
async function loadLocalSamples(): Promise<DicomSeries[]> {
  try {
    const res = await fetch('/dicom-samples/index.json')

    if (!res.ok) throw new Error(`HTTP ${res.status}`)

    const filenames: string[] = await res.json()

    if (!filenames.length) return []

    return [{
      id: 'local-dev-series',
      userId: 0,
      studyUid: 'local.study.1',
      seriesUid: 'local.series.1',
      sopInstanceUid: 'local.sop.1',
      description: 'Local Dev Series',
      seriesDescription: `${filenames.length} slices`,
      modality: 'MR',
      bodyPart: 'Unknown',
      instanceCount: filenames.length,
      studyDate: new Date().toISOString().slice(0, 10),
      physician: '',
      patientId: 'LOCAL',
      imageUrl: null,
      localFiles: filenames.map(f => `/dicom-samples/${f}`),
    }]
  } catch (err) {
    console.error('[dicom-store] failed:', err)
    return []
  }
}

export const useDicomStore = defineStore('dicom', () => {
  const series    = ref<DicomSeries[]>([])
  const loading   = ref(false)
  const error     = ref<string | null>(null)

  /**
   * Fetches uploaded DICOM images from the backend and groups completed imports
   * into DicomSeries entries — one card per unique series (seriesInstanceUid).
   *
   * Within each series, instances are sorted by instanceNumber (DICOM tag 00200013)
   * so that slices render in the correct anatomical order.
   *
   * In dev mode, if no completed images are returned, falls back to loading local
   * .dcm files from client/public/dicom-samples/.
   */
  async function fetchSeries(): Promise<void> {
    loading.value = true
    error.value   = null
    try {
      let images = await useApiFetch<DicomImageDto[]>('/images')

      // Auto-sync any images that are incomplete: no imageSetId, or COMPLETED but missing
      // critical DICOM UIDs (happens when a prior metadata parse extracted the imageSetId
      // but failed to read series/study UIDs from HealthImaging metadata).
      const unsynced = images.filter(img =>
        img.importStatus !== 'FAILED' &&
        (!img.imageSetId || !img.seriesInstanceUid || !img.sopInstanceUid || !img.sopInstanceUids)
      )
      if (unsynced.length > 0) {
        const syncResults = await Promise.all(
          unsynced.map(img =>
            useApiFetch<DicomImageDto>(`/images/${img.id}/status`).catch(() => img)
          )
        )
        const syncedById = new Map(syncResults.map(img => [img.id, img]))
        images = images.map(img => syncedById.get(img.id) ?? img)
      }

      const completed = images.filter(img => img.importStatus === 'COMPLETED')

      // Group by seriesInstanceUid so that individually-uploaded slices of the same
      // series collapse into a single viewer card instead of one card per file.
      const bySeriesUid = new Map<string, DicomImageDto[]>()
      for (const img of completed) {
        const key = img.seriesInstanceUid ?? String(img.id)
        if (!bySeriesUid.has(key)) bySeriesUid.set(key, [])
        bySeriesUid.get(key)!.push(img)
      }

      console.log(`[dicom-store] fetched ${images.length} images, ${completed.length} completed, ${bySeriesUid.size} series`)

      series.value = [...bySeriesUid.values()].map(group => {
        // Sort slices by instanceNumber ascending; nulls go last
        group.sort((a, b) => {
          const na = a.instanceNumber ?? Number.MAX_SAFE_INTEGER
          const nb = b.instanceNumber ?? Number.MAX_SAFE_INTEGER
          return na - nb
        })

        const first = group[0]

        // Build ordered SOP UID list from per-record sopInstanceUids (batch uploads have N SOPs
        // in one record), falling back to the single sopInstanceUid for legacy single-upload records.
        const sopInstanceUids: string[] = []
        for (const img of group) {
          if (img.sopInstanceUids && img.sopInstanceUids.length > 0) {
            sopInstanceUids.push(...img.sopInstanceUids)
          } else if (img.sopInstanceUid) {
            sopInstanceUids.push(img.sopInstanceUid)
          }
        }

        // Total frames = number of distinct SOP instances (one frame each for typical MR slices).
        // Falls back to summing frameCount for true multi-frame single-SOP series.
        const totalFrames = sopInstanceUids.length > 0
          ? sopInstanceUids.length
          : group.reduce((sum, img) => sum + (img.frameCount ?? 1), 0)

        return {
          id: String(first.id),
          userId: 0,
          studyUid: first.studyInstanceUid ?? '',
          seriesUid: first.seriesInstanceUid ?? '',
          sopInstanceUid: sopInstanceUids[0] ?? '',
          sopInstanceUids,
          description: first.seriesDescription ?? first.studyDescription ?? first.imageSetId ?? first.filename,
          seriesDescription: first.seriesDescription ?? '',
          modality: first.modality ?? 'MR',
          bodyPart: first.bodyPart ?? 'Unknown',
          instanceCount: sopInstanceUids.length > 0 ? sopInstanceUids.length : group.length,
          studyDate: first.studyDate ?? first.uploadedAt,
          physician: first.physician ?? '',
          patientId: first.patientId ?? '',
          imageUrl: null,
          imageSetId: first.imageSetId,
          wadoRsRoot: getDicomWebRoot(),
          numberOfFrames: totalFrames,
        }
      })

      console.log('[dicom-store] loaded series:', series.value)

      // Dev fallback: load local .dcm files only when the DB has no images at all.
      // If images exist but none are COMPLETED+imageSetId (still processing), show
      // an empty viewer rather than silently replacing with unrelated local samples.
      if (import.meta.dev && images.length === 0) {
        const local = await loadLocalSamples()
        series.value = local
      }
    } catch (e) {
      // In dev mode with no backend available, fall back to local samples
      if (import.meta.dev) {
        const local = await loadLocalSamples()
        if (local.length > 0) {
          series.value = local
          return
        }
      }
      error.value = 'Failed to load studies.'
    } finally {
      loading.value = false
    }
  }

  return { series, loading, error, fetchSeries }
})
