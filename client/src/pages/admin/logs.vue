<template>
  <div>
    <div class="mb-6 flex items-start justify-between">
      <div>
        <h1 class="text-2xl font-semibold text-slate-900 tracking-tight">Logs</h1>
        <p class="text-slate-500 mt-1 text-sm">Inspect recent application logs.</p>
      </div>
    </div>
    <Card variant="outlined" :rounded="true" class="p-4 mb-4" elevated>
      <div class="grid grid-cols-1 md:grid-cols-12 gap-3 items-end">
        <div class="md:col-span-3">
          <label class="block text-xs font-medium text-slate-600 mb-1">Username</label>
          <Input
              v-model="filters.username"
              placeholder="Username"
          />
        </div>
        <div class="md:col-span-2">
          <label class="block text-xs font-medium text-slate-600 mb-1">Level</label>
          <DropdownMenu widthClass="w-44">
            <template #trigger>
              <Button
                  variant="secondary"
                  size="md"
                  :rounded="true"
                  :hover="false"
                  class="w-full justify-between border border-gray-300 bg-white text-gray-900"
              >
                <span
                    :class="levelStyle(filters.logLevel || 'ANY')"
                    class="inline-flex items-center px-2 py-0.5 rounded-md text-xs font-semibold tracking-wide"
                >
                  {{ filters.logLevel || 'Any' }}
                </span>
                <span class="text-gray-500 ml-2">▾</span>
              </Button>
            </template>
            <template #menu="{ close }">
              <button
                  type="button"
                  class="w-full text-left px-3 py-2 text-sm rounded-md hover:bg-gray-100 flex items-center"
                  @click="filters.logLevel = ''; close();"
                  style="cursor: pointer"
              >
                <span
                    :class="levelStyle('ANY')"
                    class="inline-flex items-center px-2 py-0.5 rounded-md text-xs font-semibold tracking-wide"
                >
                  Any
                </span>
              </button>
              <button
                  v-for="level in logLevels"
                  :key="level"
                  type="button"
                  class="w-full text-left px-3 py-2 text-sm rounded-md hover:bg-gray-100 flex items-center"
                  @click="filters.logLevel = level; close();"
                  style="cursor: pointer"
              >
                <span
                    :class="levelStyle(level)"
                    class="inline-flex items-center px-2 py-0.5 rounded-md text-xs font-semibold tracking-wide"
                >
                  {{ level }}
                </span>
              </button>
            </template>
          </DropdownMenu>
        </div>
        <div class="md:col-span-3">
          <label class="block text-xs font-medium text-slate-600 mb-1">From</label>
          <Input
              v-model="filters.from"
              type="datetime-local"
          />
        </div>
        <div class="md:col-span-3">
          <label class="block text-xs font-medium text-slate-600 mb-1">To</label>
          <Input
              v-model="filters.to"
              type="datetime-local"
          />
        </div>
        <div class="md:col-span-1 flex gap-2">
          <Button
              variant="success"
              size="md"
              :rounded="true"
              class="w-full"
              @click="fetchLogs()"
          >
            Apply
          </Button>
        </div>
      </div>
    </Card>
    <Table :columns="columns" :rows="pagedLogs" rowKey="id">
      <template #cell-logLevel="{ value }">
        <span :class="levelStyle(value)" class="inline-flex items-center px-2 py-0.5 rounded-md text-xs font-semibold tracking-wide">
          {{ value }}
        </span>
      </template>
      <template #cell-username="{ value }">
        <span class="font-medium text-slate-900">{{ value ?? '—' }}</span>
      </template>
      <template #cell-message="{ value }">
        <span class="text-slate-600 text-sm">{{ value }}</span>
      </template>
      <template #cell-createdAt="{ value }">
        <span class="text-slate-400 text-xs">{{ formatDate(value) }}</span>
      </template>
    </Table>
    <Pagination
        v-model:currentPage="currentPage"
        :totalNum="logs.length"
        :perPage="perPage"
    />
    <Error class="mt-2" :error="error" dismissible @close="error = null" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { useApiFetch } from "~/composables/useApiFetch";
import type { ApiError } from "~/models/error";
import type { LogDto } from "~/models/log";

definePageMeta({ layout: "admin" });

const error = ref<ApiError | null>(null);
const logs = ref<LogDto[]>([]);

function toLocalDatetime(date: Date) {
  const offset = date.getTimezoneOffset();
  const local = new Date(date.getTime() - offset * 60000);
  return local.toISOString().slice(0,16);
}

const now = new Date();
now.setHours(23, 59, 59, 999);

const yesterday = new Date();
yesterday.setDate(yesterday.getDate() - 1);
yesterday.setHours(0,0,0,0);

const filters = ref({
  username: "",
  logLevel: "",
  from: toLocalDatetime(yesterday),
  to: toLocalDatetime(now),
});

const currentPage = ref(1);
const perPage = ref(50);

const pagedLogs = computed(() => {
  const start = (currentPage.value - 1) * perPage.value;
  return logs.value.slice(start, start + perPage.value);
});

const columns = [
  { key: "createdAt", label: "Time" },
  { key: "logLevel", label: "Level" },
  { key: "username", label: "User" },
  { key: "message", label: "Message" },
];

const levelStyles: Record<string,string> = {
  ANY: "bg-slate-100 text-slate-700",
  INFO: "bg-blue-50 text-blue-700",
  WARN: "bg-amber-50 text-amber-700",
  ERROR: "bg-red-50 text-red-700",
  DEBUG: "bg-slate-100 text-slate-600",
};

const logLevels = ["INFO","WARN","ERROR","DEBUG"];

function levelStyle(level: string) {
  return levelStyles[level] ?? "bg-slate-100 text-slate-600";
}

function formatDate(value: string) {
  return new Date(value).toLocaleString();
}

async function fetchLogs() {
  try {
    const params = new URLSearchParams();

    if (filters.value.username) params.set("username", filters.value.username);
    if (filters.value.logLevel) params.set("logLevel", filters.value.logLevel);

    params.set("from", new Date(filters.value.from).toISOString());
    params.set("to", new Date(filters.value.to).toISOString());

    const query = params.toString() ? `?${params.toString()}` : "";

    logs.value = await useApiFetch<LogDto[]>(`/logs${query}`) ?? [];
    currentPage.value = 1;
  } catch (e: unknown) {
    error.value = e as ApiError;
  }
}

await fetchLogs();
</script>