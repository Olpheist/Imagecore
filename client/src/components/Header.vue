<template>
  <header class="w-full bg-slate-900 text-white shadow-sm border-b border-white/10">
    <div class="max-w-7xl mx-auto px-6">
      <div class="h-16 flex items-center justify-between">
        <div class="flex items-center gap-3 min-w-0">
          <NuxtLink to="/">
            <slot name="logo">
              <img
                  src="/web-app-manifest-512x512.png"
                  alt="ImageCore logo"
                  class="w-8 h-8 object-contain rounded transition hover:brightness-75"
              />
            </slot>
          </NuxtLink>

          <div class="flex min-w-0 flex-col">
            <NuxtLink
                to="/"
                class="text-xl font-semibold tracking-tight hover:opacity-80 transition leading-tight"
            >
              ImageCore
            </NuxtLink>
            <span class="text-xs text-white/50 leading-tight">
              Medical Image Analysis Platform
            </span>
          </div>
        </div>

        <div class="flex items-center gap-4">
          <template v-if="userStore.isLoggedIn">
            <DropdownMenu align="right" widthClass="w-44">
              <template #trigger="{ open }">
                <button
                    type="button"
                    class="flex items-center gap-2 text-sm font-medium text-white/90 hover:text-white hover:bg-white/10 px-3 py-2 rounded-lg transition"
                    aria-haspopup="menu"
                    :aria-expanded="open"
                    style="cursor: pointer"
                >
                  <span class="text-white/60">Welcome,</span>
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

      <div
          class="h-11 flex items-center border-t border-white/10"
      >
        <nav aria-label="Breadcrumb" class="min-w-0">
          <ol class="flex flex-wrap items-center gap-2 text-sm">
            <li>
              <NuxtLink
                  to="/dashboard"
                  class="text-white/60 hover:text-white transition font-medium"
              >
                Dashboard
              </NuxtLink>
            </li>

            <template v-for="(crumb, index) in breadcrumbs" :key="crumb.to">
              <li class="text-white/30">/</li>
              <li>
                <NuxtLink
                    v-if="index !== breadcrumbs.length - 1"
                    :to="crumb.to"
                    class="text-white/60 hover:text-white transition font-medium"
                >
                  {{ crumb.label }}
                </NuxtLink>

                <span
                    v-else
                    class="text-white font-semibold"
                    aria-current="page"
                >
                  {{ crumb.label }}
                </span>
              </li>
            </template>
          </ol>
        </nav>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { navigateTo, useRoute } from "nuxt/app";
import { useUserStore } from "~/stores/user";
import DropdownMenu from "~/components/DropdownMenu.vue";

const userStore = useUserStore();
const route = useRoute();

const formatLabel = (segment: string): string => {
  const customLabels: Record<string, string> = {
    dicom: "DICOM Viewer",
    "dicom-upload": "DICOM Upload",
    profile: "Profile",
    tools: "Tools",
    admin: "Admin Center",
  };

  if (customLabels[segment]) {
    return customLabels[segment];
  }

  return segment
      .replace(/-/g, " ")
      .replace(/\b\w/g, (char) => char.toUpperCase());
};

const breadcrumbs = computed(() => {
  const segments = route.path.split("/").filter(Boolean);

  if (segments.length === 0) {
    return [];
  }

  segments.shift();

  return segments.map((segment, index) => ({
    label: formatLabel(segment),
    to: "/dashboard/" + segments.slice(0, index + 1).join("/"),
  }));
});

const handleLogout = async (close: () => void): Promise<void> => {
  close();
  await userStore.logout();
};
</script>