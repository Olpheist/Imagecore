<script setup lang="ts">
import type { ViewerTool, ViewportLayout } from '@/models/dicom'

defineProps<{
  activeTool: ViewerTool
  activeLayout: ViewportLayout
}>()

const emit = defineEmits<{
  (e: 'tool-change',   tool:   ViewerTool):     void
  (e: 'layout-change', layout: ViewportLayout): void
  (e: 'reset'):                                  void
  (e: 'invert'):                                 void
}>()

const tools: { icon: string; label: string; value: ViewerTool }[] = [
  { icon: '⇅', label: 'SCROLL', value: 'scroll' },
  { icon: '◑', label: 'W / L',  value: 'wwwc'   },
  { icon: '⊕', label: 'ZOOM',   value: 'zoom'   },
  { icon: '✥', label: 'PAN',    value: 'pan'    },
]

const layouts: { label: ViewportLayout; disabled: boolean }[] = [
  { label: '1x1', disabled: false },
  { label: '1x2', disabled: false },
  { label: '2x2', disabled: false },
]
</script>

<template>
  <div class="h-12 bg-white border-b border-gray-200 flex items-center px-3 gap-1 flex-shrink-0">

    <!-- Viewer tools -->
    <button
      v-for="t in tools"
      :key="t.value"
      :title="t.label"
      class="flex flex-col items-center gap-0.5 px-2.5 py-1.5 rounded-lg border transition-all duration-150"
      :class="activeTool === t.value
        ? 'bg-violet-50 border-violet-200 text-violet-600'
        : 'bg-transparent border-transparent text-gray-400 hover:bg-gray-100 hover:border-gray-200 hover:text-gray-600'"
      @click="emit('tool-change', t.value)"
    >
      <span class="text-base leading-none">{{ t.icon }}</span>
      <span class="font-mono leading-none" style="font-size: 8px; letter-spacing: 0.04em">{{ t.label }}</span>
    </button>

    <div class="w-px h-6 bg-gray-200 mx-2" />

    <!-- Utility tools -->
    <button
      title="Reset"
      class="flex flex-col items-center gap-0.5 px-2.5 py-1.5 rounded-lg border border-transparent text-gray-400 hover:bg-gray-100 hover:border-gray-200 hover:text-gray-600 transition-all duration-150"
      @click="emit('reset')"
    >
      <span class="text-base leading-none">↺</span>
      <span class="font-mono leading-none" style="font-size: 8px">RESET</span>
    </button>

    <button
      title="Invert"
      class="flex flex-col items-center gap-0.5 px-2.5 py-1.5 rounded-lg border border-transparent text-gray-400 hover:bg-gray-100 hover:border-gray-200 hover:text-gray-600 transition-all duration-150"
      @click="emit('invert')"
    >
      <span class="text-base leading-none">◐</span>
      <span class="font-mono leading-none" style="font-size: 8px">INVERT</span>
    </button>

    <div class="flex-1" />

    <!-- Viewport layout -->
    <div class="flex items-center gap-1.5">
      <span class="font-mono text-gray-300 mr-1" style="font-size: 9px">LAYOUT</span>
      <button
        v-for="l in layouts"
        :key="l.label"
        :disabled="l.disabled"
        class="font-mono px-2 py-1 rounded-md border text-xs transition-all"
        :class="activeLayout === l.label && !l.disabled
          ? 'bg-violet-50 border-violet-200 text-violet-600'
          : l.disabled
          ? 'bg-transparent border-gray-100 text-gray-200 cursor-not-allowed'
          : 'bg-transparent border-gray-200 text-gray-400 hover:border-gray-300'"
        @click="!l.disabled && emit('layout-change', l.label)"
      >
        {{ l.label }}
      </button>
    </div>
  </div>
</template>