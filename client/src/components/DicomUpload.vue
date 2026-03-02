<template>
  <Card variant="outlined" rounded class="w-full max-w-2xl">
    <h2 class="text-lg font-semibold text-gray-800 mb-4">Upload DICOM Image</h2>

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
        class="hidden"
        @change="onFileChange"
      />
      <template v-if="!selectedFile">
        <p class="text-sm text-gray-500">
          Drag and drop a <span class="font-mono">.dcm</span> file here, or click to select
        </p>
      </template>
      <template v-else>
        <p class="font-medium text-gray-800 text-sm">{{ selectedFile.name }}</p>
        <p class="text-xs text-gray-500 mt-1">{{ formatBytes(selectedFile.size) }}</p>
      </template>
    </div>

    <!-- Success -->
    <div
      v-if="successKey"
      class="mt-4 rounded-lg border border-green-200 bg-green-50 p-3 text-sm text-green-800"
    >
      Upload successful.
    </div>

    <!-- Error -->
    <Error :error="uploadError" dismissible class="mt-4" @close="uploadError = null" />

    <!-- Actions -->
    <div class="mt-4 flex gap-3">
      <Button
        variant="primary"
        rounded
        :disabled="!selectedFile || uploading"
        @click="doUpload"
      >
        {{ uploading ? 'Uploading…' : 'Upload' }}
      </Button>
      <Button
        v-if="selectedFile"
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

const fileInput = ref<HTMLInputElement | null>(null);
const selectedFile = ref<File | null>(null);
const isDragging = ref(false);
const uploading = ref(false);
const uploadError = ref<ApiError | null>(null);
const successKey = ref<string | null>(null);

function openFilePicker() {
  fileInput.value?.click();
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  selectedFile.value = input.files?.[0] ?? null;
  uploadError.value = null;
  successKey.value = null;
}

function onDrop(event: DragEvent) {
  isDragging.value = false;
  const file = event.dataTransfer?.files[0] ?? null;
  if (file && file.name.toLowerCase().endsWith('.dcm')) {
    selectedFile.value = file;
    uploadError.value = null;
    successKey.value = null;
  }
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function reset() {
  selectedFile.value = null;
  uploadError.value = null;
  successKey.value = null;
  if (fileInput.value) fileInput.value.value = '';
}

async function doUpload() {
  if (!selectedFile.value) return;

  const token = getToken();
  if (!token || isExpired(token)) {
    await navigateTo('/login');
    return;
  }

  uploading.value = true;
  uploadError.value = null;
  successKey.value = null;

  const baseUrl = import.meta.dev ? 'http://localhost:8080/api' : '/api';
  const formData = new FormData();
  formData.append('file', selectedFile.value);

  try {
    const res = await fetch(`${baseUrl}/images/upload`, {
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
          path: data.path ?? '/images/upload',
          details: data.details ?? null,
          timestamp: data.timestamp,
        };
      } else {
        uploadError.value = {
          status: res.status,
          error: res.statusText,
          message: `Upload failed (${res.status})`,
          path: '/images/upload',
          details: null,
        };
      }
      return;
    }

    const data = (await res.json()) as { key: string };
    successKey.value = data.key;
    selectedFile.value = null;
    if (fileInput.value) fileInput.value.value = '';
  } catch {
    uploadError.value = {
      status: 0,
      error: 'Network Error',
      message: 'Could not reach the server. Please check your connection.',
      path: '/images/upload',
      details: null,
    };
  } finally {
    uploading.value = false;
  }
}
</script>
