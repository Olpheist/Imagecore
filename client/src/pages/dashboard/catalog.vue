<template>
  <div class="min-h-screen px-6 py-12">
    <!-- Header -->
    <div class="max-w-6xl mx-auto mb-10 flex items-start justify-between">
      <div>
        <h1 class="text-2xl font-semibold">My DICOM Images</h1>
        <p class="text-sm text-gray-500 mt-1">
          Manage your uploaded medical images
        </p>
      </div>
    </div>

    <!-- Error -->
    <div class="max-w-6xl mx-auto mb-6">
      <Error :error="catalogStore.error" dismissible @close="catalogStore.error = null" />
      <Error :error="jobError" dismissible @close="jobError = null" />
    </div>

    <!-- Loading Skeleton -->
    <div v-if="catalogStore.loading" class="max-w-6xl mx-auto">
      <Card variant="elevated" rounded class="animate-pulse">
        <div class="space-y-3 p-2">
          <div v-for="n in 4" :key="n" class="h-4 bg-gray-100 rounded w-full" />
        </div>
      </Card>
    </div>

    <!-- Table -->
    <div v-else class="max-w-6xl mx-auto">
      <Table
        :columns="columns"
        :rows="catalogStore.seriesGroups"
        row-key="key"
      >
        <template #cell-displayName="{ value }">
          <span class="font-medium text-gray-800 block truncate" :title="value">{{ value }}</span>
        </template>
        <template #cell-modality="{ value }">
          <span class="font-mono text-xs bg-violet-50 text-violet-700 border border-violet-200 rounded-full px-2 py-0.5">
            {{ value ?? '—' }}
          </span>
        </template>
        <template #cell-bodyPart="{ value }">
          {{ value ?? '—' }}
        </template>
        <template #cell-studyDate="{ value }">
          {{ value ? formatDate(value) : '—' }}
        </template>
        <template #cell-instanceCount="{ value }">
          {{ value }}
        </template>
        <template #cell-status="{ value }">
          <span
            class="text-xs font-semibold rounded-full px-2 py-0.5 border"
            :class="{
              'bg-green-50 text-green-700 border-green-200':    value === 'COMPLETED',
              'bg-yellow-50 text-yellow-700 border-yellow-200': value === 'IN_PROGRESS' || value === 'SUBMITTED',
              'bg-red-50 text-red-700 border-red-200':          value === 'FAILED',
              'bg-gray-50 text-gray-500 border-gray-200':       value === 'PENDING',
            }"
          >
            {{ value }}
          </span>
        </template>
        <template #cell-actions="{ row }">
          <div class="flex gap-1.5 flex-nowrap">
            <Button
              variant="primary"
              size="sm"
              rounded
              :disabled="row.status !== 'COMPLETED'"
              :title="row.status !== 'COMPLETED' ? 'Image not yet ready' : 'View image in DICOM viewer'"
              @click="viewInViewer(row)"
            >
              View Image
            </Button>
            <Button
              variant="secondary"
              size="sm"
              rounded
              :disabled="row.status !== 'COMPLETED' || submittingGroupKey === row.key"
              :title="row.status !== 'COMPLETED' ? 'Image not yet ready' : 'Run an analysis tool'"
              @click="onOpenRunToolModal(row)"
            >
              {{ submittingGroupKey === row.key ? 'Submitting…' : 'Run Tool' }}
            </Button>
            <Button
              variant="success"
              size="sm"
              rounded
              :disabled="row.status !== 'COMPLETED'"
              :title="row.status !== 'COMPLETED' ? 'Image not yet ready' : 'Start an analysis tool workflow'"
              @click="onRunToolWorkflowClick(row)"
            >
              Run Tool Workflow
            </Button>
            <Button
              v-if="lastJobByGroupKey.get(row.key)"
              size="sm"
              rounded
              class="!bg-purple-400 hover:!bg-purple-500 text-white"
              title="Download the analysis report (available once the tool finishes)"
              @click="onDownloadReport(row)"
            >
              Download Report
            </Button>
            <Button
              variant="danger"
              size="sm"
              rounded
              :disabled="deletingKey === row.key"
              @click="onDeleteClick(row)"
            >
              {{ deletingKey === row.key ? 'Deleting…' : 'Delete' }}
            </Button>
          </div>
        </template>
      </Table>
    </div>

    <!-- Run Tool Modal -->
    <Modal
      v-model="showRunToolModal"
      :title="selectedCategory ?? 'Run Tool'"
      size="md"
      @update:model-value="onRunToolModalClose"
    >
      <!-- Category view -->
      <div v-if="selectedCategory === null">
        <p v-if="toolsLoading" class="text-sm text-gray-400">Loading…</p>
        <p v-else-if="toolsByCategory.size === 0" class="text-sm text-gray-400">No tools available</p>
        <div v-else class="flex flex-col gap-2">
          <button
            v-for="[category] in toolsByCategory"
            :key="category"
            class="w-full text-left px-4 py-3 rounded-xl border border-gray-200 hover:border-gray-300 hover:bg-gray-50 transition-colors flex items-center justify-between cursor-pointer"
            @click="selectedCategory = category"
          >
            <span class="text-sm font-medium text-gray-800">{{ category }}</span>
            <svg class="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" />
            </svg>
          </button>
        </div>
      </div>

      <!-- Tool list view -->
      <div v-else>
        <button
          class="flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 mb-4 cursor-pointer"
          @click="selectedCategory = null"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
          Back
        </button>
        <div class="flex flex-col gap-2">
          <button
            v-for="tool in toolsByCategory.get(selectedCategory)"
            :key="tool.toolId"
            class="w-full text-left px-4 py-3 rounded-xl border border-gray-200 hover:border-gray-300 hover:bg-gray-50 transition-colors cursor-pointer"
            @click="onRunToolSelect(runToolGroup!, tool)"
          >
            <p class="text-sm font-medium text-gray-800">{{ tool.name }}</p>
            <p v-if="tool.description" class="text-xs text-gray-500 mt-0.5 line-clamp-2">{{ tool.description }}</p>
          </button>
        </div>
      </div>
    </Modal>

    <!-- Delete Confirmation Modal -->
    <Modal
      v-model="showDeleteModal"
      title="Delete Image Set"
      description="This action cannot be undone."
      size="sm"
    >
      <p class="text-sm text-gray-700">
        Are you sure you want to delete
        <span class="font-medium">{{ pendingDelete?.displayName }}</span>?
        <span v-if="pendingDelete && pendingDelete.imageIds.length > 1" class="text-gray-500">
          ({{ pendingDelete.imageIds.length }} record{{ pendingDelete.imageIds.length !== 1 ? 's' : '' }})
        </span>
      </p>

      <template #footer>
        <Button variant="secondary" rounded hover @click="showDeleteModal = false">Cancel</Button>
        <Button variant="danger" rounded hover :disabled="deletingKey !== null" @click="confirmDelete">
          {{ deletingKey !== null ? 'Deleting…' : 'Delete' }}
        </Button>
      </template>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useDicomCatalogStore } from '~/stores/dicomCatalog';
import { useApiFetch } from '~/composables/useApiFetch';
import type { DicomImageSetGroup } from '~/models/dicom';
import type { ToolDto } from '~/models/tool';
import type { AnalysisJobDto } from '~/models/analysisJob';
import type { ApiError } from '~/models/error';
import type { Column } from '~/components/Table.vue';
import { formatDate } from '~/utils/formatters';
import { capitalizeFirstLetter } from '~/utils/stringFunctions';

const catalogStore = useDicomCatalogStore();
const router = useRouter();

const columns: Column[] = [
  { key: 'displayName',   label: 'Series / Study', class: 'w-[22%]'  },
  { key: 'modality',      label: 'Modality',       class: 'w-[8%]'   },
  { key: 'bodyPart',      label: 'Body Part',      class: 'w-[10%]'  },
  { key: 'studyDate',     label: 'Study Date',     class: 'w-[10%]'  },
  { key: 'instanceCount', label: 'Instances',      class: 'w-[8%]'   },
  { key: 'status',        label: 'Status',         class: 'w-[10%]'  },
  { key: 'actions',       label: 'Actions',        class: 'w-[32%]'  },
];

// Tools for Run Tool modal
const tools        = ref<ToolDto[]>([]);
const toolsLoading = ref(false);

// Run Tool modal state
const showRunToolModal = ref(false);
const runToolGroup     = ref<DicomImageSetGroup | null>(null);
const selectedCategory = ref<string | null>(null);

const toolsByCategory = computed(() => {
  const map = new Map<string, ToolDto[]>();
  for (const tool of tools.value) {
    const cat = capitalizeFirstLetter(tool.category);
    if (!map.has(cat)) map.set(cat, []);
    map.get(cat)!.push(tool);
  }
  return map;
});

async function fetchTools(): Promise<void> {
  toolsLoading.value = true;
  try {
    tools.value = await useApiFetch<ToolDto[]>('/tools');
  } finally {
    toolsLoading.value = false;
  }
}

// Job submission state
const jobError          = ref<ApiError | null>(null);
const submittingGroupKey = ref<string | null>(null);
const lastJobByGroupKey  = ref<Map<string, AnalysisJobDto>>(new Map());

// Delete state
const showDeleteModal = ref(false);
const pendingDelete   = ref<DicomImageSetGroup | null>(null);
const deletingKey     = ref<string | null>(null);

function viewInViewer(group: DicomImageSetGroup) {
  const query = group.seriesInstanceUid
    ? { seriesUid: group.seriesInstanceUid }
    : {};
  router.push({ path: '/dashboard/dicom', query });
}

function onOpenRunToolModal(group: DicomImageSetGroup) {
  runToolGroup.value     = group;
  selectedCategory.value = null;
  showRunToolModal.value = true;
}

function onRunToolModalClose() {
  selectedCategory.value = null;
  runToolGroup.value     = null;
}

async function onRunToolSelect(group: DicomImageSetGroup, tool: ToolDto) {
  showRunToolModal.value  = false;
  selectedCategory.value  = null;
  jobError.value          = null;
  submittingGroupKey.value = group.key;
  try {
    const imageId = group.imageIds[0];
    const job = await useApiFetch<AnalysisJobDto>(`/images/${imageId}/jobs`, {
      method: 'POST',
      body: { toolId: tool.toolId },
    });
    lastJobByGroupKey.value = new Map(lastJobByGroupKey.value).set(group.key, job);
  } catch (e: unknown) {
    jobError.value = e as ApiError;
  } finally {
    submittingGroupKey.value = null;
  }
}

function onDownloadReport(group: DicomImageSetGroup) {
  const job = lastJobByGroupKey.value.get(group.key);
  if (!job) return;
  const imageId = group.imageIds[0];
  window.location.href = `/api/images/${imageId}/jobs/${job.id}/report`;
}

function onRunToolWorkflowClick(_group: DicomImageSetGroup) {
  // TODO: open tool workflow page (next sprint)
}

function onDeleteClick(group: DicomImageSetGroup) {
  pendingDelete.value   = group;
  showDeleteModal.value = true;
}

async function confirmDelete() {
  if (!pendingDelete.value) return;
  deletingKey.value = pendingDelete.value.key;
  try {
    await catalogStore.deleteImageSet(pendingDelete.value.imageIds);
    showDeleteModal.value = false;
    pendingDelete.value   = null;
  } catch (e: unknown) {
    catalogStore.error = e as ApiError;
    showDeleteModal.value = false;
  } finally {
    deletingKey.value = null;
  }
}

onMounted(() => {
  catalogStore.fetchImages();
  fetchTools();
});
</script>
