<template>
  <input
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :class="classes"
      v-bind="$attrs"
      @input="onInput"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  modelValue: string | number | null | undefined;
  placeholder?: string;
  disabled?: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void;
}>();

function onInput(e: Event): void {
  const el = e.target as HTMLInputElement;
  emit('update:modelValue', el.value);
}

const classes = computed(() => {
  const base =
      'block w-full px-3 py-2 text-sm text-gray-900 bg-white border border-gray-300 transition-colors duration-150';

  const focus =
      'focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500';

  const disabledClass = props.disabled
      ? 'bg-gray-100 text-gray-500 cursor-not-allowed'
      : '';

  const rounded = 'rounded';

  return [
    base,
    focus,
    disabledClass,
    rounded
  ]
      .filter(Boolean)
      .join(' ');
});
</script>
