<template>
  <div class="min-h-screen flex flex-col bg-slate-200 relative overflow-hidden">
    <Header />

    <main class="flex-1 relative z-10 overflow-hidden">
      <div class="absolute inset-0 pointer-events-none">
        <div class="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(15,23,42,0.18),transparent_55%)]"></div>
        <div class="absolute inset-y-0 left-0 w-64 bg-linear-to-r from-slate-400/70 to-transparent"></div>
        <div class="absolute inset-y-0 right-0 w-64 bg-linear-to-l from-slate-400/70 to-transparent"></div>
      </div>

      <div class="max-w-7xl mx-auto w-full px-4 sm:px-6 lg:px-8 py-8 relative">
        <template v-if="background">
          <div class="border border-slate-300/80 bg-white/75 shadow-[0_20px_60px_rgba(15,23,42,0.12)] backdrop-blur-md overflow-hidden">
            <div
                v-if="userStore.isLoggedIn && breadcrumbs.length"
                class="border-b border-slate-200/80 bg-linear-to-r from-slate-50 via-white to-slate-50 px-6 py-4 sm:px-8"
            >
              <nav aria-label="Breadcrumb" class="min-w-0">
                <ol class="flex flex-wrap items-center gap-2 text-sm">
                  <li>
                    <NuxtLink to="/dashboard" class="text-slate-500 hover:text-slate-900 transition">
                      Dashboard
                    </NuxtLink>
                  </li>
                  <template v-for="(crumb, index) in breadcrumbs" :key="crumb.to">
                    <li class="text-slate-300">/</li>
                    <li>
                      <NuxtLink
                          v-if="index !== breadcrumbs.length - 1"
                          :to="crumb.to"
                          class="text-slate-500 hover:text-slate-900 transition"
                      >
                        {{ crumb.label }}
                      </NuxtLink>
                      <span v-else class="text-slate-900 font-semibold">{{ crumb.label }}</span>
                    </li>
                  </template>
                </ol>
              </nav>
            </div>

            <div class="relative">
              <div class="absolute inset-x-0 top-0 h-24 bg-linear-to-b from-slate-50/70 to-transparent pointer-events-none"></div>
              <div class="relative px-6 py-6 sm:px-8 sm:py-8 lg:px-10 lg:py-10">
                <slot />
              </div>
            </div>
          </div>
        </template>

        <template v-else>
          <slot />
        </template>
      </div>
    </main>

    <Footer />
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "nuxt/app";
import { useUserStore } from "~/stores/user";

const route = useRoute();
const userStore = useUserStore();

const background = computed(() => route.meta.layoutBackground !== false);

const formatLabel = (segment: string): string => {
  const customLabels: Record<string, string> = {
    dicom: "DICOM Viewer",
    "dicom-upload": "DICOM Upload",
    profile: "Profile",
    tools: "Tools",
    analytics: "Tool Analytics",
    admin: "Admin Center",
  };

  if (customLabels[segment]) return customLabels[segment];

  return segment
      .replace(/-/g, " ")
      .replace(/\b\w/g, (char) => char.toUpperCase());
};

const breadcrumbs = computed(() => {
  const segments = route.path.split("/").filter(Boolean);
  if (segments.length === 0 || segments[0] !== "dashboard") return [];
  const childSegments = segments.slice(1);
  return childSegments.map((segment, index) => ({
    label: formatLabel(segment),
    to: "/dashboard/" + childSegments.slice(0, index + 1).join("/"),
  }));
});
</script>