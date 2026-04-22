<template>
  <div class="min-h-screen flex flex-col bg-slate-200 relative overflow-hidden">
    <Header />

    <main class="flex-1 relative z-10 flex items-center justify-center">
      <div class="absolute inset-0 pointer-events-none">
        <div class="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(15,23,42,0.18),transparent_55%)]"></div>
        <div class="absolute inset-y-0 left-0 w-64 bg-linear-to-r from-slate-400/70 to-transparent"></div>
        <div class="absolute inset-y-0 right-0 w-64 bg-linear-to-l from-slate-400/70 to-transparent"></div>
      </div>

      <div class="relative px-4 w-full max-w-md">
        <Card variant="elevated" rounded class="text-center">
          <div class="py-4 space-y-6">
            <div class="space-y-1">
              <p class="text-xs font-semibold uppercase tracking-widest text-slate-400">Error</p>
              <h1 class="text-8xl font-bold tracking-tight text-slate-900">
                {{ error?.statusCode ?? 404 }}
              </h1>
            </div>

            <div class="w-12 h-px bg-slate-200 mx-auto"></div>

            <div class="space-y-1">
              <p class="text-lg font-semibold text-slate-700">
                Page not found
              </p>
            </div>

            <Button variant="primary" size="md" rounded class="w-full" @click="handleBack">
              Back to Dashboard
            </Button>
          </div>
        </Card>
      </div>
    </main>

    <Footer />
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import Header from '~/components/Header.vue';
import Footer from '~/components/Footer.vue';
import Button from '~/components/Button.vue';
import Card from '~/components/Card.vue';

const props = defineProps<{
  error: { statusCode?: number; statusMessage?: string } | null;
}>();

const handleBack = () => {
  clearError({ redirect: '/dashboard' });
};
</script>