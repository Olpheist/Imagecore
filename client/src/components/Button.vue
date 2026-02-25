<template>
  <button
      :type="type"
      :class="classes"
      :disabled="disabled"
      v-bind="$attrs"
  >
    <slot />
  </button>
</template>

<script setup lang="ts">
import { computed } from "vue";

type Variant = "primary" | "secondary" | "danger" | "success";
type Size = "sm" | "md" | "lg";
type ButtonType = "button" | "submit" | "reset";

const props = withDefaults(
    defineProps<{
      variant?: Variant;
      size?: Size;
      type?: ButtonType;
      disabled?: boolean;
      hover?: boolean;
      rounded?: boolean;
    }>(),
    {
      variant: "primary",
      size: "md",
      type: "button",
      disabled: false,
      hover: true,
      rounded: false,
    }
);

const classes = computed(() => {
  const base =
      "font-medium transition-colors duration-150 inline-flex items-center justify-center";

  const roundedClass = props.rounded ? "rounded-md" : "";

  const disabledClass = props.disabled
      ? "opacity-50 cursor-not-allowed"
      : "cursor-pointer";

  const hoverClass = !props.hover || props.disabled ? "" : "hover:brightness-110";

  const sizeClasses: Record<Size, string> = {
    sm: "px-3 py-1.5 text-xs",
    md: "px-4 py-2 text-sm",
    lg: "px-5 py-3 text-base",
  };

  const variants: Record<Variant, string> = {
    primary: "bg-blue-600 text-white",
    secondary: "bg-gray-200 text-gray-900",
    danger: "bg-red-600 text-white",
    success: "bg-green-600 text-white",
  };

  return [
    base,
    sizeClasses[props.size],
    roundedClass,
    disabledClass,
    hoverClass,
    variants[props.variant],
  ]
      .filter(Boolean)
      .join(" ");
});

const type = computed<ButtonType>(() => props.type);
const disabled = computed(() => props.disabled);
</script>