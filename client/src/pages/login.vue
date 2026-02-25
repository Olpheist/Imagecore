<template>
  <div class="min-h-screen flex items-center justify-center">
    <Card variant="elevated" rounded class="w-full max-w-md">
      <div class="space-y-6">
        <div class="text-center">
          <h1 class="text-2xl font-semibold">Welcome Back</h1>
          <p class="text-sm text-gray-500 mt-1">
            Sign in to your account
          </p>
        </div>
        <div class="space-y-4">
          <Input
              v-model="username"
              placeholder="Username"
          />
          <Input
              v-model="password"
              type="password"
              placeholder="Password"
          />
        </div>
        <Button
            variant="success"
            class="w-full"
            :disabled="loading"
            @click="onLogin"
            hover
            rounded
        >
          {{ loading ? "Logging in..." : "Login" }}
        </Button>
        <Error :error="error" dismissible @close="error = null" />
        <div class="text-center text-sm text-gray-600">
          Don’t have an account?
          <button
              class="text-blue-600 hover:underline font-medium"
              style="cursor: pointer;"
              @click="onRegister"
          >
            Register
          </button>
        </div>
      </div>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { setToken } from "~/utils/authToken";
import { navigateTo } from "nuxt/app";
import type {AuthResponse} from "~/models/auth";
import { useApiFetch } from "~/composables/useApiFetch";
import { useUserStore } from "~/stores/userStore";
import type {ApiError} from "~/models/error";

const userStore = useUserStore();

const username = ref("");
const password = ref("");
const error = ref<ApiError | null>(null);
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
    await userStore.fetchMe();

    await navigateTo("/");
  } catch (e: unknown) {
    error.value = e as ApiError;
  } finally {
    loading.value = false;
  }
};

const onRegister = async (): Promise<void> => {
  await navigateTo('/register');
}

watch([username, password], () => {
  error.value = null;
});
</script>
