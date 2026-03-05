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
      viteCommonjs(),
    ],
    resolve: {
      dedupe: [
        '@cornerstonejs/core',
        'vue',
      ],
    },
    optimizeDeps: {
      exclude: ['@cornerstonejs/dicom-image-loader'],
      include: [
        'dicom-parser',
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