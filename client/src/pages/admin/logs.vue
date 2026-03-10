<template>
  <div>
    <div class="mb-6 flex items-start justify-between">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight text-slate-900">Logs</h1>
        <p class="mt-1 text-sm text-slate-500">Inspect recent application logs.</p>
      </div>
      <div class="self-center rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-700">
        {{ totalLogs }} total
      </div>
    </div>
    <Card variant="outlined" :rounded="true" class="mb-4 p-4">
      <div class="grid grid-cols-1 gap-3 md:grid-cols-12 md:items-end">
        <div class="md:col-span-2">
          <label class="mb-1 block text-xs font-medium text-slate-600">Username</label>
          <Input v-model="filters.username" placeholder="Username" />
        </div>
        <div class="md:col-span-2">
          <label class="mb-1 block text-xs font-medium text-slate-600">Level</label>
          <DropdownMenu widthClass="w-full">
            <template #trigger>
              <Button
                  variant="secondary"
                  size="md"
                  :rounded="true"
                  :hover="false"
                  class="w-full justify-between border border-gray-300 bg-white text-gray-900"
              >
                <span
                    v-if="filters.logLevel"
                    :class="levelStyle(filters.logLevel)"
                    class="inline-flex items-center rounded-md px-2 py-0.5 text-xs font-semibold tracking-wide"
                >
                  {{ filters.logLevel }}
                </span>
                <span v-else class="text-sm text-gray-500">
                  Any level
                </span>
                <span class="ml-2 text-gray-500">▾</span>
              </Button>
            </template>
            <template #menu="{ close }">
              <button
                  type="button"
                  class="flex w-full items-center rounded-md px-3 py-2 text-left text-sm hover:bg-gray-100"
                  @click="filters.logLevel = ''; close();"
              >
                <span class="text-xs text-slate-500">Any level</span>
              </button>
              <button
                  v-for="level in logLevels"
                  :key="level"
                  type="button"
                  class="flex w-full items-center rounded-md px-3 py-2 text-left text-sm hover:bg-gray-100"
                  @click="filters.logLevel = level; close();"
              >
                <span
                    :class="levelStyle(level)"
                    class="inline-flex items-center rounded-md px-2 py-0.5 text-xs font-semibold tracking-wide"
                >
                  {{ level }}
                </span>
              </button>
            </template>
          </DropdownMenu>
        </div>
        <div class="md:col-span-2">
          <label class="mb-1 block text-xs font-medium text-slate-600">Method</label>
          <DropdownMenu widthClass="w-full">
            <template #trigger>
              <Button
                  variant="secondary"
                  size="md"
                  :rounded="true"
                  :hover="false"
                  class="w-full justify-between border border-gray-300 bg-white text-gray-900"
              >
                <span
                    v-if="filters.method"
                    :class="methodStyle(filters.method)"
                    class="inline-flex items-center rounded-md px-2 py-0.5 text-xs font-semibold tracking-wide"
                >
                  {{ filters.method }}
                </span>
                <span v-else class="text-sm text-gray-500">
                  Any method
                </span>
                <span class="ml-2 text-gray-500">▾</span>
              </Button>
            </template>
            <template #menu="{ close }">
              <button
                  type="button"
                  class="flex w-full items-center rounded-md px-3 py-2 text-left text-sm hover:bg-gray-100"
                  @click="filters.method = ''; close();"
              >
                <span class="text-xs text-slate-500">Any method</span>
              </button>
              <button
                  v-for="method in methods"
                  :key="method"
                  type="button"
                  class="flex w-full items-center rounded-md px-3 py-2 text-left text-sm hover:bg-gray-100"
                  @click="filters.method = method; close();"
              >
                <span
                    :class="methodStyle(method)"
                    class="inline-flex items-center rounded-md px-2 py-0.5 text-xs font-semibold tracking-wide"
                >
                  {{ method }}
                </span>
              </button>
            </template>
          </DropdownMenu>
        </div>
        <div class="md:col-span-2">
          <label class="mb-1 block text-xs font-medium text-slate-600">Status</label>
          <Input v-model="filters.status" placeholder="200" type="number" />
        </div>
        <div class="md:col-span-4">
          <label class="mb-1 block text-xs font-medium text-slate-600">Path</label>
          <Input v-model="filters.path" placeholder="/api/logs" />
        </div>
        <div class="md:col-span-3">
          <label class="mb-1 block text-xs font-medium text-slate-600">From</label>
          <Input v-model="filters.from" type="datetime-local" />
        </div>
        <div class="md:col-span-3">
          <label class="mb-1 block text-xs font-medium text-slate-600">To</label>
          <Input v-model="filters.to" type="datetime-local" />
        </div>
        <div class="flex gap-2 md:col-span-3">
          <Button
              variant="primary"
              :rounded="true"
              hover
              class="w-full"
              @click="applyFilters"
          >
            Apply
          </Button>
          <Button
              variant="danger"
              :rounded="true"
              hover
              class="w-full"
              @click="resetFilters"
          >
            Reset
          </Button>
        </div>
      </div>
    </Card>
    <Loading v-if="loading" label="Loading logs..." />
    <Table v-else :columns="columns" :rows="logs" rowKey="id">
      <template #cell-createdAt="{ value }">
        <span class="text-xs text-slate-500">{{ formatDate(value) }}</span>
      </template>
      <template #cell-logLevel="{ value }">
        <span
            :class="levelStyle(value)"
            class="inline-flex items-center rounded-md px-2 py-0.5 text-xs font-semibold tracking-wide"
        >
          {{ value }}
        </span>
      </template>
      <template #cell-username="{ value }">
        <span class="font-medium text-slate-900">{{ value ?? "—" }}</span>
      </template>
      <template #cell-method="{ value }">
        <span
            :class="methodStyle(value)"
            class="inline-flex items-center rounded-md px-2 py-0.5 text-xs font-semibold tracking-wide"
        >
          {{ value ?? "—" }}
        </span>
      </template>
      <template #cell-path="{ value }">
        <span class="break-all font-mono text-sm text-slate-600">{{ value ?? "—" }}</span>
      </template>
      <template #cell-status="{ value }">
        <span :class="statusStyle(value)" class="font-semibold">
          {{ value ?? "—" }}
        </span>
      </template>
      <template #cell-durationMs="{ value }">
        <span :class="durationStyle(value)" class="text-sm">
          {{ value != null ? `${value} ms` : "—" }}
        </span>
      </template>
    </Table>
    <Pagination
        v-model:currentPage="currentPage"
        :totalNum="totalLogs"
        :perPage="perPage"
    />
    <Error class="mt-2" :error="error" dismissible @close="error = null" />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { useApiFetch } from "~/composables/useApiFetch";
import type { ApiError } from "~/models/error";
import type { LogDto } from "~/models/log";
import type { PageResponse } from "~/models/page";

definePageMeta({ layout: "admin" });

const error = ref<ApiError | null>(null);
const logs = ref<LogDto[]>([]);
const totalLogs = ref(0);
const currentPage = ref(1);
const perPage = ref(50);
const loading = ref(true);

function toLocalDatetime(date: Date): string {
  const offset = date.getTimezoneOffset();
  const local = new Date(date.getTime() - offset * 60000);
  return local.toISOString().slice(0, 16);
}

const now = new Date();
now.setHours(23, 59, 59, 999);

const yesterday = new Date();
yesterday.setDate(yesterday.getDate() - 1);
yesterday.setHours(0, 0, 0, 0);

function defaultFilters(): {
  username: string;
  logLevel: string;
  method: string;
  path: string;
  status: string;
  from: string;
  to: string;
} {
  return {
    username: "",
    logLevel: "",
    method: "",
    path: "",
    status: "",
    from: toLocalDatetime(yesterday),
    to: toLocalDatetime(now),
  };
}

const filters = ref(defaultFilters());

const columns = [
  { key: "createdAt", label: "Time" },
  { key: "logLevel", label: "Level" },
  { key: "username", label: "User" },
  { key: "method", label: "Method" },
  { key: "path", label: "Path" },
  { key: "status", label: "Status" },
  { key: "durationMs", label: "Duration" },
];

const logLevels = ["INFO", "WARN", "ERROR", "DEBUG"];
const methods = ["GET", "POST", "PUT", "PATCH", "DELETE"];

const levelStyles: Record<string, string> = {
  INFO: "bg-blue-50 text-blue-700",
  WARN: "bg-amber-50 text-amber-700",
  ERROR: "bg-red-50 text-red-700",
  DEBUG: "bg-slate-100 text-slate-600",
};

const methodStyles: Record<string, string> = {
  GET: "bg-emerald-50 text-emerald-700",
  POST: "bg-blue-50 text-blue-700",
  PUT: "bg-amber-50 text-amber-700",
  PATCH: "bg-violet-50 text-violet-700",
  DELETE: "bg-red-50 text-red-700",
};

function levelStyle(level: string | null): string {
  if (!level) {
    return "bg-slate-100 text-slate-600";
  }
  return levelStyles[level] ?? "bg-slate-100 text-slate-600";
}

function methodStyle(method: string | null): string {
  if (!method) {
    return "bg-slate-100 text-slate-600";
  }
  return methodStyles[method] ?? "bg-slate-100 text-slate-600";
}

function statusStyle(status: number | null): string {
  if (status == null) {
    return "text-slate-400";
  }
  if (status >= 500) {
    return "text-red-600";
  }
  if (status >= 400) {
    return "text-amber-600";
  }
  return "text-emerald-600";
}

function durationStyle(durationMs: number | null): string {
  if (durationMs == null) {
    return "text-slate-400";
  }
  if (durationMs >= 2000) {
    return "font-semibold text-red-600";
  }
  if (durationMs >= 1000) {
    return "font-medium text-amber-600";
  }
  return "text-slate-600";
}

function formatDate(value: string): string {
  return new Date(value).toLocaleString();
}

async function fetchLogs(): Promise<void> {
  loading.value = true;
  try {
    error.value = null;

    const params = new URLSearchParams();

    params.set("page", String(currentPage.value - 1));
    params.set("size", String(perPage.value));

    if (filters.value.username.trim()) {
      params.set("username", filters.value.username.trim());
    }
    if (filters.value.logLevel.trim()) {
      params.set("logLevel", filters.value.logLevel.trim());
    }
    if (filters.value.method.trim()) {
      params.set("method", filters.value.method.trim());
    }
    if (filters.value.path.trim()) {
      params.set("path", filters.value.path.trim());
    }
    if (filters.value.status.trim()) {
      params.set("status", filters.value.status.trim());
    }
    if (filters.value.from) {
      params.set("from", new Date(filters.value.from).toISOString());
    }
    if (filters.value.to) {
      params.set("to", new Date(filters.value.to).toISOString());
    }

    const query = params.toString() ? `?${params.toString()}` : "";
    const response = await useApiFetch<PageResponse<LogDto>>(`/logs${query}`);

    logs.value = response?.content ?? [];
    totalLogs.value = response?.totalElements ?? 0;
  } catch (e: unknown) {
    error.value = e as ApiError;
    logs.value = [];
    totalLogs.value = 0;
  } finally {
    loading.value = false;
  }
}

function applyFilters(): void {
  currentPage.value = 1;
  fetchLogs();
}

function resetFilters(): void {
  filters.value = defaultFilters();
  currentPage.value = 1;
  fetchLogs();
}

watch(currentPage, async () => {
  await fetchLogs();
});

await fetchLogs();
</script>