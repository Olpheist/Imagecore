<template>
  <header class="relative z-50 w-full border-b border-white/10 bg-linear-to-r from-slate-900 to-slate-800 text-white backdrop-blur">
    <div class="max-w-7xl mx-auto px-6">
      <div class="h-16 flex items-center justify-between">
        <div class="flex items-center gap-3 min-w-0">
          <NuxtLink to="/" class="flex items-center gap-3 group">
            <img
                src="/web-app-manifest-512x512.png"
                alt="ImageCore logo"
                class="w-9 h-9 rounded-lg object-contain shadow-md transition group-hover:scale-105"
            />
            <div class="flex flex-col leading-tight">
              <span class="text-lg font-semibold tracking-tight group-hover:opacity-90">
                ImageCore
              </span>
              <span class="text-[11px] text-white/50">
                Medical Image Analysis
              </span>
            </div>
          </NuxtLink>
        </div>

        <div class="flex items-center gap-3">
          <template v-if="userStore.isLoggedIn">
            <DropdownMenu align="right" widthClass="w-64">
              <template #trigger="{ open }">
                <button
                    type="button"
                    class="group flex items-center gap-3 rounded-2xl border border-white/10 bg-white/5 px-3 py-2 text-sm font-medium transition hover:border-white/20 hover:bg-white/10 cursor-pointer"
                >
                  <div class="flex h-9 w-9 items-center justify-center rounded-full bg-white/12 text-sm font-semibold text-white ring-1 ring-white/10">
                    {{ userStore.user?.username?.[0]?.toUpperCase() }}
                  </div>

                  <div class="hidden sm:flex flex-col items-start leading-tight">
                    <span class="max-w-30 truncate text-sm font-semibold text-white">
                      {{ userStore.user?.username }}
                    </span>
                    <span class="text-[11px] text-white/55">
                      Signed in
                    </span>
                  </div>

                  <svg
                      class="h-4 w-4 text-white/60 transition-transform duration-200 group-hover:text-white/80"
                      :class="{ 'rotate-180': open }"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      viewBox="0 0 24 24"
                  >
                    <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
                  </svg>
                </button>
              </template>

              <template #menu="{ close }">
                <div class="px-3 py-3 border-b border-slate-200/80">
                  <div class="text-xs font-medium uppercase tracking-wide text-slate-500">
                    Account
                  </div>
                  <div class="mt-1 truncate text-sm font-semibold text-slate-900">
                    {{ userStore.user?.username }}
                  </div>
                </div>

                <div class="p-2">
                  <button
                      class="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm font-medium text-red-600 transition hover:bg-red-50 cursor-pointer"
                      @click="handleLogout(close)"
                  >
                    <svg
                        class="h-4 w-4 shrink-0"
                        fill="none"
                        stroke="currentColor"
                        stroke-width="2"
                        viewBox="0 0 24 24"
                    >
                      <path stroke-linecap="round" stroke-linejoin="round" d="M17 16l4-4m0 0l-4-4m4 4H9" />
                      <path stroke-linecap="round" stroke-linejoin="round" d="M13 20H6a2 2 0 01-2-2V6a2 2 0 012-2h7" />
                    </svg>
                    <span>Logout</span>
                  </button>
                </div>
              </template>
            </DropdownMenu>
          </template>

          <template v-else>
            <Button variant="success" @click="navigateTo('/login')" hover rounded>
              Login
            </Button>
          </template>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { navigateTo } from "nuxt/app";
import { useUserStore } from "~/stores/user";
import DropdownMenu from "~/components/DropdownMenu.vue";

const userStore = useUserStore();

const handleLogout = async (close: () => void): Promise<void> => {
  close();
  await userStore.logout();
};
</script>