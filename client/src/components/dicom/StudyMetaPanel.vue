<script setup lang="ts">
import { computed } from 'vue'
import type { DicomSeries } from '@/models/dicom'

const props = defineProps<{
  study: DicomSeries | null
  open: boolean
}>()

const fmtDate = (iso: string) =>
  new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })

// Moved out of template to avoid backtick/quoting issues inside v-for bindings
const metaRows = computed(() => {
  if (!props.study) return []
  return [
    { label: 'Modality',   value: 'MRI (MR)',                              mono: false },
    { label: 'Study',      value: props.study.description,                 mono: false },
    { label: 'Series',     value: props.study.seriesDescription,           mono: false },
    { label: 'Body Part',  value: props.study.bodyPart,                    mono: false },
    { label: 'Instances',  value: `${props.study.instanceCount} slice(s)`,   mono: false },
    { label: 'Date',       value: fmtDate(props.study.studyDate),          mono: false },
    { label: 'Clinician',  value: props.study.physician,                   mono: false },
    { label: 'Patient ID', value: props.study.patientId,                   mono: true  },
    { label: 'Study UID',  value: props.study.studyUid,                    mono: true  },
    { label: 'Series UID', value: props.study.seriesUid,                   mono: true  },
  ]
})

</script>

<template>
  <aside
    class="flex-shrink-0 bg-white border-l border-gray-200 overflow-hidden transition-all duration-200"
    :style="{ width: open ? '280px' : '0px' }"
  >
    <div class="h-full overflow-y-auto px-4 py-5" style="width: 280px">
      <h3 class="text-xs font-semibold text-gray-400 uppercase tracking-widest mb-4">
        Study Information
      </h3>

      <template v-if="study">
        <!-- Metadata rows -->
        <div
          v-for="row in metaRows"
          :key="row.label"
          class="grid gap-2 py-2.5 border-b border-gray-100"
          style="grid-template-columns: 80px 1fr"
        >
          <span class="font-mono text-gray-400 pt-0.5 uppercase tracking-wide" style="font-size: 9px">
            {{ row.label }}
          </span>
          <span
            class="text-gray-800 leading-snug break-all"
            :class="row.mono ? 'font-mono' : 'text-xs'"
            :style="{ fontSize: row.mono ? '10px' : '12px' }"
          >
            {{ row.value }}
          </span>
        </div>

      </template>
    </div>
  </aside>
</template>