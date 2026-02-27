<template>
  <header class="w-full bg-slate-900 text-white shadow-sm">
    <div class="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between">
      <div class="flex items-center gap-3">
        <NuxtLink to="/">
          <slot name="logo">
            <div class="w-8 h-8 bg-emerald-600 rounded-md"></div>
          </slot>
        </NuxtLink>
        <NuxtLink
            to="/"
            class="text-xl font-semibold tracking-tight hover:opacity-80 transition"
        >
          ImageCore
        </NuxtLink>
      </div>

      <div class="flex items-center gap-4">
        <template v-if="userStore.isLoggedIn">
          <DropdownMenu align="right" widthClass="w-44">
            <template #trigger="{ open }">
              <button
                  type="button"
                  class="flex items-center gap-2 text-sm font-medium text-white/90 hover:text-white hover:bg-white/10 px-3 py-2 rounded-md transition"
                  aria-haspopup="menu"
                  :aria-expanded="open"
                  style="cursor: pointer"
              >
                <span class="text-white/70">Welcome,</span>
                <span class="text-white">{{ userStore.user?.username }}</span>

                <svg
                    class="w-4 h-4 transition-transform duration-200"
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
              <button
                  type="button"
                  class="w-full text-left px-3 py-2 text-sm text-slate-900 hover:bg-slate-100"
                  role="menuitem"
                  @click="goDashboard(close)"
                  style="cursor: pointer"
              >
                Dashboard
              </button>

              <button
                  v-if="userStore.isAdmin"
                  type="button"
                  class="w-full text-left px-3 py-2 text-sm text-slate-900 hover:bg-slate-100"
                  role="menuitem"
                  @click="goAdminCenter(close)"
                  style="cursor: pointer"
              >
                Admin Center
              </button>

              <button
                  type="button"
                  class="w-full text-left px-3 py-2 text-sm text-red-600 hover:bg-slate-100"
                  role="menuitem"
                  @click="handleLogout(close)"
                  style="cursor: pointer"
              >
                Logout
              </button>
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
  </header>
</template>

<script setup lang="ts">
import { navigateTo } from "nuxt/app";
import { useUserStore } from "~/stores/userStore";
import DropdownMenu from "~/components/DropdownMenu.vue";

const userStore = useUserStore();

const goDashboard = async (close: () => void): Promise<void> => {
  close();
  await navigateTo("/dashboard");
};

const goAdminCenter = async (close: () => void): Promise<void> => {
  close();
  await navigateTo("/admin");
};

const handleLogout = async (close: () => void): Promise<void> => {
  close();
  await userStore.logout();
};
</script>

<style scoped>

</style>
