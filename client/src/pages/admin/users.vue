<template>
  <div v-if="userStore.isAdmin">
    <div class="mb-6 flex items-start justify-between">
      <div>
        <h1 class="text-2xl font-semibold text-slate-900 tracking-tight">Users</h1>
        <p class="text-slate-500 mt-1 text-sm">View and manage user accounts.</p>
      </div>
      <div class="text-xs text-slate-400 bg-white border border-slate-200 rounded-lg px-3 py-1.5 self-center">
        {{ users.length }} total
      </div>
    </div>

    <Table :columns="columns" :rows="users" rowKey="id">

      <template #cell-username="{ value }">
        <span class="font-medium text-slate-900">{{ value }}</span>
      </template>

      <template #cell-email="{ value }">
        <span class="text-slate-500">{{ value }}</span>
      </template>

      <template #cell-roles="{ row }">
        <div class="flex flex-wrap gap-1.5">
          <span
              v-for="role in row.userRoles"
              :key="role.roleId"
              :class="roleStyle(role.roleName)"
              class="inline-flex items-center px-2 py-0.5 rounded-md text-xs font-semibold tracking-wide"
          >
            {{ role.roleName }}
          </span>
          <span v-if="row.userRoles.length === 0" class="text-slate-300 text-xs">
            —
          </span>
        </div>
      </template>

      <template #cell-actions="{ row }">
        <div class="flex items-center gap-2">
          <button
              @click="handleEdit(row)"
              class="px-3 py-1.5 rounded-lg text-xs font-medium text-slate-600 bg-slate-100 hover:bg-slate-200 transition-colors"
              style="cursor: pointer"
          >
            Edit
          </button>
          <button
              @click="handleDelete(row)"
              class="px-3 py-1.5 rounded-lg text-xs font-medium text-red-500 bg-red-50 hover:bg-red-100 transition-colors"
              style="cursor: pointer"
          >
            Delete
          </button>
        </div>
      </template>
    </Table>
  </div>
</template>

<script setup lang="ts">
import type { UserDto } from "~/models/user";
import { useApiFetch } from "~/composables/useApiFetch";
import { useUserStore } from "~/stores/userStore";
const userStore = useUserStore();

definePageMeta({ layout: "admin" });

const users = await useApiFetch<UserDto[]>("/users");

const columns = [
  { key: "username", label: "Username" },
  { key: "email",    label: "Email" },
  { key: "roles",    label: "Roles" },
  { key: "actions",  label: "Actions" },
];

const roleStyles: Record<string, string> = {
  ADMIN:      "bg-violet-50 text-violet-700",
  CLINICIAN:  "bg-teal-50 text-teal-700",
  VIEWER:     "bg-slate-100 text-slate-600",
};

function roleStyle(name: string) {
  return roleStyles[name] ?? "bg-slate-100 text-slate-600";
}

function handleEdit(row: UserDto) {
  console.log("edit", row);
}

function handleDelete(row: UserDto) {
  console.log("delete", row);
}
</script>