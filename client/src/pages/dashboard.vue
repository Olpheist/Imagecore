<template>
  Welcome {{ user }}
  <Button
      @click="onLogout"
      hover
  >
    Logout
  </Button>
</template>

<script setup lang="ts">
import { useUserStore } from "~/stores/userStore";

const userStore = useUserStore();
const user = userStore.user;

const onLogout = async () => {
  await userStore.logout();
}

</script>

<style scoped>

</style>