<template>
  <div class="min-h-screen flex flex-col bg-slate-200">
    <Header />
    <main class="flex-1">
      <slot />
    </main>
    <Footer />
  </div>
</template>

<script setup lang="ts">

</script>

<style scoped>

</style>