<template>
  <div class="min-h-screen flex items-center justify-center">
    <Card variant="elevated" rounded class="w-full max-w-md">
      <div class="space-y-6">
        <div class="text-center">
          <h1 class="text-2xl font-semibold">Create Account</h1>
          <p class="text-sm text-gray-500 mt-1">
            Register to start using ImageCore
          </p>
        </div>
        <div class="space-y-4">
          <Input v-model="username" placeholder="Username" />
          <Input v-model="email" placeholder="Email" />
          <Input v-model="password" type="password" placeholder="Password" />
        </div>
        <Button
            variant="success"
            class="w-full"
            :disabled="loading"
            @click="onRegister"
            hover
            rounded
        >
          {{ loading ? "Creating..." : "Register" }}
        </Button>
        <p
            v-if="error"
            class="text-sm text-red-600 text-center bg-red-50 border border-red-200 rounded-md p-2"
        >
          {{ error }}
        </p>
        <div class="text-center text-sm text-gray-600">
          Already have an account?
          <button
              class="text-blue-600 hover:underline font-medium"
              style="cursor: pointer;"
              @click="onLogin"
          >
            Login
          </button>
        </div>
      </div>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { navigateTo } from "nuxt/app";
import { setToken } from "~/utils/authToken";
import type { AuthResponse } from "~/models/auth";
import { useApiFetch } from "~/composables/useApiFetch";
import { useUserStore } from "~/stores/userStore";

const userStore = useUserStore();

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
    await userStore.fetchMe();
    await navigateTo("/");
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : "Register failed";
  } finally {
    loading.value = false;
  }
};

const onLogin = async (): Promise<void> => {
  await navigateTo("/login");
};

watch([username, email, password], () => {
  error.value = null;
});
</script>
