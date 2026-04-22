<template>
  <div>
    <div class="max-w-5xl mx-auto px-6 py-10">
      <div class="mb-8">
        <h1 class="text-3xl font-semibold tracking-tight text-slate-900">
          Profile
        </h1>
        <p class="mt-2 text-sm text-slate-600">
          View your account information.
        </p>
      </div>

      <div class="rounded-3xl border border-slate-300 bg-white shadow-md overflow-hidden">
        <div class="border-b border-slate-200 bg-linear-to-r from-slate-50 to-white px-8 py-6">
          <div class="flex items-center gap-4">
            <div class="flex h-14 w-14 items-center justify-center rounded-2xl bg-violet-500/80 text-lg font-semibold text-white">
              {{ initials }}
            </div>
            <div>
              <h2 class="text-xl font-semibold text-slate-900">
                {{ user?.username || "User" }}
              </h2>
              <p class="text-sm text-slate-500">
                {{ user?.email || "No email available" }}
              </p>
            </div>
          </div>
        </div>

        <div class="px-8 py-8">
          <div class="grid grid-cols-1 gap-6 md:grid-cols-2">
            <div class="space-y-2">
              <label class="text-sm font-medium text-slate-600">
                Username
              </label>
              <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-slate-900">
                {{ user?.username || "—" }}
              </div>
            </div>

            <div class="space-y-2">
              <label class="text-sm font-medium text-slate-600">
                Email
              </label>
              <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-slate-900 break-all">
                {{ user?.email || "—" }}
              </div>
            </div>

            <div class="space-y-2">
              <label class="text-sm font-medium text-slate-600">
                Roles
              </label>
              <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-slate-900">
                {{ rolesDisplay }}
              </div>
            </div>

            <div class="space-y-2">
              <label class="text-sm font-medium text-slate-600">
                Created
              </label>
              <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-slate-900">
                {{ user?.createdAt ? formatDate(user.createdAt) : "—" }}
              </div>
            </div>
          </div>
          <button
              class="text-blue-600 hover:underline font-medium mt-2"
              style="cursor: pointer;"
              @click="onForgotPassword"
          >
            Reset Password
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from "vue";
import { storeToRefs } from "pinia";
import { useUserStore } from "~/stores/user";
import {navigateTo} from "nuxt/app";

const userStore = useUserStore();
const { user } = storeToRefs(userStore);

onMounted(async () => {
  if (!user.value) {
    await userStore.fetchMe();
  }
});

const rolesDisplay = computed(() => {
  const roles = user.value?.userRoles?.map((r) => r.roleName) ?? [];
  return roles.length ? roles.join(", ") : "None";
});

const initials = computed(() => {
  const username = user.value?.username?.trim() ?? "";
  if (!username) return "U";
  return username.slice(0, 1).toUpperCase();
});

const formatDate = (iso: string): string => {
  try {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return iso;
    return d.toLocaleString();
  } catch {
    return iso;
  }
};

const onForgotPassword = async (): Promise<void> => {
  await navigateTo("/forgot-password");
};

</script>

<style scoped>
</style>