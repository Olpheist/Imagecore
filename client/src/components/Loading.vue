<template>
  <div class="flex justify-center py-12">
    <div class="flex items-center gap-3 rounded-2xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
      <div class="relative h-5 w-5">
        <div class="absolute inset-0 rounded-full border-2 border-slate-200"></div>
        <div class="absolute inset-0 animate-spin rounded-full border-2 border-transparent border-t-slate-600"></div>
      </div>
      <div class="flex flex-col">
        <span class="text-sm font-semibold text-slate-700">
          {{ label }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(
    defineProps<{
      label?: string;
    }>(),
    {
      label: "Loading...",
    }
);
</script>