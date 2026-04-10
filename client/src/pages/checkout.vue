<template>
  <div class="min-h-screen flex items-center justify-center px-6 py-12">
    <div class="w-full max-w-md rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
      <h1 class="text-2xl font-semibold">Upgrade to Pro</h1>
      <p class="mt-2 text-sm text-gray-600">
        Start a Stripe checkout session for your Pro subscription.
      </p>
      <button
          class="mt-6 w-full rounded-lg bg-black px-4 py-2 text-white disabled:cursor-not-allowed disabled:opacity-60"
          :disabled="loading"
          @click="startCheckout"
      >
        {{ loading ? "Redirecting..." : "Continue to Checkout" }}
      </button>
      <p
          v-if="error"
          class="mt-4 text-sm text-red-600"
      >
        {{ error }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useApiFetch } from "~/composables/useApiFetch";

type CheckoutSessionResponse = {
  url: string;
};

const loading = ref(false);
const error = ref<string | null>(null);

async function startCheckout(): Promise<void> {
  loading.value = true;
  error.value = null;

  try {
    const response = await useApiFetch<CheckoutSessionResponse>("/billing/checkout", {
      method: "POST",
    });

    if (!response?.url) {
      throw new Error("No checkout URL returned.");
    }

    window.location.href = response.url;
  } catch (e: any) {
    error.value = e?.message ?? "Failed to start checkout.";
    loading.value = false;
  }
}
</script>