<template>
  <div class="min-h-screen flex flex-col">
    <section class="max-w-6xl mx-auto px-6 py-20 text-center">
      <h1 class="text-4xl font-bold text-slate-900 mb-4">
        Medical Image Analysis
      </h1>
      <p class="text-lg text-slate-600 max-w-2xl mx-auto mb-8">
        ImageCore allows clinicians and researchers to securely upload,
        analyze, and manage medical imaging data in a centralized platform.
      </p>
    </section>
    <section class="max-w-6xl mx-auto px-6">
      <h2 class="text-2xl font-semibold text-center mb-10">
        How ImageCore Works
      </h2>
      <div class="grid md:grid-cols-3 gap-6">
        <Card rounded>
          <h3 class="font-semibold mb-2">Upload Images</h3>
          <p class="text-sm text-slate-600">
            Securely upload DICOM or medical image files to the platform.
          </p>
        </Card>
        <Card rounded>
          <h3 class="font-semibold mb-2">Run Analysis</h3>
          <p class="text-sm text-slate-600">
            ImageCore processes images using automated analysis pipelines.
          </p>
        </Card>
        <Card rounded>
          <h3 class="font-semibold mb-2">View Results</h3>
          <p class="text-sm text-slate-600">
            Review analysis results and reports from your dashboard.
          </p>
        </Card>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
</style>
