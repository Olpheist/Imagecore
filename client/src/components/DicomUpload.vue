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
        <!-- Compact summary -->
        <div class="text-center">
          <p class="text-sm font-medium text-gray-700">
            {{ selectedFiles.length }} file{{ selectedFiles.length !== 1 ? 's' : '' }} selected
            <span class="text-xs text-gray-500">({{ formatBytes(totalSize) }})</span>
          </p>

          <!-- Expandable file list -->
          <button
            type="button"
            class="mt-2 text-xs text-blue-600 hover:text-blue-800 underline"
            @click.stop="showFileList = !showFileList"
          >
            {{ showFileList ? '▼' : '▶' }} {{ showFileList ? 'Hide' : 'Show' }} files
          </button>

          <ul v-if="showFileList" class="space-y-1 text-left mt-3 max-h-48 overflow-y-auto">
            <li
              v-for="file in selectedFiles"
              :key="file.name"
              class="flex items-center justify-between text-sm"
            >
              <span class="font-medium text-gray-800 truncate max-w-xs">{{ file.name }}</span>
              <span class="text-xs text-gray-500 ml-4 shrink-0">{{ formatBytes(file.size) }}</span>
            </li>
          </ul>
        </div>
      </template>
    </div>

    <!-- Upload progress bar -->
    <div v-if="uploadProgress > 0 && uploadProgress < 100" class="mt-4">
      <div class="flex justify-between text-xs text-gray-500 mb-1">
        <span>Uploading…</span>
        <span>{{ uploadProgress }}%</span>
      </div>
      <div class="w-full bg-gray-200 rounded-full h-2">
        <div
          class="bg-blue-500 h-2 rounded-full transition-all duration-150"
          :style="{ width: uploadProgress + '%' }"
        />
      </div>
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
import { ref, computed } from 'vue';
import type { ApiError } from '~/models/error';
import type { DicomImageDto } from '~/models/dicom';
import { formatBytes } from '~/utils/formatters';
import { getBaseUrl, ensureCsrfCookie } from '~/composables/useApiFetch';

const emit = defineEmits<{
  uploaded: [image: DicomImageDto]
}>()

const fileInput = useTemplateRef<HTMLInputElement>('fileInput');
const selectedFiles = ref<File[]>([]);
const isDragging = ref(false);
const showFileList = ref(false);
const uploadProgress = ref(0); // 0–100; 0 = idle, 1–99 = in progress, 100 = server processing
const uploading = computed(() => uploadProgress.value > 0 && uploadProgress.value < 100);
const uploadError = ref<ApiError | null>(null);
const uploadedCount = ref(0);
const totalSize = computed(() => selectedFiles.value.reduce((sum, f) => sum + f.size, 0));

function openFilePicker() {
  fileInput.value?.click();
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  selectedFiles.value = Array.from(input.files ?? []);
  uploadError.value = null;
  uploadedCount.value = 0;
  showFileList.value = false;
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
    showFileList.value = false;
  }
}

function reset() {
  selectedFiles.value = [];
  uploadError.value = null;
  uploadedCount.value = 0;
  uploadProgress.value = 0;
  showFileList.value = false;
  if (fileInput.value) fileInput.value.value = '';
}

async function doUpload() {
  if (selectedFiles.value.length === 0) return;

  uploadError.value = null;
  uploadedCount.value = 0;
  uploadProgress.value = 1; // show bar immediately

  const formData = new FormData();
  for (const file of selectedFiles.value) {
    formData.append('files', file);
  }

  await ensureCsrfCookie();
  const csrfToken = document.cookie
    .split('; ')
    .find((c) => c.startsWith('XSRF-TOKEN='))
    ?.split('=')[1];

  return new Promise<void>((resolve) => {
    const xhr = new XMLHttpRequest();

    // Track HTTP upload progress (browser → server). Cap at 90% to leave room for
    // server-side processing (S3 parallel uploads + HealthImaging job submission).
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        uploadProgress.value = Math.max(1, Math.round((event.loaded / event.total) * 90));
      }
    };

    xhr.onload = async () => {
      uploadProgress.value = 0;

      if (xhr.status === 401) {
        await navigateTo('/login');
        resolve();
        return;
      }

      if (xhr.status < 200 || xhr.status >= 300) {
        try {
          const data = JSON.parse(xhr.responseText) as Partial<ApiError>;
          uploadError.value = {
            status: data.status ?? xhr.status,
            error: data.error ?? xhr.statusText,
            message: data.message ?? `Upload failed (${xhr.status})`,
            path: data.path ?? '/images/upload-batch',
            details: data.details ?? null,
            timestamp: data.timestamp,
          };
        } catch {
          uploadError.value = {
            status: xhr.status,
            error: xhr.statusText,
            message: `Upload failed (${xhr.status})`,
            path: '/images/upload-batch',
            details: null,
          };
        }
        resolve();
        return;
      }

      const image = JSON.parse(xhr.responseText) as DicomImageDto;
      uploadedCount.value = image.fileCount;
      selectedFiles.value = [];
      if (fileInput.value) fileInput.value.value = '';
      emit('uploaded', image);
      resolve();
    };

    xhr.onerror = () => {
      uploadProgress.value = 0;
      uploadError.value = {
        status: 0,
        error: 'Network Error',
        message: 'Could not reach the server. Please check your connection.',
        path: '/images/upload-batch',
        details: null,
      };
      resolve();
    };

    xhr.open('POST', `${getBaseUrl()}/images/upload-batch`);
    xhr.withCredentials = true; // Send http-only cookies with request
    if (csrfToken) xhr.setRequestHeader('X-XSRF-TOKEN', decodeURIComponent(csrfToken));
    xhr.send(formData);
  });
}
</script>
