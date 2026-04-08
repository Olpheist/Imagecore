<template>
  <div class="min-h-screen px-6 py-12">
    <!-- Header -->
    <div class="max-w-5xl mx-auto mb-10 flex items-start justify-between">
      <div>
        <h1 class="text-2xl font-semibold">My DICOM Images</h1>
        <p class="text-sm text-gray-500 mt-1">
          Manage your uploaded medical images
        </p>
      </div>
    </div>

    <!-- Error -->
    <div class="max-w-5xl mx-auto mb-6">
      <Error :error="catalogStore.error" dismissible @close="catalogStore.error = null" />
    </div>

    <!-- Loading Skeleton -->
    <div v-if="catalogStore.loading" class="max-w-5xl mx-auto">
      <Card variant="elevated" rounded class="animate-pulse">
        <div class="space-y-3 p-2">
          <div v-for="n in 4" :key="n" class="h-4 bg-gray-100 rounded w-full" />
        </div>
      </Card>
    </div>

    <!-- Table -->
    <div v-else class="max-w-5xl mx-auto">
      <Table
        :columns="columns"
        :rows="catalogStore.images"
        row-key="id"
      >
        <template #cell-fileSize="{ value }">
          {{ formatBytes(value) }}
        </template>
        <template #cell-uploadedAt="{ value }">
          {{ formatDate(value) }}
        </template>
        <template #cell-actions="{ row }">
          <div class="flex gap-2">
            <Button
              variant="primary"
              rounded
              :disabled="!row.imageSetId"
              :title="!row.imageSetId ? 'Image not yet imported into HealthImaging' : 'Open in DICOM viewer'"
              @click="onViewImageClick(row)"
            >
              View Image
            </Button>

            <!-- Run Tool dropdown -->
            <DropdownMenu v-if="row.imageSetId" align="left" widthClass="w-64">
              <template #trigger>
                <Button
                  variant="secondary"
                  rounded
                  title="Run an analysis tool"
                >
                  Run Tool
                </Button>
              </template>
              <template #menu="{ close }">
                <p v-if="toolsLoading" class="px-3 py-2 text-sm text-gray-400">Loading…</p>
                <p v-else-if="toolsByCategory.size === 0" class="px-3 py-2 text-sm text-gray-400">No tools available</p>
                <template v-else v-for="[category, categoryTools] in toolsByCategory" :key="category">
                  <!-- Category row -->
                  <button
                    class="w-full text-left px-3 py-2 text-xs font-semibold text-gray-500 uppercase tracking-wide hover:bg-gray-100 rounded flex items-center justify-between cursor-pointer"
                    @click="expandedCategory = expandedCategory === category ? null : category"
                  >
                    {{ category }}
                    <svg
                      class="w-3 h-3 transition-transform duration-150"
                      :class="{ 'rotate-180': expandedCategory === category }"
                      fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"
                    >
                      <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
                    </svg>
                  </button>
                  <!-- Tool rows -->
                  <template v-if="expandedCategory === category">
                    <button
                      v-for="tool in categoryTools"
                      :key="tool.toolId"
                      class="w-full text-left pl-6 pr-3 py-2 text-sm text-gray-700 hover:bg-gray-100 rounded cursor-pointer"
                      @click="onRunToolSelect(row, tool, close)"
                    >
                      {{ tool.name }}
                    </button>
                  </template>
                </template>
              </template>
            </DropdownMenu>
            <Button
              v-else
              variant="secondary"
              rounded
              disabled
              title="Image not yet imported into HealthImaging"
            >
              Run Tool
            </Button>

            <Button
              variant="success"
              rounded
              :disabled="!row.imageSetId"
              :title="!row.imageSetId ? 'Image not yet imported into HealthImaging' : 'Start an analysis tool workflow'"
              @click="onRunToolWorkflowClick(row)"
            >
              Run Tool Workflow
            </Button>
            <Button
              variant="danger"
              rounded
              :disabled="deletingId === row.id"
              title="Delete image set"
              @click="onDeleteClick(row)"
            >
              {{ deletingId === row.id ? 'Deleting…' : 'Delete' }}
            </Button>
          </div>
        </template>
      </Table>
    </div>

    <!-- Delete Confirmation Modal -->
    <Modal
      v-model="showDeleteModal"
      title="Delete Image"
      description="This action cannot be undone."
      size="sm"
    >
      <p class="text-sm text-gray-700">
        Are you sure you want to delete
        <span class="font-medium">{{ pendingDelete?.filename }}</span>?
      </p>

      <template #footer>
        <Button variant="secondary" rounded hover @click="showDeleteModal = false">Cancel</Button>
        <Button variant="danger" rounded hover :disabled="deletingId !== null" @click="confirmDelete">
          {{ deletingId !== null ? 'Deleting…' : 'Delete' }}
        </Button>
      </template>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { navigateTo } from 'nuxt/app';
import { useDicomCatalogStore } from '~/stores/dicomCatalog';
import { useApiFetch } from '~/composables/useApiFetch';
import type { DicomImageDto } from '~/models/dicom';
import type { ToolDto } from '~/models/tool';
import type { Column } from '~/components/Table.vue';
import { formatBytes, formatDate } from '~/utils/formatters';
import { capitalizeFirstLetter } from '~/utils/stringFunctions';
import DropdownMenu from '~/components/DropdownMenu.vue';

const catalogStore = useDicomCatalogStore();

const columns: Column[] = [
  { key: 'filename',   label: 'Filename'    },
  { key: 'fileSize',   label: 'Size'        },
  { key: 'uploadedAt', label: 'Upload Date' },
  { key: 'actions',    label: 'Actions'     },
];

// Tools for Run Tool dropdown
const tools        = ref<ToolDto[]>([]);
const toolsLoading = ref(false);
const expandedCategory = ref<string | null>(null);

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

const showDeleteModal = ref(false);
const pendingDelete   = ref<DicomImageDto | null>(null);
const deletingId      = ref<number | null>(null);

function onViewImageClick(image: DicomImageDto) {
  navigateTo({ path: '/dashboard/dicom', query: { imageSetId: image.imageSetId! } });
}

function onRunToolSelect(image: DicomImageDto, tool: ToolDto, close: () => void) {
  close();
  // TODO: submit single analysis job for image using tool
}

function onRunToolWorkflowClick(image: DicomImageDto) {
  // TODO: open custom analysis modal or tool workflow page, not sure which yet
}

function onDeleteClick(image: DicomImageDto) {
  pendingDelete.value   = image;
  showDeleteModal.value = true;
}

async function confirmDelete() {
  if (!pendingDelete.value) return;
  deletingId.value = pendingDelete.value.id;
  try {
    await catalogStore.deleteImage(pendingDelete.value.id);
    showDeleteModal.value = false;
    pendingDelete.value   = null;
  } finally {
    deletingId.value = null;
  }
}

onMounted(() => {
  catalogStore.fetchImages();
  fetchTools();
});
</script>
