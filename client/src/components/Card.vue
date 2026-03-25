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
  borderClass?: string;
  hoverBorderClass?: string;
  paddingClass?: string;
}>();

const classes = computed(() => {
  const base = [
    "relative",
    "transition-all",
    "duration-200",
    props.paddingClass ?? "p-6",
  ];

  const variants: Record<Variant, string> = {
    default: "bg-white shadow-sm",
    elevated: "bg-white shadow-lg",
    outlined: props.borderClass ?? "bg-white border border-slate-200 shadow-sm",
    subtle: "bg-gray-50",
  };

  return [
    ...base,
    variants[props.variant ?? "default"],
    props.hover
        ? [
          "hover:-translate-y-0.5",
          "hover:shadow-xl",
          props.hoverBorderClass ?? "",
        ].join(" ")
        : "",
    props.rounded ? "rounded-2xl" : "",
  ].join(" ");
});
</script>