<template>
  <Card variant="elevated" rounded class="w-full max-w-2xl">
    <div class="space-y-6">
      <div>
        <h2 class="text-xl font-semibold">Profile</h2>
        <p class="text-sm text-gray-500 mt-1">
          View your account information.
        </p>
      </div>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div class="space-y-1">
          <label class="text-sm text-gray-600">Username</label>
          <Input
              :model-value="user?.username ?? ''"
              disabled
          />
        </div>
        <div class="space-y-1">
          <label class="text-sm text-gray-600">Email</label>
          <Input
              :model-value="user?.email ?? ''"
              disabled
          />
        </div>
        <div class="space-y-1">
          <label class="text-sm text-gray-600">Roles</label>
          <Input
              :model-value="rolesDisplay"
              disabled
          />
        </div>
      </div>

      <div v-if="user?.createdAt" class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div v-if="user?.createdAt" class="space-y-1">
          <label class="text-sm text-gray-600">Created</label>
          <Input
              :model-value="formatDate(user.createdAt)"
              disabled
          />
        </div>
      </div>
    </div>
  </Card>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { UserDto } from "~/models/user";

const props = defineProps<{
  user: UserDto | null;
}>();

const rolesDisplay = computed(() => {
  const roles = props.user?.userRoles?.map((r) => r.roleName) ?? [];
  return roles.length ? roles.join(", ") : "None";
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
</script>

<style scoped>

</style>
