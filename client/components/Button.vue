<template>
  <button
      :class="classes"
      :disabled="disabled"
      v-bind="$attrs"
  >
    <slot />
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue';

type Variant =
    | 'primary'
    | 'secondary'
    | 'danger'
    | 'success';

const props = defineProps<{
  variant?: Variant;
  disabled?: boolean;
  hover?: boolean;
  rounded?: boolean;
}>();

const variant = props.variant ?? 'primary';

// build classes based on props
const classes = computed(() => {
  const base =
      'px-4 py-2 text-sm font-medium transition-colors duration-150';

  const roundedClass = props.rounded
      ? 'rounded-md'
      : '';

  const disabledClass = props.disabled
      ? 'opacity-50 cursor-not-allowed'
      : 'cursor-pointer';

  const hoverClass =
      props.hover === false || props.disabled
          ? ''
          : 'hover:brightness-110';

  const variants: Record<Variant, string> = {
    primary: 'bg-blue-600 text-white',
    secondary: 'bg-gray-200 text-gray-900',
    danger: 'bg-red-600 text-white',
    success: 'bg-green-600 text-white'
  };

  return [
    base,
    roundedClass,
    disabledClass,
    hoverClass,
    variants[variant]
  ]
      .filter(Boolean)
      .join(' ');
});
</script>
