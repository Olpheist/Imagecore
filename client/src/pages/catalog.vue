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
              variant="danger"
              rounded
              :disabled="deletingId === row.id"
              @click="onDeleteClick(row)"
            >
              {{ deletingId === row.id ? 'Deleting…' : 'Delete' }}
            </Button>
            <Button
              variant="secondary"
              rounded
              disabled
              title="Coming soon"
            >
              Send to Analysis
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

<script setup lang="ts">;
import { ref, onMounted } from 'vue';
import { useDicomCatalogStore } from '~/stores/dicomCatalog';
import type { DicomImageDto } from '~/models/dicom';
import type { Column } from '~/components/Table.vue';
import { formatBytes, formatDate } from '~/utils/formatters';

const catalogStore = useDicomCatalogStore();

const columns: Column[] = [
  { key: 'filename',   label: 'Filename'    },
  { key: 'fileSize',   label: 'Size'        },
  { key: 'uploadedAt', label: 'Upload Date' },
  { key: 'actions',    label: 'Actions'     },
];

const showDeleteModal = ref(false);
const pendingDelete   = ref<DicomImageDto | null>(null);
const deletingId      = ref<number | null>(null);

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

onMounted(() => catalogStore.fetchImages());
</script>
