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
        <form class="space-y-4" @submit.prevent="onRegister">
          <Input v-model="username" placeholder="Username" />
          <Input v-model="email" placeholder="Email" />
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
          <Input
              v-model="confirmPassword"
              :type="showConfirmPassword ? 'text' : 'password'"
              placeholder="Confirm Password"
          >
            <template #suffix>
              <button
                  type="button"
                  class="text-sm text-gray-500 hover:text-gray-700 cursor-pointer"
                  @click="showConfirmPassword = !showConfirmPassword"
                  tabindex="-1"
              >
                {{ showConfirmPassword ? "Hide" : "Show" }}
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
            {{ loading ? "Creating..." : "Register" }}
          </Button>
        </form>
        <Error :error="error" dismissible @close="error = null" />

        <div class="flex items-center gap-2 my-2">
          <hr class="flex-1 border-gray-200" />
          <span class="text-xs text-gray-400">or</span>
          <hr class="flex-1 border-gray-200" />
        </div>

        <div ref="googleButtonRef" class="flex justify-center" />

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
import { ref, watch, onMounted } from "vue";
import { navigateTo } from "nuxt/app";
import type { AuthResponse } from "~/models/auth";
import { useApiFetch } from "~/composables/useApiFetch";
import { useUserStore } from "~/stores/user";
import type { ApiError } from "~/models/error";
import { useGoogleSignIn } from "~/composables/useGoogleSignIn";

useHead({
  title: "Register",
});

const userStore = useUserStore();

const username = ref("");
const email = ref("");
const password = ref("");
const confirmPassword = ref("");
const showPassword = ref(false);
const showConfirmPassword = ref(false);
const error = ref<ApiError | null>(null);
const loading = ref(false);
const googleButtonRef = ref<HTMLElement | null>(null);

const { initButton } = useGoogleSignIn({
  onSuccess: () => navigateTo("/"),
  onError: (err) => { error.value = err; },
});

onMounted(() => {
  if (googleButtonRef.value) {
    initButton(googleButtonRef.value);
  }
});

const onRegister = async (): Promise<void> => {
  error.value = null;
  loading.value = true;

  try {
    await useApiFetch<AuthResponse>("/auth/register", {
      method: "POST",
      body: {
        username: username.value,
        email: email.value,
        password: password.value,
        confirmPassword: confirmPassword.value,
      },
    });

    await userStore.fetchMe();
    await navigateTo("/");
  } catch (e: unknown) {
    error.value = e as ApiError;
  } finally {
    loading.value = false;
  }
};

const onLogin = async (): Promise<void> => {
  await navigateTo("/login");
};

watch([username, email, password, confirmPassword], () => {
  error.value = null;
});
</script>
