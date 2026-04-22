<template>
  <DefaultLayout>
    <div class="relative text-center px-6">
      <h1 class="text-6xl sm:text-7xl font-bold tracking-tight text-slate-900">
        404
      </h1>

      <p class="mt-4 text-lg sm:text-xl text-slate-600">
        page not found
      </p>

      <div class="mt-8 flex justify-center">
        <Button
            variant="primary"
            size="md"
            rounded
            @click="navigateTo('/dashboard')"
        >
          Dashboard
        </Button>
      </div>
    </div>
  </DefaultLayout>
</template>

<script setup lang="ts">
import DefaultLayout from '~/layouts/default.vue';
import Button from '~/components/Button.vue';
</script>

<style scoped>
</style>