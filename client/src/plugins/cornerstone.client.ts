// plugins/cornerstone.client.ts
import { defineNuxtPlugin } from '#app'
import * as cs from '@cornerstonejs/core'
import * as csTools from '@cornerstonejs/tools'
import dicomImageLoader from '@cornerstonejs/dicom-image-loader'

type LoaderOptions = Parameters<typeof dicomImageLoader.init>[0]

export default defineNuxtPlugin(async () => {
  await cs.init()

  const loaderOptions: LoaderOptions = {
    beforeSend: () => ({}),
    strict: false,
    maxWebWorkers: 1,
  }

  dicomImageLoader.init(loaderOptions)
  csTools.init()

  const { WindowLevelTool, ZoomTool, PanTool, StackScrollTool } = csTools
  ;[WindowLevelTool, ZoomTool, PanTool, StackScrollTool].forEach(tool => {
    if (!csTools.state.tools[tool.toolName]) csTools.addTool(tool)
  })

  return {
    provide: { cs, csTools, csDicomImageLoader: dicomImageLoader },
  }
})