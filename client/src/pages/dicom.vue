<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useDicomStore } from '@/stores/dicom'
import MriCanvas         from '@/components/dicom/MriCanvas.vue'
import StudyRow          from '@/components/dicom/StudyRow.vue'
import ViewerToolbar     from '@/components/dicom/ViewerToolbar.vue'
import StudyMetaPanel    from '@/components/dicom/StudyMetaPanel.vue'
import type { DicomSeries, ViewerTool, ViewportLayout } from '@/models/dicom'

// useDicomViewer lives inside MriCanvas.vue where the canvas ref lives.
// This page only owns selection state and passes it down as props.
const dicomStore   = useDicomStore()
const selectedId   = ref<string | null>(null)
const metaOpen     = ref(false)
const search       = ref('')
const activeTool   = ref<ViewerTool>('scroll')
const activeLayout = ref<ViewportLayout>('1x1')

const selected = computed<DicomSeries | null>(
  () => dicomStore.series.find(s => s.id === selectedId.value) ?? null
)

const filtered = computed(() =>
  dicomStore.series.filter(s =>
    [s.description, s.seriesDescription, s.bodyPart, s.patientId]
      .some(v => v.toLowerCase().includes(search.value.toLowerCase()))
  )
)

const totalInstances = computed(() =>
  dicomStore.series.reduce((acc, s) => acc + s.instanceCount, 0)
)

onMounted(async () => {
  await dicomStore.fetchSeries()
  if (dicomStore.series.length > 0) {
    selectedId.value = dicomStore.series[0].id
  }
})

async function selectStudy(study: DicomSeries): Promise<void> {
  selectedId.value = study.id
  // Sprint 2: fetch presigned URL — MriCanvas.vue will react via watch on study prop
  // await dicomStore.fetchViewUrl(study.id)
}

function onToolChange(tool: ViewerTool): void {
  activeTool.value = tool
}

function onLayoutChange(layout: ViewportLayout): void {
  activeLayout.value = layout
}

function onReset(): void {
  // Sprint 2: reset Cornerstone viewport via useDicomViewer
}
</script>

<template>
  <div class="flex flex-col h-screen bg-gray-50 overflow-hidden text-gray-900">

    <!-- Header -->
    <header class="bg-white border-b border-gray-200 flex items-center px-5 gap-4 flex-shrink-0 shadow-sm" style="height: 52px">

      <!-- Brand -->
      <div class="flex items-center gap-2.5">
        <div
          class="w-7 h-7 rounded-lg bg-violet-600 flex items-center justify-center text-white font-bold text-sm flex-shrink-0"
        >⬡</div>
        <div>
          <div class="text-sm font-semibold leading-tight tracking-tight">
            ImageCore
          </div>
          <div class="text-gray-400 leading-tight" style="font-size: 9px; letter-spacing: 0.08em">
            MRI VIEWER
          </div>
        </div>
      </div>

      <div class="w-px h-6 bg-gray-200 mx-1" />

      <!-- Active study breadcrumb -->
      <div
        v-if="selected"
        class="flex items-center gap-2 px-3 py-1.5 bg-gray-50 border border-gray-200 rounded-full"
      >
        <span class="w-1.5 h-1.5 rounded-full bg-violet-500 flex-shrink-0" />
        <span class="text-xs font-medium text-gray-700">{{ selected.description }}</span>
        <span class="text-gray-300">·</span>
        <span class="font-mono text-gray-400" style="font-size: 10px">
          {{ selected.instanceCount }} inst.
        </span>
      </div>

      <div class="flex-1" />

      <!-- Dataset badge -->
      <div class="flex items-center gap-1.5 px-3 py-1 bg-violet-50 border border-violet-200 rounded-full">
        <span class="font-mono font-semibold text-violet-600" style="font-size: 10px">MR</span>
        <span class="text-violet-300">·</span>
        <span class="font-mono text-violet-400" style="font-size: 9px">LOCAL DATASET</span>
      </div>

      <!-- Study info toggle -->
      <button
        class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border text-xs transition-all duration-150"
        :class="metaOpen
          ? 'bg-violet-50 border-violet-200 text-violet-600'
          : 'bg-white border-gray-200 text-gray-500 hover:bg-gray-50'"
        @click="metaOpen = !metaOpen"
      >
        <span>ⓘ</span>
        <span>Study Info</span>
      </button>
    </header>

    <!-- Body -->
    <div class="flex flex-1 overflow-hidden">

      <!-- Study Sidebar -->
      <aside class="w-80 flex-shrink-0 bg-white border-r border-gray-200 flex flex-col overflow-hidden">

        <!-- Sidebar header -->
        <div class="px-3 pt-4 pb-3 border-b border-gray-100 flex-shrink-0">
          <div class="flex items-center justify-between mb-3">
            <span class="text-xs font-semibold text-gray-400 uppercase tracking-widest">
              MRI Studies
            </span>
            <span class="font-mono text-xs text-gray-400 bg-gray-50 border border-gray-200 rounded-full px-2 py-0.5">
              {{ filtered.length }} / {{ dicomStore.series.length }}
            </span>
          </div>

          <!-- Search -->
          <div class="relative">
            <span class="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-300 text-sm pointer-events-none">⌕</span>
            <input
              v-model="search"
              placeholder="Body part, series…"
              class="w-full bg-gray-50 border border-gray-200 rounded-lg py-2 pl-7 pr-3 text-xs text-gray-700 placeholder-gray-300 transition-all focus:outline-none focus:border-violet-400 focus:ring-1 focus:ring-violet-100"
            />
          </div>
        </div>

        <!-- Loading / error states -->
        <div v-if="dicomStore.loading" class="flex-1 flex items-center justify-center">
          <span class="font-mono text-gray-300 text-xs animate-pulse">Loading studies…</span>
        </div>

        <div v-else-if="dicomStore.error" class="flex-1 flex items-center justify-center px-4 text-center">
          <span class="font-mono text-red-400 text-xs">{{ dicomStore.error }}</span>
        </div>

        <!-- Study rows -->
        <div v-else class="flex-1 overflow-y-auto p-2">
          <p v-if="filtered.length === 0" class="py-10 text-center font-mono text-gray-300 text-xs">
            No results
          </p>
          <StudyRow
            v-for="study in filtered"
            :key="study.id"
            :study="study"
            :active="study.id === selectedId"
            @select="selectStudy(study)"
          />
        </div>

        <!-- Footer -->
        <div class="px-3 py-2.5 border-t border-gray-100 flex justify-between">
          <span class="font-mono text-gray-300" style="font-size: 9px">
            {{ totalInstances.toLocaleString() }} total inst.
          </span>
        </div>
      </aside>

      <!-- Viewer -->
      <main class="flex-1 flex flex-col overflow-hidden min-w-0">
        <ViewerToolbar
          :active-tool="activeTool"
          :active-layout="activeLayout"
          @tool-change="onToolChange"
          @layout-change="onLayoutChange"
          @reset="onReset"
        />

        <div class="flex-1 overflow-hidden">
          <ClientOnly>
            <MriCanvas
              v-if="selected"
              :study="selected"
              :tool="activeTool"
            />
            <div
              v-else
              class="h-full bg-zinc-950 flex flex-col items-center justify-center gap-3 text-zinc-700"
            >
              <span class="text-4xl">⬡</span>
              <span class="font-mono text-xs tracking-widest">SELECT A STUDY</span>
            </div>
          </ClientOnly>
        </div>
      </main>

      <!-- Metadata panel -->
      <StudyMetaPanel
        :study="selected"
        :open="metaOpen"
      />

    </div>
  </div>
</template>