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
              'bg-green-50 text-green-700 border-green-200':  value === 'COMPLETED',
              'bg-yellow-50 text-yellow-700 border-yellow-200': value === 'IN_PROGRESS' || value === 'SUBMITTED',
              'bg-red-50 text-red-700 border-red-200':        value === 'FAILED',
              'bg-gray-50 text-gray-500 border-gray-200':     value === 'PENDING',
            }"
          >
            {{ value }}
          </span>
        </template>
        <template #cell-actions="{ row }">
          <div class="flex gap-1.5 flex-nowrap">
            <Button
              variant="secondary"
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
              disabled
              title="Coming soon"
            >
              Send to Analysis
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
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useDicomCatalogStore } from '~/stores/dicomCatalog';
import type { DicomImageSetGroup } from '~/models/dicom';
import type { Column } from '~/components/Table.vue';
import { formatDate } from '~/utils/formatters';

const catalogStore = useDicomCatalogStore();
const router = useRouter();

const columns: Column[] = [
  { key: 'displayName',   label: 'Series / Study', class: 'w-[26%]'  },
  { key: 'modality',      label: 'Modality',       class: 'w-[8%]'   },
  { key: 'bodyPart',      label: 'Body Part',      class: 'w-[10%]'  },
  { key: 'studyDate',     label: 'Study Date',     class: 'w-[10%]'  },
  { key: 'instanceCount', label: 'Instances',      class: 'w-[8%]'   },
  { key: 'status',        label: 'Status',         class: 'w-[10%]'  },
  { key: 'actions',       label: 'Actions',        class: 'w-[28%]'  },
];

const showDeleteModal = ref(false);
const pendingDelete   = ref<DicomImageSetGroup | null>(null);
const deletingKey     = ref<string | null>(null);

function onDeleteClick(group: DicomImageSetGroup) {
  pendingDelete.value   = group;
  showDeleteModal.value = true;
}

function viewInViewer(group: DicomImageSetGroup) {
  const query = group.seriesInstanceUid
    ? { seriesUid: group.seriesInstanceUid }
    : {};
  router.push({ path: '/dashboard/dicom', query });
}

async function confirmDelete() {
  if (!pendingDelete.value) return;
  deletingKey.value = pendingDelete.value.key;
  try {
    await catalogStore.deleteImageSet(pendingDelete.value.imageIds);
    showDeleteModal.value = false;
    pendingDelete.value   = null;
  } finally {
    deletingKey.value = null;
  }
}

onMounted(() => catalogStore.fetchImages());
</script>
