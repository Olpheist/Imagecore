<template>
  <div v-if="userStore.isAdmin">
    <div class="mb-8">
      <h1 class="text-2xl font-semibold text-slate-900 tracking-tight">Dashboard</h1>
      <p class="text-slate-500 mt-1 text-sm">System management and oversight tools.</p>
    </div>

    <div class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
      <NuxtLink class="dash-card" to="/admin/users">
        <div class="dash-card-icon bg-teal-50 text-teal-600">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z"/>
          </svg>
        </div>
        <div class="mt-3">
          <div class="text-sm font-semibold text-slate-900">Users</div>
          <div class="text-xs text-slate-500 mt-0.5">View and manage user accounts</div>
        </div>
        <div class="dash-card-arrow">→</div>
      </NuxtLink>

      <NuxtLink class="dash-card" to="/admin/logs">
        <div class="dash-card-icon bg-violet-50 text-violet-600">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
          </svg>
        </div>
        <div class="mt-3">
          <div class="text-sm font-semibold text-slate-900">Logs</div>
          <div class="text-xs text-slate-500 mt-0.5">Inspect recent application logs</div>
        </div>
        <div class="dash-card-arrow">→</div>
      </NuxtLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from "~/stores/userStore";
const userStore = useUserStore();

definePageMeta({ layout: "admin" });
</script>

<style scoped>
.dash-card {
  position: relative;
  display: block;
  background: white;
  border: 1px solid rgb(226 232 240);
  border-radius: 1rem;
  padding: 1.25rem;
  box-shadow: 0 1px 3px rgb(0 0 0 / 0.04);
  transition: transform 150ms ease, box-shadow 150ms ease, border-color 150ms ease;
  text-decoration: none;
  color: inherit;
}

.dash-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgb(0 0 0 / 0.08);
  border-color: rgb(153 246 228);
}

.dash-card-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
  border-radius: 0.625rem;
}

.dash-card-arrow {
  position: absolute;
  top: 1.25rem;
  right: 1.25rem;
  color: rgb(203 213 225); /* slate-300 */
  font-size: 1rem;
  transition: color 150ms ease, transform 150ms ease;
}

.dash-card:hover .dash-card-arrow {
  color: rgb(20 184 166); /* teal-500 */
  transform: translateX(2px);
}
</style>