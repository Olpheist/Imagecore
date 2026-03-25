<template>
  <Card variant="outlined" rounded class="w-full max-w-2xl">
    <h2 class="text-lg font-semibold text-gray-800 mb-4">Upload DICOM Images</h2>

    <!-- Drop zone -->
    <div
      class="border-2 border-dashed rounded-lg p-8 text-center cursor-pointer transition-colors"
      :class="isDragging ? 'border-blue-400 bg-blue-50' : 'border-gray-300 hover:border-blue-400'"
      @click="openFilePicker"
      @dragover.prevent="isDragging = true"
      @dragleave.prevent="isDragging = false"
      @drop.prevent="onDrop"
    >
      <input
        ref="fileInput"
        type="file"
        accept=".dcm"
        multiple
        class="hidden"
        @change="onFileChange"
      />
      <template v-if="selectedFiles.length === 0">
        <p class="text-sm text-gray-500">
          Drag and drop <span class="font-mono">.dcm</span> files here, or click to select
        </p>
      </template>
      <template v-else>
        <ul class="space-y-1 text-left">
          <li
            v-for="file in selectedFiles"
            :key="file.name"
            class="flex items-center justify-between text-sm"
          >
            <span class="font-medium text-gray-800 truncate max-w-xs">{{ file.name }}</span>
            <span class="text-xs text-gray-500 ml-4 shrink-0">{{ formatBytes(file.size) }}</span>
          </li>
        </ul>
        <p class="text-xs text-gray-400 mt-3">{{ selectedFiles.length }} file{{ selectedFiles.length !== 1 ? 's' : '' }} selected</p>
      </template>
    </div>

    <!-- Success -->
    <div
      v-if="uploadedCount > 0"
      class="mt-4 rounded-lg border border-green-200 bg-green-50 p-3 text-sm text-green-800"
    >
      {{ uploadedCount }} file{{ uploadedCount !== 1 ? 's' : '' }} uploaded successfully.
    </div>

    <!-- Error -->
    <Error :error="uploadError" dismissible class="mt-4" @close="uploadError = null" />

    <!-- Actions -->
    <div class="mt-4 flex gap-3">
      <Button
        variant="primary"
        rounded
        :disabled="selectedFiles.length === 0 || uploading"
        @click="doUpload"
      >
        {{ uploading ? 'Uploading…' : 'Upload' }}
      </Button>
      <Button
        v-if="selectedFiles.length > 0"
        variant="secondary"
        rounded
        :disabled="uploading"
        @click="reset"
      >
        Clear
      </Button>
    </div>
  </Card>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { getToken } from '~/utils/authToken';
import { isExpired } from '~/utils/jwt';
import { navigateTo } from 'nuxt/app';
import type { ApiError } from '~/models/error';
import type { DicomImageDto } from '~/models/dicom';
import { formatBytes } from '~/utils/formatters';

const emit = defineEmits<{
  uploaded: [images: DicomImageDto[]]
}>()

const fileInput = useTemplateRef<HTMLInputElement>('fileInput');
const selectedFiles = ref<File[]>([]);
const isDragging = ref(false);
const uploading = ref(false);
const uploadError = ref<ApiError | null>(null);
const uploadedCount = ref(0);

function openFilePicker() {
  fileInput.value?.click();
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  selectedFiles.value = Array.from(input.files ?? []);
  uploadError.value = null;
  uploadedCount.value = 0
}

function onDrop(event: DragEvent) {
  isDragging.value = false;
  const dropped = Array.from(event.dataTransfer?.files ?? []).filter((f) =>
    f.name.toLowerCase().endsWith('.dcm')
  );
  if (dropped.length > 0) {
    selectedFiles.value = dropped;
    uploadError.value = null;
    uploadedCount.value = 0;
  }
}

function reset() {
  selectedFiles.value = [];
  uploadError.value = null;
  uploadedCount.value = 0
  if (fileInput.value) fileInput.value.value = '';
}

async function doUpload() {
  if (selectedFiles.value.length === 0) return;

  const token = getToken();
  if (!token || isExpired(token)) {
    await navigateTo('/login');
    return;
  }

  uploading.value = true;
  uploadError.value = null;
  uploadedCount.value = 0

  const baseUrl = import.meta.dev ? 'http://localhost:8080/api' : '/api';
  const formData = new FormData();
  for (const file of selectedFiles.value) {
    formData.append('files', file);
  }

  try {
    const res = await fetch(`${baseUrl}/images/upload-batch`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: formData,
    });

    if (res.status === 401) {
      await navigateTo('/login');
      return;
    }

    if (!res.ok) {
      const contentType = res.headers.get('content-type') ?? '';
      if (contentType.includes('application/json')) {
        const data = (await res.json()) as Partial<ApiError>;
        uploadError.value = {
          status: data.status ?? res.status,
          error: data.error ?? res.statusText,
          message: data.message ?? `Upload failed (${res.status})`,
          path: data.path ?? '/images/upload-batch',
          details: data.details ?? null,
          timestamp: data.timestamp,
        };
      } else {
        uploadError.value = {
          status: res.status,
          error: res.statusText,
          message: `Upload failed (${res.status})`,
          path: '/images/upload-batch',
          details: null,
        };
      }
      return;
    }

    const images = (await res.json()) as DicomImageDto[];
    uploadedCount.value = images.length;
    selectedFiles.value = [];
    if (fileInput.value) fileInput.value.value = '';
    emit('uploaded', images);
  } catch {
    uploadError.value = {
      status: 0,
      error: 'Network Error',
      message: 'Could not reach the server. Please check your connection.',
      path: '/images/upload-batch',
      details: null,
    };
  } finally {
    uploading.value = false;
  }
}
</script>
