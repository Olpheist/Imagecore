<template>
  <div class="max-w-5xl mx-auto px-6 py-8">
    <div class="mb-6">
      <h1 class="text-2xl font-semibold tracking-tight text-slate-900">
        Profile
      </h1>
      <p class="mt-1 text-sm text-slate-500">
        View and manage your account information.
      </p>
    </div>

    <UserProfile :user="user" @billing-updated="updateUser" />
  </div>
</template>

<script setup lang="ts">
import { storeToRefs } from "pinia";
import { useUserStore } from "~/stores/user";

const userStore = useUserStore();
const { user } = storeToRefs(userStore);

const updateUser = async () => {
  await userStore.fetchMe();
};
</script>