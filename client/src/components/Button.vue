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
      rounded: true,
    }
);

const classes = computed(() => {
  const base =
      "inline-flex items-center justify-center gap-2 whitespace-nowrap border font-semibold select-none outline-none transition-all duration-200 focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-slate-400";

  const roundedClass = props.rounded ? "rounded-xl" : "rounded-none";

  const disabledClass = props.disabled
      ? "cursor-not-allowed opacity-50 shadow-none"
      : "cursor-pointer active:scale-[0.99]";

  const sizeClasses: Record<Size, string> = {
    sm: "min-h-9 px-3.5 text-sm",
    md: "min-h-11 px-4.5 text-sm",
    lg: "min-h-12 px-6 text-base",
  };

  const variants: Record<Variant, string> = {
    primary: "border-slate-900 bg-slate-900 text-white shadow-[0_8px_20px_rgba(15,23,42,0.18)]",
    secondary: "border-slate-300 bg-white text-slate-800 shadow-sm",
    danger: "border-red-700 bg-red-600 text-white shadow-[0_8px_20px_rgba(220,38,38,0.18)]",
    success: "border-emerald-700 bg-emerald-600 text-white shadow-[0_8px_20px_rgba(5,150,105,0.18)]",
  };

  const hoverClasses: Record<Variant, string> = {
    primary: "hover:bg-slate-800 hover:border-slate-800 hover:shadow-[0_12px_28px_rgba(15,23,42,0.24)]",
    secondary: "hover:bg-slate-50 hover:border-slate-400 hover:text-slate-900",
    danger: "hover:bg-red-700 hover:border-red-800 hover:shadow-[0_12px_28px_rgba(220,38,38,0.24)]",
    success: "hover:bg-emerald-700 hover:border-emerald-800 hover:shadow-[0_12px_28px_rgba(5,150,105,0.24)]",
  };

  const hoverClass =
      !props.hover || props.disabled
          ? ""
          : hoverClasses[props.variant];

  return [
    base,
    sizeClasses[props.size],
    roundedClass,
    disabledClass,
    variants[props.variant],
    hoverClass,
  ]
      .filter(Boolean)
      .join(" ");
});

const type = computed<ButtonType>(() => props.type);
const disabled = computed(() => props.disabled);
</script>