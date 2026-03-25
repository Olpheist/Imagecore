import { defineStore } from 'pinia';
import { ref } from 'vue';
import { useApiFetch } from '~/composables/useApiFetch';
import type { DicomImageDto } from '~/models/dicom';
import type { ApiError } from '~/models/error';

export const useDicomCatalogStore = defineStore('dicomCatalog', () => {
  const images  = ref<DicomImageDto[]>([]);
  const loading = ref(false);
  const error   = ref<ApiError | null>(null);

  async function fetchImages(): Promise<void> {
    loading.value = true;
    error.value   = null;
    try {
      images.value = await useApiFetch<DicomImageDto[]>('/images');
    } catch (e: unknown) {
      error.value = e as ApiError;
    } finally {
      loading.value = false;
    }
  }

  async function deleteImage(id: number): Promise<void> {
    await useApiFetch(`/images/${id}`, { method: 'DELETE' });
    images.value = images.value.filter((img) => img.id !== id);
  }

  return { images, loading, error, fetchImages, deleteImage };
})
