<template>
  <div :class="classes">
    <slot />
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";

type Variant =
    | "default"
    | "elevated"
    | "outlined"
    | "subtle";

const props = defineProps<{
  variant?: Variant;
  hover?: boolean;
  rounded?: boolean;
}>();

const classes = computed(() => {
  const base =
      "p-6 transition-all duration-200";

  const variants: Record<Variant, string> = {
    default: "bg-white shadow-sm",
    elevated: "bg-white shadow-lg",
    outlined: "bg-white border border-gray-300",
    subtle: "bg-gray-50",
  };

  return [
    base,
    variants[props.variant ?? "default"],
    props.hover ? "hover:shadow-xl hover:-translate-y-1" : "",
    props.rounded ? "rounded-xl" : "",
  ].join(" ");
});
</script>
