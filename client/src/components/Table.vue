<template>
  <div class="w-full bg-white border border-slate-200 rounded-2xl shadow-sm overflow-hidden">
    <table class="w-full table-fixed text-sm">
      <thead>
      <tr class="border-b border-slate-100">
        <th
            v-for="col in columns"
            :key="col.key"
            class="text-left px-5 py-3.5 text-xs font-semibold text-slate-400 uppercase tracking-wider"
            :class="col.class"
        >
          {{ col.label }}
        </th>
      </tr>
      </thead>

      <tbody class="divide-y divide-slate-50">
      <tr
          v-for="(row, rowIndex) in rows"
          :key="rowKey ? row[rowKey] : rowIndex"
          class="hover:bg-slate-50/70 transition-colors duration-100"
      >
        <td
            v-for="col in columns"
            :key="col.key"
            class="px-5 py-4 text-slate-700"
            :class="col.class"
        >
          <slot :name="`cell-${col.key}`" :row="row" :value="row[col.key]">
            {{ row[col.key] }}
          </slot>
        </td>
      </tr>

      <tr v-if="rows.length === 0">
        <td :colspan="columns.length" class="px-5 py-12 text-center text-slate-400 text-sm">
          No data available.
        </td>
      </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts" generic="T extends Record<string, any>">
export interface Column {
  key: string;
  label: string;
  class?: string;
}
defineProps<{
  columns: Column[];
  rows: T[];
  rowKey?: string;
}>();
</script>