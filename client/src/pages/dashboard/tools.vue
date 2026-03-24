<template>
  <div class="min-h-screen px-6 py-12">
    <!-- Header -->
    <div class="max-w-5xl mx-auto mb-10 flex items-start justify-between">
      <div>
        <h1 class="text-2xl font-semibold">Analysis Tools</h1>
        <p class="text-sm text-gray-500 mt-1">
          Browse and manage available medical imaging tools
        </p>
      </div>
      <Button variant="success" rounded hover @click="showCreateModal = true">
        + New Tool
      </Button>
    </div>

    <!-- Error -->
    <div class="max-w-5xl mx-auto mb-6">
      <Error :error="error" dismissible @close="error = null" />
    </div>

    <!-- Category Filter -->
    <div class="max-w-5xl mx-auto mb-8 flex flex-wrap gap-2">
      <button
        v-for="cat in categories"
        :key="cat"
        :class="[
          'px-4 py-1.5 rounded-full text-sm font-medium border transition-all duration-150',
          selectedCategory === cat
            ? 'bg-gray-900 text-white border-gray-900'
            : 'bg-white text-gray-600 border-gray-200 hover:border-gray-400'
        ]"
        @click="selectedCategory = selectedCategory === cat ? null : cat"
      >
        {{ cat }}
      </button>
    </div>

    <!-- Loading Skeleton -->
    <div v-if="loading" class="max-w-5xl mx-auto grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
      <Card v-for="n in 6" :key="n" variant="elevated" rounded class="animate-pulse">
        <div class="space-y-3 p-1">
          <div class="h-4 bg-gray-100 rounded w-2/3" />
          <div class="h-3 bg-gray-100 rounded w-full" />
          <div class="h-3 bg-gray-100 rounded w-4/5" />
        </div>
      </Card>
    </div>

    <!-- Empty State -->
    <div
      v-else-if="filteredTools.length === 0"
      class="max-w-5xl mx-auto text-center py-20 text-gray-400"
    >
      <p class="text-lg font-medium">No tools available</p>
      <p class="text-sm mt-1">Try selecting a different category or check back later.</p>
    </div>

    <!-- Tool Grid -->
    <div v-else class="max-w-5xl mx-auto grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
      <Card
        v-for="tool in filteredTools"
        :key="tool.toolId"
        :data-testid="`tool-card-${tool.toolId}`"
        variant="elevated"
        rounded
        class="flex flex-col justify-between transition-shadow duration-200 hover:shadow-md"
      >
        <div class="space-y-2">
          <span class="inline-block text-xs font-medium text-gray-400 uppercase tracking-wide">
            {{ tool.category }}
          </span>
          <h2 class="text-base font-semibold text-gray-900 leading-snug">{{ tool.name }}</h2>
          <p class="text-sm text-gray-500 leading-relaxed">{{ tool.description }}</p>
        </div>

        <div v-if="canDelete(tool)" class="flex justify-end pt-4 mt-4 border-t border-gray-100">
          <Button
            :data-testid="`tool-delete-${tool.toolId}`"
            variant="danger"
            :disabled="deletingId === tool.toolId.toString()"
            rounded
            hover
            @click="onDelete(tool)"
          >
            {{ deletingId === tool.toolId.toString() ? 'Deleting...' : 'Delete' }}
          </Button>
        </div>
      </Card>
    </div>

    <!-- ── Create Tool Modal ── -->
    <Modal
      v-model="showCreateModal"
      title="Create New Tool"
      description="Fill in the details below to register a new analysis tool."
      size="md"
    >
      <form id="create-tool-form" class="space-y-4" @submit.prevent="onCreateTool">
        <Error v-if="createError" :error="createError" dismissible @close="createError = null" />

        <div class="space-y-1">
          <label class="block text-sm font-medium text-gray-700">
            Name <span class="text-red-400">*</span>
          </label>
          <input
            v-model="form.name"
            type="text"
            placeholder="e.g. Lung Nodule Detector"
            required
            class="w-full px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-gray-900 focus:border-transparent transition"
          />
        </div>

        <div class="space-y-1">
          <label class="block text-sm font-medium text-gray-700">
            Description <span class="text-red-400">*</span>
          </label>
          <textarea
            v-model="form.description"
            rows="3"
            placeholder="Briefly describe what this tool does"
            required
            class="w-full px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-gray-900 focus:border-transparent transition resize-none"
          />
        </div>

        <div class="space-y-1">
          <label class="block text-sm font-medium text-gray-700">
            Category <span class="text-red-400">*</span>
          </label>
          <input
            v-model="form.category"
            type="text"
            placeholder="e.g. Radiology"
            required
            class="w-full px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-gray-900 focus:border-transparent transition"
          />
        </div>

        <div class="space-y-1">
          <label class="block text-sm font-medium text-gray-700">
            Image URI <span class="text-red-400">*</span>
          </label>
          <input
            v-model="form.imageTag"
            type="text"
            placeholder="e.g. docker.io/myorg/tool:latest or 123456789.dkr.ecr.us-east-1.amazonaws.com/tool:latest"
            required
            class="w-full px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-gray-900 focus:border-transparent transition font-mono text-xs"
          />
        </div>
      </form>

      <template #footer>
        <Button variant="secondary" rounded hover @click="showCreateModal = false">Cancel</Button>
        <Button type="submit" form="create-tool-form" variant="success" rounded hover :disabled="creating">
          {{ creating ? 'Creating...' : 'Create Tool' }}
        </Button>
      </template>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from "vue";
import { useApiFetch } from "~/composables/useApiFetch";
import type { ApiError } from "~/models/error";
import type { ToolDto } from "~/models/tool";
import { useUserStore } from "~/stores/user";
import { capitalizeFirstLetter } from "~/utils/stringFunctions";

useHead({
  title: "Analysis Tools",
});

const userStore = useUserStore();
const canDelete = (tool: ToolDto): boolean => {
  const isAdmin = userStore.hasRole("ADMIN");
  const isOwner = tool.CreatedByUserId === userStore.user?.id;
  return isAdmin || isOwner;
};

const tools            = ref<ToolDto[]>([]);
const loading          = ref(false);
const error            = ref<ApiError | null>(null);
const selectedCategory = ref<string | null>(null);

// Create modal
const showCreateModal = ref(false);
const creating        = ref(false);
const createError     = ref<ApiError | null>(null);

const emptyForm = () => ({ name: "", description: "", category: "", imageTag: "" });
const form = ref(emptyForm());

// Reset form whenever the modal is closed
watch(showCreateModal, (open) => {
  if (!open) {
    createError.value = null;
    form.value = emptyForm();
  }
});

const onCreateTool = async (): Promise<void> => {
  createError.value = null;
  creating.value = true;
  try {
    const created = await useApiFetch<ToolDto>("/tools", {
      method: "POST",
      body: form.value,
    });
    tools.value.unshift(created);
    showCreateModal.value = false;
  } catch (e: unknown) {
    createError.value = e as ApiError;
  } finally {
    creating.value = false;
  }
};

// Delete 
const deletingId = ref<string | null>(null);

const onDelete = async (tool: ToolDto): Promise<void> => {
  error.value = null;
  deletingId.value = tool.toolId.toString();
  try {
    await useApiFetch(`/tools/${tool.toolId}`, { method: "DELETE" });
    tools.value = tools.value.filter((t) => t.toolId !== tool.toolId);
  } catch (e: unknown) {
    error.value = e as ApiError;
    if (error.value?.status === 403) {
        alert("You don't have permission to delete this tool.");
    }
  } finally {
    deletingId.value = null;
  }
};

// Tools list, category is stored lowercase
const categories = computed(() =>
  [...new Set(tools.value.map((t) => capitalizeFirstLetter(t.category)))].sort()
);

const filteredTools = computed(() => {
  if (!selectedCategory.value) return tools.value;
  // Filter tools by category, ensuring case-insensitive comparison
  return tools.value.filter((t) => capitalizeFirstLetter(t.category) === selectedCategory.value);
});

const fetchTools = async (): Promise<void> => {
  error.value = null;
  loading.value = true;
  try {
    tools.value = await useApiFetch<ToolDto[]>("/tools");
  } catch (e: unknown) {
    error.value = e as ApiError;
  } finally {
    loading.value = false;
  }
};

onMounted(fetchTools);
</script>