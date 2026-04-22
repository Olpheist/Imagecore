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
        <form class="space-y-4" @submit.prevent="onLogin">
          <Input
              v-model="username"
              placeholder="Username"
          />
          <Input
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="Password"
          >
            <template #suffix>
              <button
                  type="button"
                  class="text-sm text-gray-500 hover:text-gray-700 cursor-pointer"
                  @click="showPassword = !showPassword"
                  tabindex="-1"
              >
                {{ showPassword ? "Hide" : "Show" }}
              </button>
            </template>
          </Input>
          <Button
              type="submit"
              variant="success"
              class="w-full"
              :disabled="loading"
              hover
              rounded
          >
            {{ loading ? "Logging in..." : "Login" }}
          </Button>
        </form>
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
          <br>
          Forgot your password?
          <button
              class="text-blue-600 hover:underline font-medium"
              style="cursor: pointer;"
              @click="onForgotPassword"
          >
            Reset it
          </button>
        </div>
      </div>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { navigateTo } from "nuxt/app";
import type { AuthResponse } from "~/models/auth";
import { useApiFetch } from "~/composables/useApiFetch";
import { useUserStore } from "~/stores/user";
import type { ApiError } from "~/models/error";

useHead({
  title: "Login",
});

definePageMeta({
  layoutBackground: false,
});

const userStore = useUserStore();

const username = ref("");
const password = ref("");
const showPassword = ref(false);
const error = ref<ApiError | null>(null);
const loading = ref(false);

const onLogin = async (): Promise<void> => {
  error.value = null;
  loading.value = true;

  try {
    await useApiFetch<AuthResponse>("/auth/login", {
      method: "POST",
      body: { username: username.value, password: password.value },
    });

    await userStore.fetchMe();
    await navigateTo("/");
  } catch (e: unknown) {
    error.value = e as ApiError;
  } finally {
    loading.value = false;
  }
};

const onRegister = async (): Promise<void> => {
  await navigateTo("/register");
};

const onForgotPassword = async (): Promise<void> => {
  await navigateTo("/forgot-password");
};

watch([username, password], () => {
  error.value = null;
});
</script>
