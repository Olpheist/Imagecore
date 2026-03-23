<template>
  <Header />
  <div v-if="userStore.isAdmin" class="min-h-screen flex flex-col bg-slate-200">

    <div class="flex flex-1">
      <aside class="w-64 bg-slate-100 border-r border-slate-100 flex flex-col">
        <div class="px-5 py-5 border-b border-slate-100">
          <div class="text-xs font-semibold text-slate-400 uppercase tracking-widest mb-0.5">Admin</div>
          <div class="text-base font-semibold text-slate-800">Management Console</div>
        </div>

        <nav class="p-3 space-y-0.5 flex-1">
          <NuxtLink class="nav-link" to="/admin">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                    d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"/>
            </svg>
            Dashboard
          </NuxtLink>
          <NuxtLink class="nav-link" to="/admin/users">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                    d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z"/>
            </svg>
            Users
          </NuxtLink>
          <NuxtLink class="nav-link" to="/admin/logs">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                    d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
            </svg>
            Logs
          </NuxtLink>
        </nav>
      </aside>

      <main class="flex-1 p-8">
        <div class="max-w-6xl">
          <slot />
        </div>
      </main>
    </div>

  </div>
  <div v-else class="min-h-screen flex items-center justify-center bg-slate-50">
    <div class="bg-white border border-slate-200 rounded-2xl shadow-sm p-10 text-center max-w-md w-full">

      <div class="mx-auto mb-6 flex items-center justify-center w-14 h-14 rounded-full bg-red-50 text-red-600">
        <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M12 9v2m0 4h.01M5.07 19h13.86c1.54 0 2.5-1.67 1.73-3L13.73 4a2 2 0 00-3.46 0L3.34 16c-.77 1.33.19 3 1.73 3z"/>
        </svg>
      </div>

      <h1 class="text-xl font-semibold text-slate-900 mb-2">
        Access Restricted
      </h1>

      <p class="text-sm text-slate-500 mb-6">
        You do not have permission to view the Admin Management Console.
        If you believe this is a mistake, please contact a system administrator.
      </p>

      <NuxtLink
          to="/"
          class="inline-flex items-center justify-center px-4 py-2 rounded-xl bg-slate-900 text-white text-sm font-medium hover:bg-slate-800 transition"
      >
        Go to Home
      </NuxtLink>

    </div>
  </div>
  <Footer />
</template>

<script setup lang="ts">
import { useUserStore } from "~/stores/user";
const userStore = useUserStore();

</script>

<style scoped>
.nav-link {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  padding: 0.6rem 0.75rem;
  border-radius: 0.625rem;
  color: rgb(100 116 139); /* slate-500 */
  text-decoration: none;
  font-size: 0.875rem;
  font-weight: 500;
  transition: background 120ms ease, color 120ms ease;
}

.nav-link:hover {
  background: rgb(248 250 252); /* slate-50 */
  color: rgb(15 23 42); /* slate-900 */
}

.nav-link.router-link-exact-active {
  background: rgb(240 253 250); /* teal-50 */
  color: rgb(15 118 110); /* teal-700 */
  font-weight: 600;
}
</style>