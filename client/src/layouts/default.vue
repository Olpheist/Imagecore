<template>
  <div class="min-h-screen flex flex-col bg-slate-200 relative overflow-hidden">
    <div class="absolute inset-0 pointer-events-none">
      <div class="absolute inset-y-0 left-0 w-40 bg-gradient-to-r from-slate-300/60 to-transparent"></div>
      <div class="absolute inset-y-0 right-0 w-40 bg-gradient-to-l from-slate-300/60 to-transparent"></div>
    </div>

    <Header />

    <main class="flex-1 relative z-10">
      <div class="max-w-7xl mx-auto w-full px-4 sm:px-6 lg:px-8 py-6">
        <slot />
      </div>
    </main>

    <Footer />
  </div>
</template>

<script setup lang="ts">

</script>

<style scoped>

</style>