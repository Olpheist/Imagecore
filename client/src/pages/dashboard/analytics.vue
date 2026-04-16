<template>
  <div class="min-h-screen px-6 py-12">
    <!-- Header -->
    <div class="max-w-6xl mx-auto mb-8">
      <h1 class="text-2xl font-semibold">Tool Analytics</h1>
      <p class="text-sm text-gray-500 mt-1">Usage statistics for tools you own</p>
    </div>

    <!-- Error loading tools list -->
    <div class="max-w-6xl mx-auto mb-6">
      <Error :error="toolsError" dismissible @close="toolsError = null" />
    </div>

    <div class="max-w-6xl mx-auto flex gap-6 items-start">
      <!-- Sidebar -->
      <div class="w-56 shrink-0">
        <div class="bg-white rounded-2xl shadow-sm border border-gray-100 p-3">

          <!-- Your Tools -->
          <p class="text-[10px] font-semibold uppercase tracking-wider text-gray-400 px-2 mb-2">Your Tools</p>

          <div v-if="toolsLoading" class="space-y-2 px-1 py-2">
            <div v-for="n in 3" :key="n" class="h-8 bg-gray-100 rounded-lg animate-pulse" />
          </div>

          <div v-else-if="ownedTools.length === 0" class="px-2 py-3 text-center">
            <p class="text-xs text-gray-400">No tools created yet.</p>
            <NuxtLink to="/dashboard/tools" class="text-xs text-violet-600 hover:underline mt-1 inline-block">
              Go to Tools
            </NuxtLink>
          </div>

          <template v-else>
            <button
              v-for="tool in ownedTools"
              :key="tool.toolId"
              class="w-full text-left px-3 py-2 rounded-lg mb-1 flex items-center justify-between transition-colors"
              :class="selectedTool?.toolId === tool.toolId
                ? 'bg-gray-900 text-white'
                : 'hover:bg-gray-50 text-gray-700'"
              @click="selectTool(tool)"
            >
              <div class="min-w-0">
                <div class="text-xs font-semibold truncate">{{ tool.name }}</div>
                <div class="text-[10px] mt-0.5" :class="selectedTool?.toolId === tool.toolId ? 'text-gray-400' : 'text-gray-400'">
                  {{ capitalizeFirstLetter(tool.category) }}
                </div>
              </div>
            </button>
          </template>

          <!-- Admin: all other tools -->
          <template v-if="isAdmin && otherTools.length > 0">
            <p class="text-[10px] font-semibold uppercase tracking-wider text-gray-400 px-2 mt-4 mb-2">All Tools (Admin)</p>
            <button
              v-for="tool in otherTools"
              :key="tool.toolId"
              class="w-full text-left px-3 py-2 rounded-lg mb-1 flex items-center justify-between transition-colors"
              :class="selectedTool?.toolId === tool.toolId
                ? 'bg-gray-900 text-white'
                : 'hover:bg-gray-50 text-gray-700'"
              @click="selectTool(tool)"
            >
              <div class="min-w-0">
                <div class="text-xs font-semibold truncate">{{ tool.name }}</div>
                <div class="text-[10px] mt-0.5 text-gray-400">{{ capitalizeFirstLetter(tool.category) }}</div>
              </div>
            </button>
          </template>
        </div>
      </div>

      <!-- Detail panel -->
      <div class="flex-1 min-w-0">

        <!-- Nothing selected -->
        <div v-if="!selectedTool" class="flex items-center justify-center h-64 text-gray-400">
          <p class="text-sm">Select a tool to view its analytics</p>
        </div>

        <!-- Loading skeleton -->
        <div v-else-if="statsLoading" class="space-y-4 animate-pulse">
          <div class="h-6 bg-gray-100 rounded w-48" />
          <div class="h-4 bg-gray-100 rounded w-32" />
          <div class="grid grid-cols-4 gap-4 mt-4">
            <div v-for="n in 4" :key="n" class="h-24 bg-gray-100 rounded-2xl" />
          </div>
          <div class="h-20 bg-gray-100 rounded-2xl" />
          <div class="h-52 bg-gray-100 rounded-2xl" />
        </div>

        <!-- Stats error -->
        <Error v-else-if="statsError" :error="statsError" dismissible @close="statsError = null" />

        <!-- Stats -->
        <div v-else-if="stats">
          <!-- Tool title -->
          <div class="mb-5">
            <h2 class="text-lg font-bold text-gray-900">{{ selectedTool.name }}</h2>
            <p class="text-sm text-gray-500 mt-0.5">
              {{ capitalizeFirstLetter(selectedTool.category) }} ·
              {{ isAdmin && selectedTool.CreatedByUserId !== userStore.user?.id ? 'Owned by another user' : 'Created by you' }}
            </p>
          </div>

          <!-- 4 stat tiles -->
          <div class="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-4">
            <Card variant="outlined" rounded paddingClass="p-4">
              <p class="text-[10px] text-gray-400 uppercase tracking-wider mb-2">Total Runs</p>
              <p class="text-3xl font-bold text-gray-900">{{ stats.totalRuns }}</p>
            </Card>
            <Card variant="outlined" rounded paddingClass="p-4">
              <p class="text-[10px] text-gray-400 uppercase tracking-wider mb-2">Success Rate</p>
              <p
                class="text-3xl font-bold"
                :class="stats.totalRuns === 0
                  ? 'text-gray-400'
                  : stats.successRatePct >= 90 ? 'text-emerald-600'
                  : stats.successRatePct >= 70 ? 'text-amber-500'
                  : 'text-red-500'"
              >
                {{ stats.totalRuns === 0 ? '—' : stats.successRatePct.toFixed(0) + '%' }}
              </p>
            </Card>
            <Card variant="outlined" rounded paddingClass="p-4">
              <p class="text-[10px] text-gray-400 uppercase tracking-wider mb-2">Avg Completion</p>
              <p class="text-3xl font-bold text-gray-900">
                {{ stats.avgCompletionSeconds != null ? formatDuration(stats.avgCompletionSeconds) : '—' }}
              </p>
            </Card>
            <Card variant="outlined" rounded paddingClass="p-4">
              <p class="text-[10px] text-gray-400 uppercase tracking-wider mb-2">Last Run</p>
              <p class="text-3xl font-bold text-gray-900">
                {{ stats.lastRunAt ? formatRelativeTime(stats.lastRunAt) : '—' }}
              </p>
            </Card>
          </div>

          <!-- Status breakdown bar -->
          <Card variant="outlined" rounded paddingClass="p-4" class="mb-4">
            <p class="text-[10px] text-gray-400 uppercase tracking-wider mb-3">Status Breakdown</p>
            <div v-if="stats.totalRuns === 0" class="text-sm text-gray-400">No runs yet</div>
            <template v-else>
              <div class="flex h-2 rounded-full overflow-hidden gap-px mb-3">
                <div
                  v-if="stats.completedRuns > 0"
                  class="bg-emerald-500 transition-all"
                  :style="{ width: (stats.completedRuns / stats.totalRuns * 100) + '%' }"
                />
                <div
                  v-if="stats.pendingOrRunningRuns > 0"
                  class="bg-amber-400 transition-all"
                  :style="{ width: (stats.pendingOrRunningRuns / stats.totalRuns * 100) + '%' }"
                />
                <div
                  v-if="stats.failedRuns > 0"
                  class="bg-red-400 transition-all"
                  :style="{ width: (stats.failedRuns / stats.totalRuns * 100) + '%' }"
                />
              </div>
              <div class="flex flex-wrap gap-4 text-xs">
                <span class="text-emerald-600 font-medium">● {{ stats.completedRuns }} completed</span>
                <span class="text-amber-500 font-medium">● {{ stats.pendingOrRunningRuns }} running / pending</span>
                <span class="text-red-500 font-medium">● {{ stats.failedRuns }} failed</span>
              </div>
            </template>
          </Card>

          <!-- Recent runs -->
          <Card variant="outlined" rounded paddingClass="p-4">
            <p class="text-[10px] text-gray-400 uppercase tracking-wider mb-3">Recent Runs</p>
            <div v-if="stats.recentJobs.length === 0" class="text-sm text-gray-400">No runs yet</div>
            <div v-else class="space-y-2">
              <div
                v-for="job in stats.recentJobs"
                :key="job.id"
                class="grid grid-cols-[60px_1fr_80px_90px] items-center gap-3 px-3 py-2 bg-gray-50 rounded-lg"
              >
                <span class="text-xs text-gray-500 font-mono">#{{ job.id }}</span>
                <span :class="['text-[11px] font-semibold px-2 py-0.5 rounded-full w-fit border', statusClass(job.status)]">
                  {{ job.status }}
                </span>
                <span class="text-xs text-gray-500 text-right">
                  {{ job.durationSeconds != null ? formatDuration(job.durationSeconds) : '—' }}
                </span>
                <span class="text-xs text-gray-400 text-right">{{ formatRelativeTime(job.createdAt) }}</span>
              </div>
            </div>
          </Card>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useApiFetch } from '~/composables/useApiFetch'
import { useUserStore } from '~/stores/user'
import { capitalizeFirstLetter } from '~/utils/stringFunctions'
import type { ToolDto } from '~/models/tool'
import type { ToolStatsDto } from '~/models/toolStats'
import type { ApiError } from '~/models/error'

useHead({ title: 'Tool Analytics' })

const userStore = useUserStore()
const isAdmin = userStore.hasRole('ADMIN')

const tools        = ref<ToolDto[]>([])
const toolsLoading = ref(false)
const toolsError   = ref<ApiError | null>(null)

const selectedTool = ref<ToolDto | null>(null)
const stats        = ref<ToolStatsDto | null>(null)
const statsLoading = ref(false)
const statsError   = ref<ApiError | null>(null)

const ownedTools = computed(() =>
  tools.value.filter(t => t.CreatedByUserId === userStore.user?.id)
)

const otherTools = computed(() =>
  tools.value.filter(t => t.CreatedByUserId !== userStore.user?.id)
)

async function fetchTools(): Promise<void> {
  toolsLoading.value = true
  toolsError.value = null
  try {
    tools.value = await useApiFetch<ToolDto[]>('/tools')
  } catch (e) {
    toolsError.value = e as ApiError
  } finally {
    toolsLoading.value = false
  }
}

async function selectTool(tool: ToolDto): Promise<void> {
  selectedTool.value = tool
  stats.value = null
  statsError.value = null
  statsLoading.value = true
  try {
    stats.value = await useApiFetch<ToolStatsDto>(`/tools/${tool.toolId}/stats`)
  } catch (e) {
    statsError.value = e as ApiError
  } finally {
    statsLoading.value = false
  }
}

function formatDuration(seconds: number): string {
  if (seconds < 60) return `${seconds.toFixed(0)}s`
  return `${(seconds / 60).toFixed(1)}m`
}

function formatRelativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime()
  const mins = Math.floor(diff / 60_000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins}m ago`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours}h ago`
  return `${Math.floor(hours / 24)}d ago`
}

function statusClass(status: string): string {
  const map: Record<string, string> = {
    COMPLETED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    FAILED:    'bg-red-50 text-red-700 border-red-200',
    RUNNING:   'bg-amber-50 text-amber-700 border-amber-200',
    PENDING:   'bg-gray-100 text-gray-600 border-gray-200',
    SUBMITTED: 'bg-blue-50 text-blue-700 border-blue-200',
  }
  return map[status] ?? 'bg-gray-100 text-gray-500 border-gray-200'
}

onMounted(fetchTools)
</script>
