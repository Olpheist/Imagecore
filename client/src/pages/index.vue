<template>
  <div class="flex gap-4 items-center">
    <!-- Not logged in -->
    <template v-if="!userStore.isLoggedIn">
      <Button hover @click="goLogin">
        Login
      </Button>

      <Button hover @click="goRegister">
        Register
      </Button>
    </template>

    <!-- Logged in -->
    <template v-else>
      <span class="text-lg">
        Welcome, {{ userStore.user?.username }}
      </span>

      <Button hover @click="goDashboard">
        Dashboard
      </Button>
    </template>
  </div>
</template>

<script setup lang="ts">
import { navigateTo } from "nuxt/app";
import { useUserStore } from "~/stores/userStore";

const userStore = useUserStore();

const goLogin = () => navigateTo("/login");
const goRegister = () => navigateTo("/register");
const goDashboard = () => navigateTo("/dashboard");
</script>

<style scoped>

</style>
