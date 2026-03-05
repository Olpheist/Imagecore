<template>
  <div class="flex items-center justify-between px-1 py-3">

    <!-- Left: entry count -->
    <p class="text-xs text-slate-400">
      Showing
      <span class="font-medium text-slate-600">{{ rangeStart }}–{{ rangeEnd }}</span>
      of
      <span class="font-medium text-slate-600">{{ totalNum }}</span>
      results
    </p>

    <!-- Right: page controls -->
    <div class="flex items-center gap-1">

      <!-- Prev -->
      <button
          @click="prev"
          :disabled="currentPage <= 1"
          style="cursor: pointer"
          class="inline-flex items-center justify-center h-8 w-8 rounded-lg border border-slate-200 bg-white text-slate-500
               hover:bg-slate-50 hover:text-slate-700 transition-colors duration-100
               disabled:opacity-30 disabled:cursor-not-allowed disabled:hover:bg-white"
          aria-label="Previous page"
      >
        <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M15 19l-7-7 7-7" />
        </svg>
      </button>

      <!-- Page numbers -->
      <template v-for="page in visiblePages" :key="page">
        <span
            v-if="page === '...'"
            class="inline-flex items-center justify-center h-8 w-8 text-xs text-slate-400 select-none"
        >
          ···
        </span>
        <button
            v-else
            @click="goTo(page as number)"
            style="cursor: pointer"
            :class="[
            'inline-flex items-center justify-center h-8 w-8 rounded-lg border text-xs font-medium transition-colors duration-100',
            currentPage === page
              ? 'border-slate-800 bg-slate-800 text-white'
              : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50 hover:text-slate-900'
          ]"
        >
          {{ page }}
        </button>
      </template>

      <!-- Next -->
      <button
          @click="next"
          :disabled="currentPage >= totalPages"
          style="cursor: pointer"
          class="inline-flex items-center justify-center h-8 w-8 rounded-lg border border-slate-200 bg-white text-slate-500
               hover:bg-slate-50 hover:text-slate-700 transition-colors duration-100
               disabled:opacity-30 disabled:cursor-not-allowed disabled:hover:bg-white"
          aria-label="Next page"
      >
        <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" />
        </svg>
      </button>

    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";

const props = withDefaults(
    defineProps<{
      totalNum: number;
      perPage?: number;
    }>(),
    { perPage: 10 }
);

const currentPage = defineModel<number>("currentPage", { default: 1 });

const totalPages = computed(() => Math.max(1, Math.ceil(props.totalNum / props.perPage)));

const rangeStart = computed(() => Math.min((currentPage.value - 1) * props.perPage + 1, props.totalNum));
const rangeEnd   = computed(() => Math.min(currentPage.value * props.perPage, props.totalNum));

const visiblePages = computed<(number | "...")[]>(() => {
  const total = totalPages.value;
  const cur   = currentPage.value;

  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);

  const pages: (number | "...")[] = [1];
  if (cur > 3) pages.push("...");

  const start = Math.max(2, cur - 1);
  const end   = Math.min(total - 1, cur + 1);
  for (let i = start; i <= end; i++) pages.push(i);

  if (cur < total - 2) pages.push("...");
  pages.push(total);

  return pages;
});

function goTo(page: number) {
  currentPage.value = Math.max(1, Math.min(page, totalPages.value));
}
function prev() { goTo(currentPage.value - 1); }
function next() { goTo(currentPage.value + 1); }
</script>