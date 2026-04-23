// https://nuxt.com/docs/api/configuration/nuxt-config
import tailwindcss from "@tailwindcss/vite";
import { viteCommonjs } from "@originjs/vite-plugin-commonjs";

export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },
  ssr: false,
  srcDir: 'src/',
  css: ['./assets/css/main.css'],
  app: {
    head: {
      title: "ImageCore",
      script: [
        { src: "https://accounts.google.com/gsi/client", async: true, defer: true }
      ]
    }
  },
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