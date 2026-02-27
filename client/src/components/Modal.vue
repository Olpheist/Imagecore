<!-- components/Modal.vue -->
<template>
  <Teleport to="body">
    <Transition name="modal">
      <div
          v-if="modelValue"
          class="fixed inset-0 z-50 flex items-center justify-center p-4"
      >
        <!-- Backdrop -->
        <div
            class="absolute inset-0 bg-slate-900/40 backdrop-blur-sm"
            @click="$emit('update:modelValue', false)"
        />

        <!-- Panel -->
        <div
            class="relative bg-white rounded-2xl shadow-xl w-full flex flex-col"
            :class="sizeClass"
        >
          <!-- Header -->
          <div class="flex items-center justify-between px-6 py-4 border-b border-slate-100">
            <div>
              <h2 class="text-base font-semibold text-slate-900">{{ title }}</h2>
              <p v-if="description" class="text-xs text-slate-500 mt-0.5">{{ description }}</p>
            </div>
            <button
                @click="$emit('update:modelValue', false)"
                class="text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg p-1.5 transition-colors cursor-pointer"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
              </svg>
            </button>
          </div>

          <!-- Body -->
          <div class="px-6 py-5 overflow-y-auto">
            <slot />
          </div>

          <!-- Footer -->
          <div v-if="$slots.footer" class="px-6 py-4 border-t border-slate-100 flex items-center justify-end gap-2">
            <slot name="footer" />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
const props = defineProps<{
  modelValue: boolean;
  title: string;
  description?: string;
  size?: "sm" | "md" | "lg" | "xl";
}>();

defineEmits<{
  "update:modelValue": [value: boolean];
}>();

const sizeClass = computed(() => ({
  sm: "max-w-sm",
  md: "max-w-md",
  lg: "max-w-lg",
  xl: "max-w-2xl",
}[props.size ?? "md"]));
</script>

<style scoped>
.modal-enter-active,
.modal-leave-active {
  transition: opacity 200ms ease;
}
.modal-enter-active .relative,
.modal-leave-active .relative {
  transition: opacity 200ms ease, transform 200ms ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}
.modal-enter-from .relative,
.modal-leave-to .relative {
  opacity: 0;
  transform: translateY(8px) scale(0.98);
}
</style>