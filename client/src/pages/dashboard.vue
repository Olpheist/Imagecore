<template>
  <div class="p-4 flex flex-col gap-6">
    <UserProfile :user="user"/>
    <DicomUpload v-if="isClinician"/>
  </div>

  <div class="p-4 flex-col gap-6">
    <Button variant="primary" hover rounded @click="$router.push('/tools')">View Available Tools</Button>
  </div>

  <div class="p-4 flex-col gap-6">
    <Button variant="primary" hover rounded @click="$router.push('/dicom')">Go to our DICOM Viewer</Button>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from "~/stores/userStore";

useHead({
  title: "Dashboard",
});

const userStore = useUserStore();
const user = userStore.user;
const isClinician = userStore.hasRole("CLINICIAN");
</script>

<style scoped>

</style>