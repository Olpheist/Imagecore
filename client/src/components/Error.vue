<template>
  <div
      v-if="error"
      class="rounded-2xl border border-red-300 bg-red-50 p-4 text-sm text-red-900 shadow-sm"
  >
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div class="font-semibold">
        <span>{{ title }}</span>
        <span v-if="error.status" class="ml-1 text-red-700">
          ({{ error.status }})
        </span>
      </div>

      <button
          v-if="dismissible"
          @click="$emit('close')"
          class="ml-4 text-red-600 hover:text-red-800 cursor-pointer"
      >
        ✕
      </button>
    </div>

    <!-- Main message -->
    <div class="mt-2">
      {{ error.message }}
    </div>

    <!-- Validation / detail list -->
    <ul
        v-if="error.details && error.details.length"
        class="mt-3 list-disc pl-5 text-xs text-red-800"
    >
      <li v-for="(detail, index) in error.details" :key="index">
        {{ detail }}
      </li>
    </ul>

    <!-- Optional path (debug mode) -->
    <div
        v-if="showPath && error.path"
        class="mt-3 text-xs text-red-700 opacity-70"
    >
      {{ error.path }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { ApiError } from "~/models/error";

const props = withDefaults(
    defineProps<{
      error: ApiError | null;
      dismissible?: boolean;
      showPath?: boolean;
    }>(),
    {
      dismissible: false,
      showPath: false,
    }
);

defineEmits<{
  (e: "close"): void;
}>();

const title = computed(() => {
  if (!props.error) return "Error";
  return props.error.error ?? "Error";
});
</script>