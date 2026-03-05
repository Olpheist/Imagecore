// https://nuxt.com/docs/api/configuration/nuxt-config
import tailwindcss from "@tailwindcss/vite";
import { viteCommonjs } from "@originjs/vite-plugin-commonjs";

export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },
  ssr: false,
  srcDir: 'src/',
  css: ['./assets/css/main.css'],
  vite: {
    plugins: [
      tailwindcss(),
      viteCommonjs(), // required for dicom-parser CJS interop
    ],
    optimizeDeps: {
      exclude: [
        '@cornerstonejs/dicom-image-loader', // must be excluded so workers load correctly
      ],
      include: [
        'dicom-parser', // must be included separately
        '@cornerstonejs/core',
        '@cornerstonejs/tools',
      ],
    },
    worker: {
      format: 'es',
    },
  },
  modules: [
    '@pinia/nuxt',
  ]
});