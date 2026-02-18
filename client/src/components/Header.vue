<template>
  <header class="w-full bg-white border-b border-gray-200 shadow-sm">
    <div class="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between">
      <div class="flex items-center gap-3">
        <slot name="logo">
          <div class="w-8 h-8 bg-green-600 rounded-md"></div>
        </slot>
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
                  class="flex items-center gap-2 text-sm text-gray-700 hover:text-gray-900 font-medium"
                  aria-haspopup="menu"
                  :aria-expanded="open"
                  style="cursor: pointer"
              >
                Welcome, {{ userStore.user?.username }}
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
                  class="w-full text-left px-3 py-2 text-sm rounded-md hover:bg-gray-100"
                  role="menuitem"
                  @click="goDashboard(close)"
                  style="cursor: pointer"
              >
                Dashboard
              </button>
              <button
                  type="button"
                  class="w-full text-left px-3 py-2 text-sm rounded-md hover:bg-gray-100 text-red-600"
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
          <Button
              variant="success"
              @click="navigateTo('/login')"
              hover
              rounded
          >
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

const handleLogout = async (close: () => void): Promise<void> => {
  close();
  await userStore.logout();
};
</script>

<style scoped>

</style>
