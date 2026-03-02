<template>
  <div class="p-4 flex flex-col gap-6">
    <UserProfile :user="user"/>
    <DicomUpload v-if="isClinician"/>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from "~/stores/userStore";

const userStore = useUserStore();
const user = userStore.user;
const isClinician = userStore.hasRole("CLINICIAN");
</script>

<style scoped>

</style>