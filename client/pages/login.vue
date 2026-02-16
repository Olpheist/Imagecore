<template>
  <div class="max-w-sm space-y-3">
    <Input v-model="username" placeholder="Username" />
    <Input v-model="password" type="password" placeholder="Password" />

    <Button variant="success" :disabled="loading" @click="onLogin">
      {{ loading ? "Logging in..." : "Login" }}
    </Button>

    <p v-if="error" class="text-sm text-red-600">
      {{ error }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { setToken } from "~/utils/authToken";

type AuthResponse = {
  token: string;
  id: number;
  email: string;
  username: string;
};

const username = ref("");
const password = ref("");
const error = ref<string | null>(null);
const loading = ref(false);

const onLogin = async (): Promise<void> => {
  error.value = null;
  loading.value = true;

  try {
    const resp = await useApiFetch<AuthResponse>("/auth/login", {
      method: "POST",
      body: { username: username.value, password: password.value },
    });

    setToken(resp.token);

    // TODO: store user data in a store for later user
    // TODO: also in register
    await navigateTo("/");
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : "Login failed";
  } finally {
    loading.value = false;
  }
};
</script>
