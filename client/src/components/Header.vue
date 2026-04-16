<template>
  <header class="w-full bg-linear-to-r from-slate-900 to-slate-800 text-white border-b border-white/10 backdrop-blur">
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
            <DropdownMenu align="right" widthClass="w-44">
              <template #trigger="{ open }">
                <button
                    type="button"
                    class="flex items-center gap-2 text-sm font-medium bg-white/5 hover:bg-white/10 px-3 py-1.5 rounded-xl border border-white/10 transition cursor-pointer"
                >
                  <div class="w-7 h-7 rounded-full bg-violet-500/80 flex items-center justify-center text-xs font-semibold">
                    {{ userStore.user?.username?.[0]?.toUpperCase() }}
                  </div>
                  <span class="hidden sm:inline text-white/80">
                    {{ userStore.user?.username }}
                  </span>
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
                    class="w-full text-left px-3 py-2 text-sm text-red-600 hover:bg-slate-100 cursor-pointer"
                    @click="handleLogout(close)"
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

      <!-- BREADCRUMBS -->
      <div class="h-11 flex items-center bg-slate-800/60 border-t border-white/10 rounded-b-xl px-2" v-if="userStore.isLoggedIn">
        <nav aria-label="Breadcrumb" class="min-w-0">
          <ol class="flex items-center gap-2 text-sm">
            <li>
              <NuxtLink
                  to="/dashboard"
                  class="text-white/50 hover:text-white transition"
              >
                Dashboard
              </NuxtLink>
            </li>
            <template v-for="(crumb, index) in breadcrumbs" :key="crumb.to">
              <li class="text-white/20">/</li>
              <li>
                <NuxtLink
                    v-if="index !== breadcrumbs.length - 1"
                    :to="crumb.to"
                    class="text-white/50 hover:text-white transition"
                >
                  {{ crumb.label }}
                </NuxtLink>
                <span
                    v-else
                    class="text-white font-medium"
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
    analytics: "Tool Analytics",
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