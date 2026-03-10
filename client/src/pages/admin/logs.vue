<template>
  <div>
    <div class="mb-6 flex items-start justify-between">
      <div>
        <h1 class="text-2xl font-semibold text-slate-900 tracking-tight">Logs</h1>
        <p class="mt-1 text-sm text-slate-500">Inspect recent application logs.</p>
      </div>
    </div>
    <Table :columns="columns" :rows="pagedLogs" rowKey="id">
      <template #cell-createdAt="{ value }">
        <span class="text-xs text-slate-500">{{ formatDate(value) }}</span>
      </template>
      <template #cell-logLevel="{ value }">
        <span :class="levelStyle(value)" class="inline-flex items-center rounded-md px-2 py-0.5 text-xs font-semibold tracking-wide">
          {{ value }}
        </span>
      </template>
      <template #cell-username="{ value }">
        <span class="font-medium text-slate-900">{{ value ?? "—" }}</span>
      </template>
      <template #cell-method="{ value }">
        <span :class="methodStyle(value)" class="inline-flex items-center rounded-md px-2 py-0.5 text-xs font-semibold tracking-wide">
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
      <template #cell-ip="{ value }">
        <span class="font-mono text-xs text-slate-500">{{ value ?? "—" }}</span>
      </template>
      <template #cell-message="{ value }">
        <span class="text-sm text-slate-600">{{ value }}</span>
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
import { computed, ref } from "vue";
import { useApiFetch } from "~/composables/useApiFetch";
import type { ApiError } from "~/models/error";
import type { LogDto } from "~/models/log";

definePageMeta({ layout: "admin" });

const error = ref<ApiError | null>(null);
const logs = ref<LogDto[]>([]);
const currentPage = ref(1);
const perPage = ref(50);

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

const filters = ref({
  username: "",
  logLevel: "",
  from: toLocalDatetime(yesterday),
  to: toLocalDatetime(now),
});

const pagedLogs = computed(() => {
  const start = (currentPage.value - 1) * perPage.value;
  return logs.value.slice(start, start + perPage.value);
});

const columns = [
  { key: "createdAt", label: "Time" },
  { key: "logLevel", label: "Level" },
  { key: "username", label: "User" },
  { key: "method", label: "Method" },
  { key: "path", label: "Path" },
  { key: "status", label: "Status" },
  { key: "durationMs", label: "Duration" },
  { key: "ip", label: "IP" }
];

const logLevels = ["INFO", "WARN", "ERROR", "DEBUG"];

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
  try {
    error.value = null;

    const params = new URLSearchParams();

    if (filters.value.username.trim()) {
      params.set("username", filters.value.username.trim());
    }

    if (filters.value.logLevel) {
      params.set("logLevel", filters.value.logLevel);
    }

    if (filters.value.from) {
      params.set("from", new Date(filters.value.from).toISOString());
    }

    if (filters.value.to) {
      params.set("to", new Date(filters.value.to).toISOString());
    }

    const query = params.toString() ? `?${params.toString()}` : "";
    logs.value = (await useApiFetch<LogDto[]>(`/logs${query}`)) ?? [];
    currentPage.value = 1;
  } catch (e: unknown) {
    error.value = e as ApiError;
    logs.value = [];
  }
}

await fetchLogs();
</script>