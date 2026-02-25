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
          <Button
              @click="handleEdit(row)"
              rounded hover
              size="sm"
          >
            Edit
          </Button>
          <Button
              @click="handleDelete(row)"
              variant="danger"
              rounded hover
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
  </div>
</template>

<script setup lang="ts">
import type {UserDto} from "~/models/user";
import { useApiFetch } from "~/composables/useApiFetch";
import { useUserStore } from "~/stores/userStore";
import type {RoleDto} from "~/models/role";
const userStore = useUserStore();

definePageMeta({ layout: "admin" });

const users = await useApiFetch<UserDto[]>("/users");
const roles = await useApiFetch<RoleDto[]>("/roles");

const showEditModal = ref(false);
type EditModalForm = {
  userId: number | null;
  roleIds: number[];
};

const editModalForm = ref<EditModalForm>({
  userId: null,
  roleIds: [],
});

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

}

function handleDelete(row: UserDto) {
  console.log("delete", row);
}
</script>