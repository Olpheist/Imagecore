<template>
  <div class="relative">
    <input
        :value="modelValue"
        :placeholder="placeholder"
        :disabled="disabled"
        :class="inputClasses"
        v-bind="$attrs"
        :type="type"
        @input="onInput"
    />

    <div
        v-if="$slots.suffix"
        class="absolute right-3 top-1/2 -translate-y-1/2 flex items-center"
    >
      <slot name="suffix" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
  modelValue: string | number | null | undefined;
  placeholder?: string;
  disabled?: boolean;
  type?: string;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
}>();

function onInput(e: Event): void {
  const el = e.target as HTMLInputElement;
  emit("update:modelValue", el.value);
}

const type = computed(() => props.type ?? "text");

const inputClasses = computed(() => {
  const base =
      "block w-full px-3 py-2 text-sm text-gray-900 bg-white border border-gray-300 transition-colors duration-150";

  const focus =
      "focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500";

  const disabledClass = props.disabled ? "bg-gray-100 text-gray-500 cursor-not-allowed" : "";

  const rounded = "rounded";

  // add right padding if suffix exists so text doesn't go under the button
  const padRight = "pr-16";

  return [base, focus, disabledClass, rounded, padRight].filter(Boolean).join(" ");
});
</script>