<template>
  <div v-if="isClinician" class="p-4 flex-col gap-6">
    <Button variant="primary" hover rounded @click="$router.push('/dicom-upload')">DICOM Upload</Button>
  </div>

  <div class="p-4 flex-col gap-6">
    <Button variant="primary" hover rounded @click="$router.push('/profile')">User Profile</Button>
  </div>

  <div class="p-4 flex-col gap-6">
    <Button variant="primary" hover rounded @click="$router.push('/tools')">View Available Tools</Button>
  </div>

  <div class="p-4 flex-col gap-6">
    <Button variant="primary" hover rounded @click="$router.push('/dicom')">Go to our DICOM Viewer</Button>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from "~/stores/user";

useHead({
  title: "Dashboard",
});

const userStore = useUserStore();
const isClinician = userStore.hasRole("CLINICIAN");
</script>

<style scoped>

</style>