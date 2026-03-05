<script setup lang="ts">
import type { DicomSeries } from '@/models/dicom'

defineProps<{
  study: DicomSeries
  active: boolean
}>()

defineEmits<{
  (e: 'select'): void
}>()

const fmtDate = (iso: string) =>
  new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
</script>

<template>
  <div
    class="rounded-xl p-3 cursor-pointer mb-1 border transition-all duration-150"
    :class="active
      ? 'bg-violet-50 border-violet-200'
      : 'bg-transparent border-transparent hover:bg-gray-50 hover:border-gray-200'"
    @click="$emit('select')"
  >
    <!-- Row 1: description + date -->
    <div class="flex items-center justify-between mb-1.5">
      <span
        class="font-semibold text-sm tracking-tight"
        :class="active ? 'text-violet-900' : 'text-gray-900'"
      >
        {{ study.description }}
      </span>
      <span class="font-mono text-gray-400" style="font-size: 10px">
        {{ fmtDate(study.studyDate) }}
      </span>
    </div>

    <!-- Row 2: series description -->
    <div class="text-xs text-gray-500 mb-2 leading-snug">
      {{ study.seriesDescription }}
    </div>

    <!-- Row 3: series UID tail + instance count -->
    <div class="flex items-center justify-between">
      <span class="font-mono text-gray-300 truncate max-w-[180px]" style="font-size: 10px">
        ···{{ study.seriesUid.slice(-18) }}
      </span>
      <span
        class="font-mono rounded-full px-2 py-0.5 border"
        :class="active
          ? 'text-violet-600 bg-violet-100 border-violet-200'
          : 'text-gray-400 bg-gray-50 border-gray-200'"
        style="font-size: 10px"
      >
        {{ study.instanceCount }} inst.
      </span>
    </div>
  </div>
</template>