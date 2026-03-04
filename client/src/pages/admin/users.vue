<template>
  <div>
    <div class="mb-6 flex items-start justify-between">
      <div>
        <h1 class="text-2xl font-semibold text-slate-900 tracking-tight">Users</h1>
        <p class="text-slate-500 mt-1 text-sm">View and manage user accounts.</p>
      </div>
      <div class="text-xs text-slate-700 bg-white border border-slate-200 rounded-lg px-3 py-1.5 self-center">
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
          <Button
              @click="handleEdit(row)"
              rounded hover
              size="sm"
          >
            Edit
          </Button>
          <Button
              @click="openDeleteModal(row)"
              variant="danger"
              rounded
              hover
              size="sm"
          >
            Delete
          </Button>
        </div>
      </template>
    </Table>
    <Modal v-model="showEditModal" title="Edit User">
      <div class="space-y-3">
        <p class="text-sm text-slate-500">Roles</p>

        <div class="flex flex-wrap gap-2">
          <label
              v-for="r in roles"
              :key="r.id"
              class="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm"
              style="cursor: pointer"
          >
            <input
                type="checkbox"
                class="h-4 w-4"
                :value="r.id"
                v-model="editModalForm.roleIds"
            />
            <span :class="roleStyle(r.name)" class="rounded-md px-2 py-0.5 text-xs font-semibold">
          {{ r.name }}
        </span>
          </label>
        </div>
      </div>
      <template #footer>
        <div class="flex gap-2">
          <Button
              variant="danger"
              rounded
              hover
              @click="showEditModal = false"
          >
            Cancel
          </Button>
          <Button
              variant="primary"
              rounded
              hover
              @click="saveRoles"
          >
            Save
          </Button>
        </div>
      </template>
    </Modal>
    <Modal v-model="showDeleteModal" title="Delete User">
      <div class="space-y-4">
        <p class="text-sm text-slate-600">
          Are you sure you want to delete this user?
        </p>

        <div v-if="userToDelete" class="text-sm bg-slate-50 p-3 rounded border">
          <div><span class="font-semibold">Username:</span> {{ userToDelete.username }}</div>
          <div><span class="font-semibold">Email:</span> {{ userToDelete.email }}</div>
        </div>

        <p class="text-sm text-red-600">
          This action cannot be undone.
        </p>

        <div class="flex justify-end gap-3 pt-2">
          <Button
              variant="primary"
              @click="closeDeleteModal"
              hover rounded
          >
            Cancel
          </Button>
          <Button
              variant="danger"
              @click="confirmDelete"
              :disabled="!userToDelete"
              hover rounded
          >
            Delete User
          </Button>
        </div>
      </div>
    </Modal>
    <Error class="mt-2" :error="error" dismissible @close="error = null" />
  </div>
</template>

<script setup lang="ts">
import type {UserDto} from "~/models/user";
import { useApiFetch } from "~/composables/useApiFetch";
import type {RoleDto} from "~/models/role";
import {ref} from "vue";
import type {ApiError} from "~/models/error";

definePageMeta({ layout: "admin" });

const users = ref(await useApiFetch<UserDto[]>("/users") ?? []);
const roles = ref(await useApiFetch<RoleDto[]>("/roles") ?? []);
const error = ref<ApiError | null>(null);

const showEditModal = ref(false);
type EditModalForm = {
  userId: number | null;
  roleIds: number[];
};
const editModalForm = ref<EditModalForm>({
  userId: null,
  roleIds: [],
});

const showDeleteModal = ref(false);
const userToDelete = ref<UserDto | null>(null);

const columns = [
  { key: "username", label: "Username" },
  { key: "email",    label: "Email" },
  { key: "roles",    label: "Roles" },
  { key: "actions",  label: "Actions" },
];

const roleStyles: Record<string, string> = {
  ADMIN:      "bg-violet-50 text-violet-700",
  CLINICIAN:  "bg-teal-50 text-teal-700",
  RESEARCHER: "bg-blue-50 text-blue-700",
  TECHNICIAN: "bg-amber-50 text-amber-700",
  PATIENT:    "bg-emerald-50 text-emerald-700",
};

function roleStyle(name: string) {
  return roleStyles[name] ?? "bg-slate-100 text-slate-600";
}

function handleEdit(row: UserDto) {
  showEditModal.value = true;

  editModalForm.value = {
    userId: row.id,
    roleIds: row.userRoles.map((ur) => ur.roleId),
  };
}

async function saveRoles(): Promise<void> {
  try {
    const id = editModalForm.value.userId;
    const ids = editModalForm.value.roleIds;

    const updated = await useApiFetch<UserDto>(`/users/${id}/roles`, {
      method: "PUT",
      body: { roleIds: ids }
    });

    const idx = users.value.findIndex((u) => u.id === updated.id);
    if (idx !== -1) {
      // replace the row so it updates immediately
      users.value[idx] = updated;
    }
  } catch (e: unknown) {
    error.value = e as ApiError;
  } finally {
    showEditModal.value = false;
  }
}

function openDeleteModal(row: UserDto): void {
  userToDelete.value = row;
  showDeleteModal.value = true;
}

function closeDeleteModal(): void {
  showDeleteModal.value = false;
  userToDelete.value = null;
}

async function confirmDelete(): Promise<void> {
  if (!userToDelete.value) return;

  try {
    const id = userToDelete.value.id;

    await useApiFetch<void>(`/users/${id}`, {
      method: "DELETE",
    });

    users.value = users.value.filter((u) => u.id !== id);
  } catch (e: unknown) {
    error.value = e as ApiError;
  } finally {
    closeDeleteModal();
  }
}
</script>