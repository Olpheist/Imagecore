<template>
  <div>
    <div class="mb-8">
      <h1 class="text-2xl font-semibold tracking-tight text-slate-900">Admin Center</h1>
      <p class="mt-1 text-sm text-slate-500">System management and oversight tools.</p>
    </div>

    <div class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
      <Card
          variant="outlined"
          hover
          rounded
          hoverBorderClass="hover:border-teal-300"
          class="group block cursor-pointer"
          @click="navigateTo('/dashboard/admin/users')"
      >
        <div class="inline-flex h-10 w-10 items-center justify-center rounded-xl bg-teal-50 text-teal-600">
          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z"
            />
          </svg>
        </div>
        <div class="mt-3">
          <div class="text-sm font-semibold text-slate-900">Users</div>
          <div class="mt-0.5 text-xs text-slate-500">View and manage user accounts</div>
        </div>
        <div class="absolute right-5 top-5 text-slate-300 transition-all duration-200 group-hover:translate-x-0.5 group-hover:text-teal-500">
          →
        </div>
      </Card>

      <Card
          variant="outlined"
          hover
          rounded
          hoverBorderClass="hover:border-violet-300"
          class="group block cursor-pointer"
          @click="navigateTo('/dashboard/admin/logs')"
      >
        <div class="inline-flex h-10 w-10 items-center justify-center rounded-xl bg-violet-50 text-violet-600">
          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
            />
          </svg>
        </div>
        <div class="mt-3">
          <div class="text-sm font-semibold text-slate-900">Logs</div>
          <div class="mt-0.5 text-xs text-slate-500">Inspect recent application logs</div>
        </div>
        <div class="absolute right-5 top-5 text-slate-300 transition-all duration-200 group-hover:translate-x-0.5 group-hover:text-violet-500">
          →
        </div>
      </Card>
    </div>
  </div>
</template>

<script setup lang="ts">
definePageMeta({ layout: "admin" });

useHead({
  title: "Admin Dashboard",
});
</script>