<template>
  <div class="p-10">
    <div class="mb-8">
      <h1 class="text-2xl font-semibold text-slate-900 tracking-tight">Dashboard</h1>
      <p class="mt-1 text-sm text-slate-500">Access your profile, tools, and imaging features.</p>
    </div>

    <div class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
      <Card
          v-if="userStore.isAdmin"
          variant="outlined"
          hover
          rounded
          hoverBorderClass="hover:border-red-300"
          class="group block cursor-pointer"
          @click="navigateTo('/dashboard/admin')"
      >
        <div class="inline-flex h-10 w-10 items-center justify-center rounded-xl bg-red-50 text-red-600">
          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M12 11c0-1.657 1.343-3 3-3h3v10h-6v-7zM6 8h3c1.657 0 3 1.343 3 3v7H6V8z"
            />
          </svg>
        </div>

        <div class="mt-3">
          <div class="text-sm font-semibold text-slate-900">Admin Center</div>
          <div class="mt-0.5 text-xs text-slate-500">
            Manage users, logs, and system configuration.
          </div>
        </div>

        <div class="absolute right-5 top-5 text-slate-300 transition-all duration-200 group-hover:translate-x-0.5 group-hover:text-red-500">
          →
        </div>
      </Card>

      <Card
          v-if="isClinician"
          variant="outlined"
          hover
          rounded
          hoverBorderClass="hover:border-teal-300"
          class="group block cursor-pointer"
          @click="navigateTo('/dashboard/dicom-upload')"
      >
        <div class="inline-flex h-10 w-10 items-center justify-center rounded-xl bg-teal-50 text-teal-600">
          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1M12 4v12m0 0l-4-4m4 4l4-4"
            />
          </svg>
        </div>
        <div class="mt-3">
          <div class="text-sm font-semibold text-slate-900">DICOM Upload</div>
          <div class="mt-0.5 text-xs text-slate-500">Upload medical imaging files securely.</div>
        </div>
        <div class="absolute right-5 top-5 text-slate-300 transition-all duration-200 group-hover:translate-x-0.5 group-hover:text-teal-500">
          →
        </div>
      </Card>

      <Card
          variant="outlined"
          hover
          rounded
          hoverBorderClass="hover:border-blue-300"
          class="group block cursor-pointer"
          @click="navigateTo('/dashboard/profile')"
      >
        <div class="inline-flex h-10 w-10 items-center justify-center rounded-xl bg-blue-50 text-blue-600">
          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M5.121 17.804A9 9 0 1118.88 17.8M15 11a3 3 0 11-6 0 3 3 0 016 0z"
            />
          </svg>
        </div>
        <div class="mt-3">
          <div class="text-sm font-semibold text-slate-900">User Profile</div>
          <div class="mt-0.5 text-xs text-slate-500">View and manage your account details.</div>
        </div>
        <div class="absolute right-5 top-5 text-slate-300 transition-all duration-200 group-hover:translate-x-0.5 group-hover:text-blue-500">
          →
        </div>
      </Card>

      <Card
          variant="outlined"
          hover
          rounded
          hoverBorderClass="hover:border-violet-300"
          class="group block cursor-pointer"
          @click="navigateTo('/dashboard/tools')"
      >
        <div class="inline-flex h-10 w-10 items-center justify-center rounded-xl bg-violet-50 text-violet-600">
          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M10.325 4.317a1 1 0 011.35-.447l.994.497a1 1 0 00.894 0l.994-.497a1 1 0 011.35.447l.518.966a1 1 0 00.75.52l1.084.156a1 1 0 01.847.99v1.094a1 1 0 00.292.707l.78.78a1 1 0 010 1.414l-.78.78a1 1 0 00-.292.707v1.094a1 1 0 01-.847.99l-1.084.156a1 1 0 00-.75.52l-.518.966a1 1 0 01-1.35.447l-.994-.497a1 1 0 00-.894 0l-.994.497a1 1 0 01-1.35-.447l-.518-.966a1 1 0 00-.75-.52l-1.084-.156a1 1 0 01-.847-.99v-1.094a1 1 0 00-.292-.707l-.78-.78a1 1 0 010-1.414l.78-.78a1 1 0 00.292-.707V7.966a1 1 0 01.847-.99l1.084-.156a1 1 0 00.75-.52l.518-.966z"
            />
            <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
            />
          </svg>
        </div>
        <div class="mt-3">
          <div class="text-sm font-semibold text-slate-900">Available Tools</div>
          <div class="mt-0.5 text-xs text-slate-500">Browse platform tools and capabilities.</div>
        </div>
        <div class="absolute right-5 top-5 text-slate-300 transition-all duration-200 group-hover:translate-x-0.5 group-hover:text-violet-500">
          →
        </div>
      </Card>

      <Card
          variant="outlined"
          hover
          rounded
          hoverBorderClass="hover:border-amber-300"
          class="group block cursor-pointer"
          @click="navigateTo('/dashboard/dicom')"
      >
        <div class="inline-flex h-10 w-10 items-center justify-center rounded-xl bg-amber-50 text-amber-600">
          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 19h8a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"
            />
          </svg>
        </div>
        <div class="mt-3">
          <div class="text-sm font-semibold text-slate-900">DICOM Viewer</div>
          <div class="mt-0.5 text-xs text-slate-500">Open and inspect medical imaging studies.</div>
        </div>
        <div class="absolute right-5 top-5 text-slate-300 transition-all duration-200 group-hover:translate-x-0.5 group-hover:text-amber-500">
          →
        </div>
      </Card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from "~/stores/user";

useHead({
  title: "Dashboard",
});

const userStore = useUserStore();
const isClinician = userStore.hasRole("CLINICIAN");
</script>