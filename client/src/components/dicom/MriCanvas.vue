<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick, computed } from 'vue'
import type { DicomSeries, ViewerTool, ViewportLayout } from '@/models/dicom'

const props = defineProps<{
  study: DicomSeries
  tool: ViewerTool
  layout: ViewportLayout
}>()

const { $cs, $csTools, $csDicomImageLoader } = useNuxtApp()
const cs              = $cs              as typeof import('@cornerstonejs/core')
const csTools         = $csTools         as typeof import('@cornerstonejs/tools')
const dicomImageLoader = $csDicomImageLoader as typeof import('@cornerstonejs/dicom-image-loader')

const viewerRef   = ref<HTMLDivElement | null>(null)
const vpRef0      = ref<HTMLDivElement | null>(null)
const vpRef1      = ref<HTMLDivElement | null>(null)
const vpRef2      = ref<HTMLDivElement | null>(null)
const vpRef3      = ref<HTMLDivElement | null>(null)
const isLoading   = ref(false)
const loadError   = ref<string | null>(null)

const RENDERING_ENGINE_ID = 'mri-rendering-engine'
const TOOL_GROUP_ID       = 'mri-tool-group'
const VOLUME_ID           = 'cornerstoneStreamingImageVolume:mri-volume'

const cursors: Record<ViewerTool, string> = {
  scroll: 'ns-resize',
  wwwc:   'ew-resize',
  zoom:   'zoom-in',
  pan:    'grab',
}

let renderingEngine: any = null
let toolGroup: any       = null
let loadGeneration       = 0  // incremented on each loadStudy; stale loads self-abort
let engineLayout: ViewportLayout | null = null  // layout the current engine was built for

// Layout configuration
interface ViewportSlot {
  id: string
  orientation: string // OrientationAxis value
  label: string
}

const layoutConfigs: Record<ViewportLayout, ViewportSlot[]> = {
  '1x1': [
    { id: 'vp-axial', orientation: 'axial', label: 'Axial' },
  ],
  '1x2': [
    { id: 'vp-axial',    orientation: 'axial',    label: 'Axial' },
    { id: 'vp-sagittal', orientation: 'sagittal', label: 'Sagittal' },
  ],
  '2x2': [
    { id: 'vp-axial',    orientation: 'axial',    label: 'Axial' },
    { id: 'vp-sagittal', orientation: 'sagittal', label: 'Sagittal' },
    { id: 'vp-coronal',  orientation: 'coronal',  label: 'Coronal' },
  ],
}

const activeSlots = computed(() => layoutConfigs[props.layout])

const gridClass = computed(() => {
  if (props.layout === '1x1') return 'grid-cols-1 grid-rows-1'
  if (props.layout === '1x2') return 'grid-cols-2 grid-rows-1'
  return 'grid-cols-2 grid-rows-2'
})

function getVpRef(index: number): HTMLDivElement | null {
  return [vpRef0.value, vpRef1.value, vpRef2.value, vpRef3.value][index] ?? null
}

// Image ID helpers
/**
 * Builds an array of Cornerstone imageIds from a DicomSeries.
 *
 * Supports two cases:
 *  1. DICOMweb (WADO-RS)  →  wadors:<root>/studies/{uid}/series/{uid}/instances/{uid}/frames/N
 *  2. Single file fallback →  wadouri:<url>
 *
 * When wadoRsRoot is set, generates standard DICOMweb frame URLs that the backend
 * DICOMweb proxy translates into AWS HealthImaging GetImageFrame calls.
 */
function buildImageIds(): string[] {
  const { imageUrl, localFiles, wadoRsRoot, numberOfFrames = 1,
          studyUid, seriesUid, sopInstanceUid, sopInstanceUids, imageSetId } = props.study

  // 1. Local .dcm files for dev testing (wadouri per file)
  // if (localFiles?.length) {
  //   return localFiles.map(path => `wadouri:${path}`)
  // }

  // 2. DICOMweb (WADO-RS) via AWS HealthImaging proxy.
  // studyUid / seriesUid are used only as URL path segments — the backend frame endpoint
  // resolves images by sopUID alone, so we fall back to imageSetId when the real UIDs
  // weren't populated from HealthImaging metadata.
  const effectiveStudyUid  = studyUid  || imageSetId || null
  const effectiveSeriesUid = seriesUid || imageSetId || null
  // console.log('Building image IDs with', { wadoRsRoot, studyUid: effectiveStudyUid, seriesUid: effectiveSeriesUid, sopInstanceUid, sopInstanceUids, numberOfFrames })
  if (wadoRsRoot && effectiveStudyUid && effectiveSeriesUid) {
    const instancesBase = `${wadoRsRoot}/studies/${effectiveStudyUid}/series/${effectiveSeriesUid}/instances`

    // Multi-instance series: each slice is a separate SOP instance (one upload per file).
    // Build one image ID per instance — the controller resolves the correct HealthImaging
    // frame ID for each SOP via the frame_ids column.
    if (sopInstanceUids && sopInstanceUids.length > 1) {
      return sopInstanceUids.map(sop => `wadors:${instancesBase}/${sop}/frames/1`)
    }

    // True multi-frame single-SOP (e.g. ultrasound cine): one instance with N frames.
    const sop = sopInstanceUids?.[0] ?? sopInstanceUid
    if (sop) {
      return Array.from({ length: numberOfFrames }, (_, i) =>
        `wadors:${instancesBase}/${sop}/frames/${i + 1}`
      )
    }
  }

  // Single file fallback
  if (imageUrl) return [`wadouri:${imageUrl}`]
  return [`wadouri:/dicom-samples/mri-001/sample.dcm`]
}

// Auth: the app uses cookie-based sessions. Set withCredentials so the browser
// includes the session cookie on WADO-RS XHR requests to the backend proxy.
type LoaderOptions = Parameters<typeof dicomImageLoader.init>[0]

function configureAuth() {
  dicomImageLoader.init({
    beforeSend: (xhr: XMLHttpRequest) => {
      xhr.withCredentials = true
    },
  } as LoaderOptions)
}

/**
 * Pre-fetches and registers DICOM instance metadata in Cornerstone's WADO-RS metadata
 * cache before images are loaded. The wadors: image loader does NOT automatically call
 * the DICOMweb /metadata endpoint — metadata must be registered first via
 * dicomImageLoader.wadors.metaDataManager.add() or imagePixelModule will be undefined.
 *
 * Fast path (HealthImaging): calls the bulk /imagesets/{id}/instances/metadata endpoint
 * once to get all SOP metadata in a single request, instead of N per-instance calls.
 */
async function preloadMetadata(imageIds: string[]): Promise<void> {
  const { imageSetId, wadoRsRoot } = props.study

  // Fast path: one request for all metadata when we have an imageSetId.
  // Maps sopUid → [{dicomJson}] so we can register each imageId directly.
  if (imageSetId && wadoRsRoot) {
    try {
      const res = await fetch(`${wadoRsRoot}/imagesets/${imageSetId}/instances/metadata`, {
        credentials: 'include',
      })
      if (res.ok) {
        const allMeta: Record<string, Array<Record<string, unknown>>> = await res.json()
        const registerMeta = (dicomImageLoader as any).wadors.metaDataManager

        for (const imageId of imageIds) {
          // Extract sopUid from the imageId URL path
          const sopMatch = imageId.match(/\/instances\/([^/]+)\/frames\//)
          if (!sopMatch) continue
          const sopUid = sopMatch[1]
          const metaArray = allMeta[sopUid]
          const meta = Array.isArray(metaArray) && metaArray.length > 0 ? metaArray[0] : null
          if (!meta) continue

          registerMeta.add(imageId, meta)
          registerMeta.add(imageId.replace(/\/frames\/\d+$/, ''), meta)
        }

        return
      }
      console.warn('[MriCanvas] bulk metadata endpoint returned', res.status, '— falling back to per-instance')
    } catch (e) {
      console.warn('[MriCanvas] bulk metadata failed, falling back to per-instance', e)
    }
  }

  // Fallback: per-instance metadata (non-HealthImaging sources or if bulk endpoint unavailable).
  // Deduplicates by SOP (strips /frames/N) so a series with N slices makes N requests, not N×frames.
  const toFetch = new Map<string, string>()
  for (const id of imageIds) {
    const metaUrl = id.replace(/^wadors:/, '').replace(/\/frames\/\d+$/, '/metadata')
    if (!toFetch.has(metaUrl)) toFetch.set(metaUrl, id)
  }

  await Promise.all([...toFetch.entries()].map(async ([metaUrl, imageId]) => {
    try {
      const res = await fetch(metaUrl, { credentials: 'include' })
      if (!res.ok) {
        console.warn('[MriCanvas] metadata fetch failed:', res.status, metaUrl)
        return
      }
      const json: Record<string, unknown>[] = await res.json()
      const meta = Array.isArray(json) && json.length > 0 ? json[0] : null
      if (!meta) return

      ;(dicomImageLoader as any).wadors.metaDataManager.add(imageId, meta)
      ;(dicomImageLoader as any).wadors.metaDataManager.add(imageId.replace(/\/frames\/\d+$/, ''), meta)
    } catch (e) {
      console.warn('[MriCanvas] metadata preload error:', metaUrl, e)
    }
  }))
}

function cleanup() {
  csTools.ToolGroupManager.destroyToolGroup(TOOL_GROUP_ID)
  toolGroup = null

  // Check Cornerstone's global registry, not just the local variable.
  // If a previous load() was interrupted before it could store the engine in
  // renderingEngine, the registry still holds a live context that would leak.
  const existing = cs.getRenderingEngine(RENDERING_ENGINE_ID)
  if (existing) {
    try { existing.destroy() } catch { /* already gone */ }
  }
  renderingEngine = null
  engineLayout = null

  try { cs.cache.removeVolumeLoadObject(VOLUME_ID) } catch { /* not cached */ }
}

// Stack mode (1x1), original behavior
async function loadStack() {
  const gen = ++loadGeneration
  const el = getVpRef(0)
  if (!el) return

  const imageIds = buildImageIds()
  if (!imageIds.length) {
    loadError.value = 'No imageUrl or wadoRsRoot set on this study'
    return
  }

  isLoading.value = true
  loadError.value = null

  try {
    // Only rebuild the rendering engine when the layout changes (e.g. multi → single).
    // Destroying and recreating the engine burns a WebGL context each time; browsers cap
    // this at ~16 active contexts. By reusing the engine across study changes we avoid
    // exhausting the pool when the user clicks through series.
    const needsNewEngine = engineLayout !== '1x1' || !cs.getRenderingEngine(RENDERING_ENGINE_ID)
    if (needsNewEngine) {
      cleanup()
      if (gen !== loadGeneration) return

      renderingEngine = new cs.RenderingEngine(RENDERING_ENGINE_ID)
      renderingEngine.enableElement({
        viewportId: 'vp-axial',
        type: cs.Enums.ViewportType.STACK,
        element: el,
      })

      // Wait for viewport registration
      await new Promise<void>((resolve, reject) => {
        const timeout = setTimeout(() => reject(new Error('Viewport registration timed out')), 5_000)
        const interval = setInterval(() => {
          try {
            if (renderingEngine!.getViewport('vp-axial')) {
              clearInterval(interval)
              clearTimeout(timeout)
              resolve()
            }
          } catch { /* not ready */ }
        }, 50)
      })

      engineLayout = '1x1'
      setupTools(['vp-axial'])
    }

    if (gen !== loadGeneration) return

    await preloadMetadata(imageIds)
    if (gen !== loadGeneration) return

    const viewport = renderingEngine.getViewport('vp-axial')
    await Promise.race([
      viewport.setStack(imageIds),
      new Promise((_, reject) => setTimeout(() => reject(new Error('setStack timed out after 10s')), 10_000)),
    ])

    viewport.render()
    activateTool(props.tool)
  } catch (e: any) {
    loadError.value = `Failed to load image: ${e?.message ?? 'Unknown error'}`
    console.error('[MriCanvas]', e)
  } finally {
    isLoading.value = false
  }
}

// Volume mode (1x2, 2x2), orthogonal MPR viewports
async function loadVolume() {
  const gen = ++loadGeneration
  const slots = activeSlots.value
  const elements = slots.map((_, i) => getVpRef(i)).filter(Boolean) as HTMLDivElement[]

  if (elements.length !== slots.length) return

  const imageIds = buildImageIds()
  if (!imageIds.length) {
    loadError.value = 'No imageUrl or wadoRsRoot set on this study'
    return
  }

  isLoading.value = true
  loadError.value = null

  try {
    // Rebuild the engine only when the layout (viewport count) changes.
    // On study change with the same layout, remove the old volume and reuse the engine.
    const needsNewEngine = engineLayout !== props.layout || !cs.getRenderingEngine(RENDERING_ENGINE_ID)
    if (needsNewEngine) {
      cleanup()
      if (gen !== loadGeneration) return

      renderingEngine = new cs.RenderingEngine(RENDERING_ENGINE_ID)

      const viewportInputs = slots.map((slot, i) => ({
        viewportId: slot.id,
        type: cs.Enums.ViewportType.ORTHOGRAPHIC,
        element: elements[i],
        defaultOptions: {
          orientation: slot.orientation as unknown as typeof cs.Enums.OrientationAxis,
        },
      }))
      renderingEngine.setViewports(viewportInputs)
      engineLayout = props.layout
      setupTools(slots.map(s => s.id))
    } else {
      // Same layout — remove the old cached volume so a new one can be created below
      try { cs.cache.removeVolumeLoadObject(VOLUME_ID) } catch { /* not cached */ }
    }

    if (gen !== loadGeneration) return

    await preloadMetadata(imageIds)
    if (gen !== loadGeneration) return

    // Create and cache the volume
    const volume = await cs.volumeLoader.createAndCacheVolume(VOLUME_ID, { imageIds })
    ;(volume as any).load()

    // Set volume on all viewports
    await Promise.all(
      slots.map(slot =>
        cs.setVolumesForViewports(
          renderingEngine!,
          [{ volumeId: VOLUME_ID }],
          [slot.id],
        )
      )
    )

    // Render all viewports
    renderingEngine.renderViewports(slots.map(s => s.id))

    activateTool(props.tool)
  } catch (e: any) {
    loadError.value = `Failed to load volume: ${e?.message ?? 'Unknown error'}`
    console.error('[MriCanvas]', e)
  } finally {
    isLoading.value = false
  }
}

async function loadStudy() {
  await nextTick()
  if (props.layout === '1x1') {
    await loadStack()
  } else {
    await loadVolume()
  }
}

function setupTools(viewportIds: string[]) {
  csTools.ToolGroupManager.destroyToolGroup(TOOL_GROUP_ID)
  toolGroup = csTools.ToolGroupManager.createToolGroup(TOOL_GROUP_ID)

  const { WindowLevelTool, ZoomTool, PanTool, StackScrollTool } = csTools
  ;[WindowLevelTool, ZoomTool, PanTool, StackScrollTool].forEach(tool => {
    toolGroup.addTool(tool.toolName)
    toolGroup.setToolPassive(tool.toolName)
  })

  viewportIds.forEach(id => {
    toolGroup.addViewport(id, RENDERING_ENGINE_ID)
  })
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
}

function resetViewports() {
  if (!renderingEngine) return
  const slots = activeSlots.value
  for (const slot of slots) {
    const vp = renderingEngine.getViewport(slot.id)
    if (vp) {
      vp.resetCamera()
      vp.resetProperties()
      vp.render()
    }
  }
}

function invertViewports() {
  if (!renderingEngine) return
  const slots = activeSlots.value
  for (const slot of slots) {
    const vp = renderingEngine.getViewport(slot.id)
    if (!vp) continue
    const { invert } = vp.getProperties()
    vp.setProperties({ invert: !invert })
    vp.render()
  }
}

defineExpose({ resetViewports, invertViewports })

// Watchers & lifecycle
watch(() => props.study.id, loadStudy)
watch(() => props.layout, loadStudy)
watch(() => props.tool, activateTool)

onMounted(() => {
  configureAuth()
  loadStudy()
})
onUnmounted(cleanup)
</script>

<template>
  <div class="mri-canvas-wrapper">
    <div v-if="isLoading" class="mri-overlay">Loading…</div>
    <div v-if="loadError" class="mri-overlay mri-error">{{ loadError }}</div>

    <div class="grid h-full w-full" :class="gridClass">
      <div
        v-for="(slot, i) in activeSlots"
        :key="slot.id"
        class="relative overflow-hidden"
        :class="{ 'border-r border-gray-700': i === 0 && layout !== '1x1',
                   'border-b border-gray-700': layout === '2x2' && i < 2 }"
      >
        <!-- Orientation label -->
        <span
          v-if="layout !== '1x1'"
          class="absolute top-2 left-2 z-10 font-mono text-xs text-gray-400 bg-black/60 px-2 py-0.5 rounded"
        >
          {{ slot.label }}
        </span>
        <div
          :ref="(el) => { if (i === 0) vpRef0 = el as HTMLDivElement; else if (i === 1) vpRef1 = el as HTMLDivElement; else if (i === 2) vpRef2 = el as HTMLDivElement; else vpRef3 = el as HTMLDivElement; }"
          class="w-full h-full bg-black"
          :style="{ cursor: cursors[tool] }"
        />
      </div>

      <!-- Empty 4th cell in 2x2 (only 3 orientations) -->
      <div
        v-if="layout === '2x2'"
        class="bg-zinc-950 flex items-center justify-center"
      >
        <span class="font-mono text-xs text-zinc-700 tracking-widest">3D Visualization Coming Soon</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mri-canvas-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
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
