<template>
  <div class="max-w-sm space-y-3">
    <Input v-model="username" placeholder="Username" />
    <Input v-model="email" placeholder="Email" />
    <Input v-model="password" type="password" placeholder="Password" />

    <Button variant="success" :disabled="loading" @click="onRegister">
      {{ loading ? "Creating..." : "Register" }}
    </Button>

    <p v-if="error" class="text-sm text-red-600">
      {{ error }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { navigateTo } from "nuxt/app";
import { setToken } from "~/utils/authToken";

type AuthResponse = {
  token: string;
  id: number;
  email: string;
  username: string;
};

const username = ref("");
const email = ref("");
const password = ref("");
const error = ref<string | null>(null);
const loading = ref(false);

const onRegister = async (): Promise<void> => {
  error.value = null;
  loading.value = true;

  try {
    const resp = await useApiFetch<AuthResponse>("/auth/register", {
      method: "POST",
      body: { username: username.value, email: email.value, password: password.value },
    });

    setToken(resp.token);
    await navigateTo("/");
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : "Register failed";
  } finally {
    loading.value = false;
  }
};
</script>
