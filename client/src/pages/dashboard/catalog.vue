<template>
  <div class="min-h-screen px-6 py-12">
    <div class="max-w-6xl mx-auto mb-10 flex items-start justify-between">
      <div>
        <h1 class="text-2xl font-semibold">My DICOM Images</h1>
        <p class="text-sm text-gray-500 mt-1">
          Manage your uploaded medical images
        </p>
      </div>
    </div>
    <div class="max-w-6xl mx-auto mb-6">
      <Error :error="catalogStore.error" dismissible @close="catalogStore.error = null" />
      <Error :error="jobError" dismissible @close="jobError = null" />
    </div>
    <div v-if="catalogStore.loading" class="max-w-6xl mx-auto">
      <Card variant="elevated" rounded class="animate-pulse">
        <div class="space-y-3 p-2">
          <div v-for="n in 4" :key="n" class="h-4 bg-gray-100 rounded w-full" />
        </div>
      </Card>
    </div>
    <div v-else class="max-w-6xl mx-auto">
      <Table :columns="columns" :rows="catalogStore.seriesGroups" row-key="key">
        <template #cell-displayName="{ value }">
          <span class="font-medium text-slate-800 block truncate" :title="value">{{ value }}</span>
        </template>
        <template #cell-modality="{ value }">
          <span class="font-mono text-sm bg-violet-50 text-violet-700 border border-violet-200 rounded-full px-3 py-1">
            {{ value ?? '—' }}
          </span>
        </template>
        <template #cell-bodyPart="{ value }">
          <span class="text-sm text-slate-700">{{ value ?? '—' }}</span>
        </template>
        <template #cell-studyDate="{ value }">
          <div class="text-sm leading-tight text-slate-700">
            {{ value ? formatDate(value) : '—' }}
          </div>
        </template>
        <template #cell-instanceCount="{ value }">
          <span class="text-sm text-slate-700">{{ value }}</span>
        </template>
        <template #cell-status="{ value }">
          <span
              class="text-xs font-semibold rounded-full px-2 py-0.5 border whitespace-nowrap"
              :class="{
                'bg-green-50 text-green-700 border-green-200': value === 'COMPLETED',
                'bg-yellow-50 text-yellow-700 border-yellow-200': value === 'IN_PROGRESS' || value === 'SUBMITTED',
                'bg-red-50 text-red-700 border-red-200': value === 'FAILED',
                'bg-gray-50 text-gray-500 border-gray-200': value === 'PENDING',
              }"
          >
            {{ value }}
          </span>
        </template>
        <template #cell-viewerAction="{ row }">
          <Button
              variant="primary"
              size="sm"
              rounded
              class="!w-10 !h-10 !p-0 inline-flex items-center justify-center"
              :disabled="row.status !== 'COMPLETED'"
              :title="'View image'"
              @click="viewInViewer(row)"
          >
            <svg
                xmlns="http://www.w3.org/2000/svg"
                class="w-5 h-5 text-white"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2.2"
                stroke-linecap="round"
                stroke-linejoin="round"
            >
              <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z" />
              <circle cx="12" cy="12" r="3" />
            </svg>
          </Button>
        </template>
        <template #cell-toolAction="{ row }">
          <div class="flex flex-col items-center justify-center gap-1">
            <DropdownMenu widthClass="w-52">
              <template #trigger>
                <button
                    type="button"
                    class="flex h-10 w-10 items-center justify-center rounded-xl border border-slate-200 bg-white text-slate-700 shadow-sm transition hover:bg-slate-100 cursor-pointer focus:outline-none focus:ring-2 focus:ring-black focus:ring-offset-1 active:ring-2 active:ring-black"
                    title="More actions"
                >
                  <svg class="h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="5" r="1.5" />
                    <circle cx="12" cy="12" r="1.5" />
                    <circle cx="12" cy="19" r="1.5" />
                  </svg>
                </button>
              </template>

              <template #menu="{ close }">
                <div class="py-2">
                  <button
                      class="flex w-full items-center gap-3 px-4 py-2.5 text-sm font-medium text-slate-800 hover:bg-slate-100 transition disabled:opacity-40 cursor-pointer"
                      :disabled="row.status !== 'COMPLETED' || submittingGroupKey === row.key"
                      @click="onOpenRunToolModal(row); close()"
                  >
                    <svg class="w-4 h-4 text-slate-500" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.868v4.264a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z"/>
                      <path stroke-linecap="round" stroke-linejoin="round" d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/>
                    </svg>
                    <span>{{ submittingGroupKey === row.key ? 'Submitting…' : 'Run Tool' }}</span>
                  </button>

                  <button
                      class="flex w-full items-center gap-3 px-4 py-2.5 text-sm font-medium text-slate-800 hover:bg-slate-100 transition disabled:opacity-40 cursor-pointer"
                      :disabled="row.status !== 'COMPLETED'"
                      @click="onRunToolWorkflowClick(row); close()"
                  >
                    <svg class="w-4 h-4 text-green-600" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                      <rect x="3" y="5" width="6" height="6" rx="1" />
                      <rect x="15" y="5" width="6" height="6" rx="1" />
                      <rect x="9" y="13" width="6" height="6" rx="1" />
                      <path stroke-linecap="round" stroke-linejoin="round" d="M9 8h6" />
                      <path stroke-linecap="round" stroke-linejoin="round" d="M12 11v2" />
                    </svg>
                    <span>Workflow</span>
                  </button>

                  <div v-if="lastJobByGroupKey.get(row.key)" class="my-1 border-t border-slate-200"></div>

                  <button
                      v-if="lastJobByGroupKey.get(row.key)"
                      class="flex w-full items-center gap-3 px-4 py-2.5 text-sm font-medium text-slate-800 hover:bg-slate-100 transition disabled:opacity-40 cursor-pointer"
                      :disabled="lastJobByGroupKey.get(row.key)!.status !== 'COMPLETED'"
                      @click="onDownloadReport(row); close()"
                  >
                    <svg class="w-4 h-4 text-purple-600" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M12 16v-8m0 8l-3-3m3 3l3-3"/>
                      <path stroke-linecap="round" stroke-linejoin="round" d="M4 20h16"/>
                    </svg>
                    <span>Download Report</span>
                  </button>
                </div>
              </template>
            </DropdownMenu>

            <div
                v-if="isPipelineActive(lastPipelineByGroupKey.get(row.key))"
                class="inline-flex items-center gap-1 rounded-full border border-violet-200 bg-violet-50 px-1.5 py-0.5 text-[10px] font-semibold text-violet-700"
                :title="`Workflow is ${lastPipelineByGroupKey.get(row.key)!.status.toLowerCase()}`"
            >
              <svg class="h-2.5 w-2.5 animate-spin shrink-0" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              <span>{{ lastPipelineByGroupKey.get(row.key)!.status }}</span>
            </div>
            <div
                v-if="isJobActive(lastJobByGroupKey.get(row.key))"
                class="inline-flex items-center gap-1 rounded-full border border-violet-200 bg-violet-50 px-1.5 py-0.5 text-[10px] font-semibold text-violet-700"
                :title="`Tool is ${lastJobByGroupKey.get(row.key)!.status.toLowerCase()}`"
            >
              <svg class="h-2.5 w-2.5 animate-spin shrink-0" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              <span>{{ lastJobByGroupKey.get(row.key)!.status }}</span>
            </div>
          </div>
        </template>
        <template #cell-deleteAction="{ row }">
          <div class="flex items-center justify-center px-3">
            <Button
                variant="danger"
                size="sm"
                rounded
                class="inline-flex items-center justify-center"
                :disabled="deletingKey === row.key"
                title="Delete image set"
                aria-label="Delete"
                @click="onDeleteClick(row)"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M3 6h18" />
                <path d="M8 6V4h8v2" />
                <path d="M6 6l1 15h10l1-15" />
                <path d="M10 11v6" />
                <path d="M14 11v6" />
              </svg>
            </Button>
          </div>
        </template>
      </Table>
    </div>
    <Modal v-model="showRunToolModal" :title="selectedCategory ?? 'Run Tool'" size="md" @update:model-value="onRunToolModalClose">
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
      <div v-else>
        <button class="flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 mb-4 cursor-pointer" @click="selectedCategory = null">
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

    <!-- Run Tool Workflow Modal -->
    <Modal
      v-model="showWorkflowModal"
      :title="workflowModalTitle"
      size="md"
      @update:model-value="onWorkflowModalClose"
    >
      <!-- Step header: Run Tools: [tool1] -> [tool2] -->
      <div class="flex items-center gap-2 mb-4 text-sm font-medium text-gray-700">
        <span>Run Tools:</span>
        <span
          class="px-2 py-0.5 rounded-lg border text-xs"
          :class="workflowStep1 ? 'border-violet-300 bg-violet-50 text-violet-700' : 'border-gray-200 bg-gray-50 text-gray-400'"
        >{{ workflowStep1?.name ?? 'blank' }}</span>
        <svg class="w-4 h-4 text-gray-400 flex-shrink-0" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" />
        </svg>
        <span
          class="px-2 py-0.5 rounded-lg border text-xs"
          :class="workflowStep2 ? 'border-violet-300 bg-violet-50 text-violet-700' : 'border-gray-200 bg-gray-50 text-gray-400'"
        >{{ workflowStep2?.name ?? 'blank' }}</span>
      </div>

      <!-- Selecting step 1 -->
      <div v-if="workflowStep1 === null">
        <p class="text-xs text-gray-500 mb-3">Select the first tool</p>
        <p v-if="toolsLoading" class="text-sm text-gray-400">Loading…</p>
        <p v-else-if="toolsByCategory.size === 0" class="text-sm text-gray-400">No tools available</p>
        <div v-else-if="workflowCategory === null" class="flex flex-col gap-2">
          <button
            v-for="[category] in toolsByCategory"
            :key="category"
            class="w-full text-left px-4 py-3 rounded-xl border border-gray-200 hover:border-gray-300 hover:bg-gray-50 transition-colors flex items-center justify-between cursor-pointer"
            @click="workflowCategory = category"
          >
            <span class="text-sm font-medium text-gray-800">{{ category }}</span>
            <svg class="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" />
            </svg>
          </button>
        </div>
        <div v-else>
          <button
            class="flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 mb-4 cursor-pointer"
            @click="workflowCategory = null"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M15 19l-7-7 7-7" />
            </svg>
            Back
          </button>
          <div class="flex flex-col gap-2">
            <button
              v-for="tool in toolsByCategory.get(workflowCategory)"
              :key="tool.toolId"
              class="w-full text-left px-4 py-3 rounded-xl border border-gray-200 hover:border-gray-300 hover:bg-gray-50 transition-colors cursor-pointer"
              @click="onWorkflowStep1Select(tool)"
            >
              <p class="text-sm font-medium text-gray-800">{{ tool.name }}</p>
              <p v-if="tool.description" class="text-xs text-gray-500 mt-0.5 line-clamp-2">{{ tool.description }}</p>
            </button>
          </div>
        </div>
      </div>

      <!-- Selecting step 2 -->
      <div v-else-if="workflowStep2 === null">
        <p class="text-xs text-gray-500 mb-3">Select the second tool</p>
        <div v-if="workflowCategory === null" class="flex flex-col gap-2">
          <button
            v-for="[category] in toolsByCategory"
            :key="category"
            class="w-full text-left px-4 py-3 rounded-xl border border-gray-200 hover:border-gray-300 hover:bg-gray-50 transition-colors flex items-center justify-between cursor-pointer"
            @click="workflowCategory = category"
          >
            <span class="text-sm font-medium text-gray-800">{{ category }}</span>
            <svg class="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" />
            </svg>
          </button>
        </div>
        <div v-else>
          <button
            class="flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 mb-4 cursor-pointer"
            @click="workflowCategory = null"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M15 19l-7-7 7-7" />
            </svg>
            Back
          </button>
          <div class="flex flex-col gap-2">
            <button
              v-for="tool in toolsByCategory.get(workflowCategory)"
              :key="tool.toolId"
              class="w-full text-left px-4 py-3 rounded-xl border border-gray-200 hover:border-gray-300 hover:bg-gray-50 transition-colors cursor-pointer"
              @click="onWorkflowStep2Select(tool)"
            >
              <p class="text-sm font-medium text-gray-800">{{ tool.name }}</p>
              <p v-if="tool.description" class="text-xs text-gray-500 mt-0.5 line-clamp-2">{{ tool.description }}</p>
            </button>
          </div>
        </div>
      </div>

      <!-- Confirm view -->
      <div v-else>
        <p class="text-sm text-gray-700">
          This will run <span class="font-medium">{{ workflowStep1.name }}</span> on the image, then
          automatically run <span class="font-medium">{{ workflowStep2.name }}</span> on the output.
        </p>
      </div>

      <template v-if="workflowStep1 !== null && workflowStep2 !== null" #footer>
        <Button variant="secondary" rounded @click="workflowStep2 = null; workflowCategory = null">Back</Button>
        <Button variant="primary" rounded :disabled="submittingWorkflow" @click="onConfirmWorkflow">
          {{ submittingWorkflow ? 'Submitting…' : 'Run Workflow' }}
        </Button>
      </template>
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
        <Button variant="danger" rounded hover :disabled="deletingKey !== null" @click="confirmDelete" title="Confirm delete">
          {{ deletingKey !== null ? 'Deleting…' : 'Delete' }}
        </Button>
      </template>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { useDicomCatalogStore } from '~/stores/dicomCatalog';
import { useApiFetch } from '~/composables/useApiFetch';
import type { DicomImageSetGroup } from '~/models/dicom';
import type { ToolDto } from '~/models/tool';
import type { AnalysisJobDto } from '~/models/analysisJob';
import type { PipelineDto } from '~/models/pipeline';
import type { ApiError } from '~/models/error';
import type { Column } from '~/components/Table.vue';
import { formatDate } from '~/utils/formatters';
import { capitalizeFirstLetter } from '~/utils/stringFunctions';

const catalogStore = useDicomCatalogStore();
const router = useRouter();

const columns: Column[] = [
  { key: 'displayName', label: 'Series / Study', class: 'w-[25%] text-left' },
  { key: 'modality', label: 'Modality', class: 'w-[9%] text-center' },
  { key: 'bodyPart', label: 'Body Part', class: 'w-[9%] text-center' },
  { key: 'studyDate', label: 'Study Date', class: 'w-[12%] text-center' },
  { key: 'instanceCount', label: 'Instances', class: 'w-[8%] text-center' },
  { key: 'status', label: 'Status', class: 'w-[10%] text-center' },
  { key: 'viewerAction', label: 'Viewer', class: 'w-[7%]' },
  { key: 'toolAction', label: 'Tools', class: 'w-[10%]' },
  { key: 'deleteAction', label: '', class: '' },
];

const tools = ref<ToolDto[]>([]);
const toolsLoading = ref(false);
const showRunToolModal = ref(false);
const runToolGroup = ref<DicomImageSetGroup | null>(null);
const selectedCategory = ref<string | null>(null);
const jobError = ref<ApiError | null>(null);
const submittingGroupKey = ref<string | null>(null);
const lastJobByGroupKey = ref<Map<string, AnalysisJobDto>>(new Map());
const showDeleteModal = ref(false);
const pendingDelete = ref<DicomImageSetGroup | null>(null);
const deletingKey = ref<string | null>(null);
let pollInterval: ReturnType<typeof setInterval> | null = null;

const toolsByCategory = computed(() => {
  const map = new Map<string, ToolDto[]>();
  for (const tool of tools.value) {
    const cat = capitalizeFirstLetter(tool.category);
    if (!map.has(cat)) {
      map.set(cat, []);
    }
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

function isJobActive(job: AnalysisJobDto | undefined): job is AnalysisJobDto {
  return job !== undefined && ['PENDING', 'SUBMITTED', 'RUNNING'].includes(job.status);
}

async function fetchLatestJob(group: DicomImageSetGroup): Promise<void> {
  const imageId = group.imageIds[0];
  try {
    const job = await useApiFetch<AnalysisJobDto>(`/images/${imageId}/jobs/latest`);
    lastJobByGroupKey.value = new Map(lastJobByGroupKey.value).set(group.key, job);
  } catch (e: unknown) {
    if ((e as ApiError).status !== 404) {
      throw e;
    }
  }
}

// Pipeline state
const lastPipelineByGroupKey = ref<Map<string, PipelineDto>>(new Map());

function isPipelineActive(pipeline: PipelineDto | undefined): pipeline is PipelineDto {
  return pipeline !== undefined && ['PENDING', 'RUNNING'].includes(pipeline.status);
}

async function fetchLatestPipeline(group: DicomImageSetGroup): Promise<void> {
  const imageId = group.imageIds[0];
  try {
    const pipeline = await useApiFetch<PipelineDto>(`/images/${imageId}/pipelines/latest`);
    lastPipelineByGroupKey.value = new Map(lastPipelineByGroupKey.value).set(group.key, pipeline);
  } catch (e: unknown) {
    if ((e as ApiError).status !== 404) throw e;
  }
}

// Workflow modal state
const showWorkflowModal  = ref(false);
const workflowGroup      = ref<DicomImageSetGroup | null>(null);
const workflowStep1      = ref<ToolDto | null>(null);
const workflowStep2      = ref<ToolDto | null>(null);
const workflowCategory   = ref<string | null>(null);
const submittingWorkflow = ref(false);

const workflowModalTitle = computed(() => {
  if (workflowStep1.value === null) return 'Select First Tool';
  if (workflowStep2.value === null) return 'Select Second Tool';
  return 'Confirm Workflow';
});

async function pollActive(): Promise<void> {
  const pipelineGroups = catalogStore.seriesGroups.filter(g =>
    isPipelineActive(lastPipelineByGroupKey.value.get(g.key))
  );
  const jobGroups = catalogStore.seriesGroups.filter(g =>
    isJobActive(lastJobByGroupKey.value.get(g.key))
  );
  await Promise.all([
    ...pipelineGroups.map(fetchLatestPipeline),
    ...jobGroups.map(fetchLatestJob),
  ]);
}

function viewInViewer(group: DicomImageSetGroup): void {
  const query = group.seriesInstanceUid ? { seriesUid: group.seriesInstanceUid } : {};
  router.push({ path: '/dashboard/dicom', query });
}

function onOpenRunToolModal(group: DicomImageSetGroup): void {
  runToolGroup.value = group;
  selectedCategory.value = null;
  showRunToolModal.value = true;
}

function onRunToolModalClose(): void {
  selectedCategory.value = null;
  runToolGroup.value = null;
}

async function onRunToolSelect(group: DicomImageSetGroup, tool: ToolDto): Promise<void> {
  showRunToolModal.value = false;
  selectedCategory.value = null;
  jobError.value = null;
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

function onDownloadReport(group: DicomImageSetGroup): void {
  const job = lastJobByGroupKey.value.get(group.key);
  if (!job) {
    return;
  }
  const imageId = group.imageIds[0];
  window.location.href = `/api/images/${imageId}/jobs/${job.id}/report`;
}

function onRunToolWorkflowClick(group: DicomImageSetGroup) {
  workflowGroup.value    = group;
  workflowStep1.value    = null;
  workflowStep2.value    = null;
  workflowCategory.value = null;
  showWorkflowModal.value = true;
}

function onWorkflowModalClose() {
  workflowStep1.value    = null;
  workflowStep2.value    = null;
  workflowCategory.value = null;
  workflowGroup.value    = null;
}

function onWorkflowStep1Select(tool: ToolDto) {
  workflowStep1.value    = tool;
  workflowCategory.value = null;
}

function onWorkflowStep2Select(tool: ToolDto) {
  workflowStep2.value    = tool;
  workflowCategory.value = null;
}

async function onConfirmWorkflow() {
  if (!workflowGroup.value || !workflowStep1.value || !workflowStep2.value) return;
  submittingWorkflow.value = true;
  jobError.value = null;
  try {
    const imageId = workflowGroup.value.imageIds[0];
    const pipeline = await useApiFetch<PipelineDto>(`/images/${imageId}/pipelines`, {
      method: 'POST',
      body: { toolIds: [workflowStep1.value.toolId, workflowStep2.value.toolId] },
    });
    lastPipelineByGroupKey.value = new Map(lastPipelineByGroupKey.value).set(workflowGroup.value.key, pipeline);
    showWorkflowModal.value = false;
  } catch (e: unknown) {
    jobError.value = e as ApiError;
  } finally {
    submittingWorkflow.value = false;
  }
}

function onDeleteClick(group: DicomImageSetGroup): void {
  pendingDelete.value = group;
  showDeleteModal.value = true;
}

async function confirmDelete(): Promise<void> {
  if (!pendingDelete.value) {
    return;
  }
  deletingKey.value = pendingDelete.value.key;
  try {
    await catalogStore.deleteImageSet(pendingDelete.value.imageIds);
    showDeleteModal.value = false;
    pendingDelete.value = null;
  } catch (e: unknown) {
    catalogStore.error = e as ApiError;
    showDeleteModal.value = false;
  } finally {
    deletingKey.value = null;
  }
}

onMounted(async () => {
  await catalogStore.fetchImages();
  fetchTools();
  await Promise.all([
    ...catalogStore.seriesGroups.map(fetchLatestJob),
    ...catalogStore.seriesGroups.map(fetchLatestPipeline),
  ]);
  pollInterval = setInterval(pollActive, 10_000);
});

onUnmounted(() => {
  if (pollInterval) {
    clearInterval(pollInterval);
  }
});
</script>

<style scoped>
</style>
