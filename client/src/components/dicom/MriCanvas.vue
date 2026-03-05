<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import type { DicomSeries, ViewerTool } from '@/models/dicom'

const props = defineProps<{
  study: DicomSeries
  tool: ViewerTool
  /**
   * Optional: bearer token or pre-signed auth header value for AWS HealthImaging.
   * If provided, injected into every WADO-RS request via cornerstoneDicomImageLoader.
   */
  authToken?: string
}>()

const { $cs, $csTools, $csDicomImageLoader } = useNuxtApp()
const cs              = $cs              as typeof import('@cornerstonejs/core')
const csTools         = $csTools         as typeof import('@cornerstonejs/tools')
const dicomImageLoader = $csDicomImageLoader as typeof import('@cornerstonejs/dicom-image-loader')

const viewerRef  = ref<HTMLDivElement | null>(null)
const isLoading  = ref(false)
const loadError  = ref<string | null>(null)

const RENDERING_ENGINE_ID = 'mri-rendering-engine'
const VIEWPORT_ID         = 'mri-viewport'
const TOOL_GROUP_ID       = 'mri-tool-group'

const cursors: Record<ViewerTool, string> = {
  scroll: 'ns-resize',
  wwwc:   'ew-resize',
  zoom:   'zoom-in',
  pan:    'grab',
}

let renderingEngine: any = null
let toolGroup: any       = null

// Image ID helpers
/**
 * Builds an array of Cornerstone imageIds from a DicomSeries.
 *
 * Supports three cases:
 *  1. Single file WADO-URI  →  wadouri:<url>
 *  2. Multi-frame WADO-RS   →  wadors:<baseUrl>/frames/1, /frames/2, …
 *  3. AWS HealthImaging     →  wadors:<awsEndpoint>/…/frames/<n>
 *
 * The series model drives the choice:
 *   - study.imageUrl          = single-frame fallback (wadouri)
 *   - study.wadoRsRoot        = base URL for WADO-RS (wadors)
 *   - study.numberOfFrames    = how many frames to generate (default 1)
 */
function buildImageIds(): string[] {
  const { imageUrl, wadoRsRoot, numberOfFrames = 1 } = props.study

  console.log('[MriCanvas] study props:', { imageUrl, wadoRsRoot })

  if (wadoRsRoot) {
    return Array.from({ length: numberOfFrames }, (_, i) =>
      `wadors:${wadoRsRoot}/frames/${i + 1}`
    )
  }

  if (imageUrl) {
    const id = `wadouri:${imageUrl}`
    console.log('[MriCanvas] imageId:', id)
    return [id]
  }

  return []
}

// Auth header injection (for AWS HealthImaging signed requests)
type LoaderOptions = Parameters<typeof dicomImageLoader.init>[0]

function configureAuth() {
  if (!props.authToken) return
  const token = props.authToken

  dicomImageLoader.init({
    beforeSend: () => ({
      Authorization: `Bearer ${token}`,
    }),
  } satisfies LoaderOptions)
}


// Core load / render
async function loadStudy() {
  if (!viewerRef.value) return

  const imageIds = buildImageIds()
  if (!imageIds.length) {
    loadError.value = 'No imageUrl or wadoRsRoot set on this study'
    return
  }

  isLoading.value = true
  loadError.value = null

  try {
    configureAuth()

    try {
      const existing = cs.getRenderingEngine(RENDERING_ENGINE_ID)
      if (existing) existing.destroy()
    } catch { /* didn't exist */ }

    csTools.ToolGroupManager.destroyToolGroup(TOOL_GROUP_ID)

    renderingEngine = new cs.RenderingEngine(RENDERING_ENGINE_ID)

    // Wait for the element to be fully enabled before proceeding
    renderingEngine!.enableElement({
      viewportId: VIEWPORT_ID,
      type: cs.Enums.ViewportType.STACK,
      element: viewerRef.value!,
    })

    // Poll until the viewport is registered in the engine
    await new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Viewport registration timed out')), 5_000)
      const interval = setInterval(() => {
        try {
          const vp = renderingEngine!.getViewport(VIEWPORT_ID)
          if (vp) {
            clearInterval(interval)
            clearTimeout(timeout)
            resolve()
          }
        } catch {
          // Not ready yet — keep polling
        }
      }, 50)
    })

    const viewport = renderingEngine!.getViewport(VIEWPORT_ID)
    console.log('[MriCanvas] setting stack:', imageIds)

    await Promise.race([
      viewport.setStack(imageIds),
      new Promise((_, reject) =>
        setTimeout(() => reject(new Error('setStack timed out after 10s')), 10_000)
      ),
    ])

    viewport.render()
    setupTools()
    activateTool(props.tool)

  } catch (e: any) {
    loadError.value = `Failed to load image: ${e?.message ?? 'Unknown error'}`
    console.error('[MriCanvas]', e)
  } finally {
    isLoading.value = false
  }
}


function setupTools() {
  // Destroy old tool group via the manager, not the group instance
  csTools.ToolGroupManager.destroyToolGroup(TOOL_GROUP_ID)

  toolGroup = csTools.ToolGroupManager.createToolGroup(TOOL_GROUP_ID)

  const { WindowLevelTool, ZoomTool, PanTool, StackScrollTool } = csTools
  ;[WindowLevelTool, ZoomTool, PanTool, StackScrollTool].forEach(tool => {
    toolGroup.addTool(tool.toolName)
    toolGroup.setToolPassive(tool.toolName)
  })

  // Add viewport after tools are registered
  toolGroup.addViewport(VIEWPORT_ID, RENDERING_ENGINE_ID)
}

function activateTool(tool: ViewerTool) {
  if (!toolGroup) return

  const { WindowLevelTool, ZoomTool, PanTool, StackScrollTool } = csTools
  const toolMap: Record<ViewerTool, string> = {
    scroll: StackScrollTool.toolName,
    wwwc:   WindowLevelTool.toolName,
    zoom:   ZoomTool.toolName,
    pan:    PanTool.toolName,
  }

  const { MouseBindings } = csTools.Enums
  Object.values(toolMap).forEach(name => toolGroup.setToolPassive(name))
  toolGroup.setToolActive(toolMap[tool], {
    bindings: [{ mouseButton: MouseBindings.Primary }],
  })

  if (viewerRef.value) {
    viewerRef.value.style.cursor = cursors[tool]
  }
}

// Watchers & lifecycle
watch(() => props.study.id, loadStudy)
watch(() => props.tool, activateTool)
watch(() => props.authToken, configureAuth)

onMounted(loadStudy)
onUnmounted(() => {
  toolGroup?.destroy()
  toolGroup = null

  if (renderingEngine) {
    try {
      renderingEngine.disableElement(VIEWPORT_ID)
    } catch {
      // May already be gone
    }
    renderingEngine.destroy()
    renderingEngine = null
  }
})
</script>

<template>
  <div class="mri-canvas-wrapper">
    <div
      v-if="isLoading"
      class="mri-overlay"
    >
      Loading…
    </div>

    <div
      v-if="loadError"
      class="mri-overlay mri-error"
    >
      {{ loadError }}
    </div>

    <div
      ref="viewerRef"
      class="mri-viewport"
      :style="{ cursor: cursors[tool] }"
    />
  </div>
</template>

<style scoped>
.mri-canvas-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
}
.mri-viewport {
  width: 100%;
  height: 100%;
  background: #000;
}
.mri-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: rgba(0, 0, 0, 0.6);
  z-index: 10;
  font-size: 0.9rem;
}
.mri-error {
  background: rgba(180, 0, 0, 0.7);
}
</style>