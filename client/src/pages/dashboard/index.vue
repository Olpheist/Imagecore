<template>
  <div class="px-6 py-8 lg:px-8">
    <div class="mx-auto max-w-7xl">
      <div>
        <div class="border-b border-slate-200 px-6 py-6 lg:px-8">
          <div class="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <h1 class="text-3xl font-semibold tracking-tight text-slate-900">
                Dashboard
              </h1>
              <p class="mt-2 text-sm text-slate-600">
                Quickly access your profile, tools, uploads, and imaging features.
              </p>
            </div>

            <div class="flex w-full flex-col gap-3 lg:w-auto lg:min-w-105">
              <div class="relative">
                <input
                    v-model="search"
                    type="text"
                    placeholder="Search dashboard cards..."
                    class="w-full rounded-2xl border border-slate-300 bg-white px-4 py-3 pl-11 text-sm text-slate-900 shadow-sm outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                />
                <svg
                    class="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                >
                  <path
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="2"
                      d="M21 21l-4.35-4.35M10.5 18a7.5 7.5 0 100-15 7.5 7.5 0 000 15z"
                  />
                </svg>
              </div>

              <div class="flex flex-wrap gap-2">
                <button
                    v-for="option in filterOptions"
                    :key="option"
                    type="button"
                    class="rounded-full border px-3 py-1.5 text-xs font-medium transition cursor-pointer"
                    :class="selectedFilter === option
                    ? 'border-slate-900 bg-slate-900 text-white'
                    : 'border-slate-300 bg-white text-slate-600 hover:border-slate-400 hover:text-slate-900'"
                    @click="selectedFilter = option"
                >
                  {{ option }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <div class="px-6 py-6 lg:px-8">
          <div class="mb-5 flex items-center justify-between">
            <p class="text-sm text-slate-500">
              Showing {{ filteredCards.length }} {{ filteredCards.length === 1 ? "card" : "cards" }}
            </p>

            <button
                v-if="search || selectedFilter !== 'All'"
                type="button"
                class="text-sm font-medium text-slate-600 transition hover:text-slate-900"
                @click="resetFilters"
            >
              Clear filters
            </button>
          </div>
          <div
              v-if="filteredCards.length > 0"
              class="grid gap-4 md:grid-cols-2 xl:grid-cols-3"
          >
            <Card
                v-for="(card, index) in filteredCards"
                :key="card.title"
                variant="outlined"
                hover
                rounded
                :hoverBorderClass="card.hoverBorderClass"
                class="group relative flex min-h-55 cursor-pointer flex-col overflow-hidden border border-slate-200 border-t-4 border-t-slate-800/60"
                :class="[
                  index % 2 === 0
                    ? 'bg-white'
                    : 'bg-linear-to-br from-slate-100 via-slate-100 to-slate-200/80'
                ]"
                @click="navigateTo(card.to)"
            >
              <div
                  class="inline-flex h-11 w-11 items-center justify-center rounded-2xl text-current"
                  :class="card.iconWrapClass"
              >
                <span v-html="card.icon"></span>
              </div>

              <div class="mt-4">
                <div class="text-base font-semibold text-slate-900">
                  {{ card.title }}
                </div>
                <div class="mt-1 text-sm leading-6 text-slate-500">
                  {{ card.description }}
                </div>
              </div>

              <div class="mt-auto flex items-center justify-between pt-5">
                <span
                    class="rounded-full px-2.5 py-1 text-xs font-medium text-slate-600"
                    :class="[index % 2 === 0 ? 'bg-slate-100' : 'bg-white']"
                >
                  {{ card.category }}
                </span>

                <div
                    class="text-slate-300 transition-all duration-200 group-hover:translate-x-0.5"
                    :class="card.arrowClass"
                >
                  →
                </div>
              </div>
            </Card>
          </div>
          <div
              v-else
              class="rounded-2xl border border-dashed border-slate-300 bg-slate-50 px-6 py-12 text-center"
          >
            <h2 class="text-lg font-semibold text-slate-900">
              No matching cards
            </h2>
            <p class="mt-2 text-sm text-slate-500">
              Try a different search term or clear the current filter.
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useUserStore } from "~/stores/user";

useHead({
  title: "Dashboard",
});

type CardCategory = "Admin" | "Imaging" | "Account" | "Tools";

type DashboardCard = {
  title: string;
  description: string;
  to: string;
  category: CardCategory;
  hoverBorderClass: string;
  iconWrapClass: string;
  arrowClass: string;
  icon: string;
};

const makeCard = (card: DashboardCard): DashboardCard => card;

const userStore = useUserStore();
const isClinician = userStore.hasRole("CLINICIAN");
const isResearcher = userStore.hasRole("RESEARCHER");

const search = ref("");
const selectedFilter = ref<CardCategory | "All">("All");

const baseOptions: Array<CardCategory | "All"> = [
  "All",
  "Imaging",
  "Tools",
  "Account"
];

if (userStore.isAdmin) {
  baseOptions.push("Admin");
}

const filterOptions: ReadonlyArray<CardCategory | "All"> = baseOptions;

const allCards = computed<DashboardCard[]>(() => {
  const cards: DashboardCard[] = [
    ...(userStore.isAdmin
        ? [
          makeCard({
            title: "Admin Center",
            description: "Manage users, logs, and system configuration.",
            to: "/dashboard/admin",
            category: "Admin",
            hoverBorderClass: "hover:border-red-300",
            iconWrapClass: "bg-red-50 text-red-600",
            arrowClass: "group-hover:text-red-500",
            icon: `
              <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 11c0-1.657 1.343-3 3-3h3v10h-6v-7zM6 8h3c1.657 0 3 1.343 3 3v7H6V8z" />
              </svg>
            `,
          }),
        ]
        : []),
    ...(isClinician
        ? [
          makeCard({
            title: "DICOM Upload",
            description: "Upload medical imaging files securely.",
            to: "/dashboard/dicom-upload",
            category: "Imaging",
            hoverBorderClass: "hover:border-teal-300",
            iconWrapClass: "bg-teal-50 text-teal-600",
            arrowClass: "group-hover:text-teal-500",
            icon: `
              <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1M12 4v12m0 0l-4-4m4 4l4-4" />
              </svg>
            `,
          }),
        ]
        : []),
    makeCard({
      title: "User Profile",
      description: "View and manage your account details.",
      to: "/dashboard/profile",
      category: "Account",
      hoverBorderClass: "hover:border-blue-300",
      iconWrapClass: "bg-blue-50 text-blue-600",
      arrowClass: "group-hover:text-blue-500",
      icon: `
        <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5.121 17.804A9 9 0 1118.88 17.8M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
      `,
    }),
    makeCard({
      title: "Tool Analytics",
      description: "View usage statistics for your tools.",
      to: "/dashboard/analytics",
      category: "Tools",
      hoverBorderClass: "hover:border-indigo-300",
      iconWrapClass: "bg-indigo-50 text-indigo-600",
      arrowClass: "group-hover:text-indigo-500",
      icon: `
        <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
        </svg>
      `,
    }),
    makeCard({
      title: "Available Tools",
      description: "Browse platform tools and capabilities.",
      to: "/dashboard/tools",
      category: "Tools",
      hoverBorderClass: "hover:border-violet-300",
      iconWrapClass: "bg-violet-50 text-violet-600",
      arrowClass: "group-hover:text-violet-500",
      icon: `
        <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317a1 1 0 011.35-.447l.994.497a1 1 0 00.894 0l.994-.497a1 1 0 011.35.447l.518.966a1 1 0 00.75.52l1.084.156a1 1 0 01.847.99v1.094a1 1 0 00.292.707l.78.78a1 1 0 010 1.414l-.78.78a1 1 0 00-.292.707v1.094a1 1 0 01-.847.99l-1.084.156a1 1 0 00-.75.52l-.518.966a1 1 0 01-1.35.447l-.994-.497a1 1 0 00-.894 0l-.994.497a1 1 0 01-1.35-.447l-.518-.966a1 1 0 00-.75-.52l-1.084-.156a1 1 0 01-.847-.99v-1.094a1 1 0 00-.292-.707l-.78-.78a1 1 0 010-1.414l.78-.78a1 1 0 00.292-.707V7.966a1 1 0 01.847-.99l1.084-.156a1 1 0 00.75-.52l.518-.966z" />
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
      `,
    }),
    ...((isClinician || isResearcher)
        ? [
          makeCard({
            title: "My DICOM Images",
            description: "View and manage your uploaded imaging files.",
            to: "/dashboard/catalog",
            category: "Imaging",
            hoverBorderClass: "hover:border-emerald-300",
            iconWrapClass: "bg-emerald-50 text-emerald-600",
            arrowClass: "group-hover:text-emerald-500",
            icon: `
              <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 10h16M4 14h16M4 18h16" />
              </svg>
            `,
          }),
        ]
        : []),
    makeCard({
      title: "DICOM Viewer",
      description: "Open and inspect medical imaging studies.",
      to: "/dashboard/dicom",
      category: "Imaging",
      hoverBorderClass: "hover:border-amber-300",
      iconWrapClass: "bg-amber-50 text-amber-600",
      arrowClass: "group-hover:text-amber-500",
      icon: `
        <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 19h8a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
        </svg>
      `,
    }),
  ];

  return cards;
});

const filteredCards = computed<DashboardCard[]>(() => {
  const query = search.value.trim().toLowerCase();

  return allCards.value.filter((card) => {
    const matchesFilter =
        selectedFilter.value === "All" || card.category === selectedFilter.value;

    const matchesSearch =
        query.length === 0 ||
        card.title.toLowerCase().includes(query) ||
        card.description.toLowerCase().includes(query) ||
        card.category.toLowerCase().includes(query);

    return matchesFilter && matchesSearch;
  });
});

const resetFilters = (): void => {
  search.value = "";
  selectedFilter.value = "All";
};
</script>