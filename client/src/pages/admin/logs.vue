<template>
  <div v-if="userStore.isAdmin">
    <div class="mb-6">
      <h1 class="text-3xl font-semibold text-slate-900">Logs</h1>
      <p class="text-slate-600 mt-1">Inspect recent application logs.</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from "~/stores/userStore";
const userStore = useUserStore();

definePageMeta({
  layout: "admin"
});
</script>

<style scoped>

</style>