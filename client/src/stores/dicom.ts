import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { DicomSeries } from '@/models/dicom'

/**
 * Local MRI dataset for local development.
 *
 * future implementations: replace this with a Pinia store action
 *   → GET /api/images  (returns DicomSeries[])
 *
 * imageUrl could be populated by something like:
 *   → GET /api/images/{id}/view-url  (returns presigned S3 URL)
 */
export const LOCAL_MRI_STUDIES: DicomSeries[] = [
  {
    id: 'mri-001',
    userId: 0,
    description: 'Brain — T1 Coronal',
    seriesDescription: 'T1 MPRAGE Post-Contrast',
    bodyPart: 'Brain',
    studyUid: '1.2.840.10008.5.1.4.1.1.4.001',
    seriesUid: '1.3.6.1.4.1.9590.100.1.001',
    instanceCount: 1,
    studyDate: '2026-03-01',
    physician: 'Dr. Apple',
    patientId: 'PT-00421',
    imageUrl: '/dicom-samples/mri-001/sample.dcm',
    // replaced by WADO-RS stack via HealthImaging proxy
  },
]

/** *
 * Future migration:
 *   - fetchViewUrl() replaced by WADO-RS series stack via HealthImaging proxy
 */
export const useDicomStore = defineStore('dicom', () => {
  const series    = ref<DicomSeries[]>([])
  const loading   = ref(false)
  const error     = ref<string | null>(null)

  /**
   * Future migration: replace body with API call to fetch series list.
   */
  async function fetchSeries(): Promise<void> {
    loading.value = true
    error.value   = null
    try {
      // Future migration:
      // const { data } = await useApifetch('/api/images')
      // series.value = data
      series.value = LOCAL_MRI_STUDIES
    } catch (e) {
      error.value = 'Failed to load studies.'
    } finally {
      loading.value = false
    }
  }

  /**
   * Future migration: fetches presigned S3 URL and patches the series entry in place.
   */
  async function fetchViewUrl(id: string): Promise<string | null> {
    // Future migration:
    // const { data } = await useApifetch(`/api/images/${id}/view-url`)
    // const entry = series.value.find(s => s.id === id)
    // if (entry) entry.imageUrl = data.url
    // return data.url
    return null
  }

  return { series, loading, error, fetchSeries, fetchViewUrl }
})