<template>
  <div class="min-h-screen flex flex-col bg-slate-50">
    <Header />

    <div class="flex flex-1">
      <aside class="w-64 bg-white border-r border-slate-100 flex flex-col">
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

    <Footer />
  </div>
</template>

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