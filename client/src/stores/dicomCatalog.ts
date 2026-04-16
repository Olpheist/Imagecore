import { defineStore } from 'pinia';
import { ref } from 'vue';
import { useApiFetch } from '~/composables/useApiFetch';
import type { DicomImageSetGroup } from '~/models/dicom';
import type { ApiError } from '~/models/error';

export const useDicomCatalogStore = defineStore('dicomCatalog', () => {
  const series  = ref<DicomImageSetGroup[]>([]);
  const loading = ref(false);
  const error   = ref<ApiError | null>(null);

  // Keep seriesGroups as an alias so catalog.vue doesn't need changes
  const seriesGroups = series;

  async function fetchImages(): Promise<void> {
    loading.value = true;
    error.value   = null;
    try {
      series.value = await useApiFetch<DicomImageSetGroup[]>('/images/series');
    } catch (e: unknown) {
      error.value = e as ApiError;
    } finally {
      loading.value = false;
    }
  }

  /**
   * Deletes all DB records (and their HealthImaging imageSets / S3 objects) for a series.
   * After deletion, removes the group from the local list.
   */
  async function deleteImageSet(imageIds: number[]): Promise<void> {
    for (const id of imageIds) {
      // Let errors propagate so the caller can surface them. The backend already
      // swallows ResourceNotFoundException (404) from HealthImaging when multiple
      // DB rows share the same imageSetId, so we don't need to guard here.
      await useApiFetch(`/images/${id}`, { method: 'DELETE' });
    }
    // Only remove the group from local state once all deletes succeed
    const deletedSet = new Set(imageIds);
    series.value = series.value.filter(
      g => !g.imageIds.every(id => deletedSet.has(id))
    );
  }

  return { series, seriesGroups, loading, error, fetchImages, deleteImageSet };
});
