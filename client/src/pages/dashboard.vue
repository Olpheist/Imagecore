<template>
  <div class="p-4">
    <UserProfile :user="user"/>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from "~/stores/userStore";

const userStore = useUserStore();
const user = userStore.user;

</script>

<style scoped>

</style>