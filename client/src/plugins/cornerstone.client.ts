// plugins/cornerstone.client.ts
import { defineNuxtPlugin } from '#app'
import * as cs from '@cornerstonejs/core'
import * as csTools from '@cornerstonejs/tools'
import dicomImageLoader from '@cornerstonejs/dicom-image-loader'

type LoaderOptions = Parameters<typeof dicomImageLoader.init>[0]

let initialized = false  // ← guard against double-init

export default defineNuxtPlugin(async () => {
  if (initialized) return {
    provide: { cs, csTools, csDicomImageLoader: dicomImageLoader },
  }

  // Order matters — cs first, then dicomImageLoader, then csTools
  await cs.init()

  // Register the built-in streaming volume loader for MPR viewports
  const { cornerstoneStreamingImageVolumeLoader } = cs
  cs.volumeLoader.registerVolumeLoader(
    'cornerstoneStreamingImageVolume',
    cornerstoneStreamingImageVolumeLoader as unknown as cs.Types.VolumeLoaderFn,
  )

  // Fully await worker registration before anything else
  await new Promise<void>((resolve) => {
    dicomImageLoader.init({
      beforeSend: () => ({}),
      maxWebWorkers: 1,
    } satisfies LoaderOptions)
    // Give the worker a tick to register itself with Cornerstone's engine registry
    setTimeout(resolve, 100)
  })

  csTools.init()

  const { WindowLevelTool, ZoomTool, PanTool, StackScrollTool } = csTools
  ;[WindowLevelTool, ZoomTool, PanTool, StackScrollTool].forEach(tool => {
    if (!csTools.state.tools[tool.toolName]) csTools.addTool(tool)
  })

  initialized = true

  return {
    provide: { cs, csTools, csDicomImageLoader: dicomImageLoader },
  }
})