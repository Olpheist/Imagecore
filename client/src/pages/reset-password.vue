<template>
  <div class="min-h-screen flex items-center justify-center">
    <Card variant="elevated" rounded class="w-full max-w-md">
      <div class="space-y-6">
        <div class="text-center">
          <h1 class="text-2xl font-semibold">Reset Password</h1>
          <p class="text-sm text-gray-500 mt-1">
            Enter your new password below.
          </p>
        </div>
        <div class="space-y-4">
          <Input
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="New password"
          >
            <template #suffix>
              <button
                  type="button"
                  class="text-sm text-gray-500 hover:text-gray-700 cursor-pointer"
                  @click="showPassword = !showPassword"
              >
                {{ showPassword ? "Hide" : "Show" }}
              </button>
            </template>
          </Input>
          <Input
              v-model="confirmPassword"
              placeholder="Confirm password"
              :type="showConfirmPassword ? 'text' : 'password'"
          >
            <template #suffix>
              <button
                  type="button"
                  class="text-sm text-gray-500 hover:text-gray-700 cursor-pointer"
                  @click="showConfirmPassword = !showConfirmPassword"
              >
                {{ showConfirmPassword ? "Hide" : "Show" }}
              </button>
            </template>
          </Input>
        </div>
        <Button
            variant="success"
            class="w-full"
            :disabled="loading"
            @click="onSubmit"
            hover
            rounded
        >
          {{ loading ? "Updating..." : "Update Password" }}
        </Button>
        <Error :error="error" dismissible @close="error = null" />
        <div v-if="success" class="text-sm text-green-700 text-center">
          Password updated successfully.
          <div class="mt-2">
            <NuxtLink to="/login" class="text-blue-600 hover:underline font-medium">
              Go to login
            </NuxtLink>
          </div>
        </div>
      </div>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute } from "nuxt/app";
import { useApiFetch } from "~/composables/useApiFetch";
import type { ApiError } from "~/models/error";
import Card from "~/components/Card.vue";
import Button from "~/components/Button.vue";
import Input from "~/components/Input.vue";
import Error from "~/components/Error.vue";

useHead({
  title: "Reset Password",
});

definePageMeta({
  layoutBackground: false,
});

const route = useRoute();

const token = computed(() => {
  const t = route.query.token;
  return typeof t === "string" ? t : null;
});

const password = ref("");
const confirmPassword = ref("");
const showPassword = ref(false);
const showConfirmPassword = ref(false);
const loading = ref(false);
const success = ref(false);
const error = ref<ApiError | null>(null);

const onSubmit = async (): Promise<void> => {
  error.value = null;
  loading.value = true;

  try {
    await useApiFetch<void>("/auth/reset-password", {
      method: "POST",
      body: {
        token: token.value,
        newPassword: password.value,
        confirmPassword: confirmPassword.value
      },
    });

    success.value = true;
  } catch (e: unknown) {
    error.value = e as ApiError;
  } finally {
    loading.value = false;
  }
};
</script>