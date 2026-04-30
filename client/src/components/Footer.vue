<template>
  <footer class="w-full border-t border-slate-200 bg-slate-100">
    <div class="max-w-7xl mx-auto px-6 py-6 flex flex-col sm:flex-row items-center justify-between text-sm text-gray-600">
      <div>
        © {{ year }} ImageCore. All rights reserved.
      </div>
      <div class="flex items-center gap-6 mt-3 sm:mt-0">
        <NuxtLink
            class="hover:text-gray-900 transition"
            to="/privacy"
        >
          Privacy
        </NuxtLink>

        <NuxtLink
            class="hover:text-gray-900 transition"
            to="/terms"
        >
          Terms
        </NuxtLink>
      </div>

    </div>
  </footer>
</template>

<script setup lang="ts">
const year = new Date().getFullYear();
</script>

<style scoped>

</style>
