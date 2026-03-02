<template>
  <div class="min-h-screen flex items-center justify-center px-4">
    <Card variant="default" :rounded="true" class="w-full max-w-md">
      <div class="mb-6">
        <div class="text-center">
          <h1 class="text-2xl font-semibold">Forgot Password</h1>
          <p class="text-sm text-gray-500 mt-1">
            Enter your email and we’ll send a reset link.
          </p>
        </div>
      </div>
      <div class="space-y-1">
        <label class="block text-sm font-medium text-gray-700">Email</label>
        <Input
            v-model="email"
            type="email"
            placeholder="you@example.com"
            :disabled="loading"
        />
      </div>
      <Button
          type="submit"
          variant="success"
          size="md"
          :rounded="true"
          :disabled="loading || !email"
          class="w-full mt-2"
          @click="onSubmit"
      >
        <span v-if="!loading">Send reset link</span>
        <span v-else>Sending...</span>
      </Button>
      <div v-if="submitted" class="mt-4 text-sm text-green-700">
        If that email exists, a reset link has been sent.
      </div>
      <div class="mt-6 text-sm text-gray-600">
        Remembered it?
        <NuxtLink to="/login" class="text-blue-600 hover:underline">
          Back to login
        </NuxtLink>
      </div>
    </Card>
  </div>
</template>

<script setup lang="ts">
import Card from "~/components/Card.vue";
import Button from "~/components/Button.vue";
import Input from "~/components/Input.vue";
import { useApiFetch } from "~/composables/useApiFetch";

const email = ref("");
const loading = ref(false);
const submitted = ref(false);

const onSubmit = async (): Promise<void> => {
  submitted.value = false;
  loading.value = true;

  try {
    await useApiFetch<void>("/auth/forgot-password", {
      method: "POST",
      body: { email: email.value },
    });
    submitted.value = true;
    email.value = "";
  } catch (e) {
    // dont worry about error
    submitted.value = true;
  } finally {
    loading.value = false;
  }
};
</script>