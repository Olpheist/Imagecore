<template>
  Welcome {{ user }}
</template>

<script setup lang="ts">
import { useUserStore } from "~/stores/userStore";

const userStore = useUserStore();
const user = userStore.user;

</script>

<style scoped>

</style>