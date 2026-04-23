<template>
  <div class="relative inline-block" ref="rootRef">
    <div @click="toggle" class="inline-flex">
      <slot name="trigger" :open="open" />
    </div>

    <Transition
        enter-active-class="transition duration-150 ease-out"
        enter-from-class="opacity-0 translate-y-1 scale-[0.98]"
        enter-to-class="opacity-100 translate-y-0 scale-100"
        leave-active-class="transition duration-100 ease-in"
        leave-from-class="opacity-100 translate-y-0 scale-100"
        leave-to-class="opacity-0 translate-y-1 scale-[0.98]"
    >
      <div
          v-if="open"
          :class="menuClasses"
          role="menu"
      >
        <slot name="menu" :close="close" />
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";

const props = withDefaults(
    defineProps<{
      align?: "left" | "right";
      widthClass?: string;
    }>(),
    {
      align: "right",
      widthClass: "w-48",
    }
);

const open = ref(false);
const rootRef = ref<HTMLElement | null>(null);

const close = (): void => {
  open.value = false;
};

const toggle = (): void => {
  open.value = !open.value;
};

const menuClasses = computed(() => {
  const alignClass = props.align === "right" ? "right-0" : "left-0";

  return [
    "absolute",
    alignClass,
    "mt-3",
    props.widthClass,
    "overflow-hidden",
    "rounded-2xl",
    "border",
    "border-slate-200",
    "bg-white/95",
    "backdrop-blur",
    "shadow-[0_20px_50px_rgba(15,23,42,0.18)]",
    "ring-1",
    "ring-black/5",
    "z-50",
  ].join(" ");
});

const onDocClick = (e: MouseEvent): void => {
  const el = rootRef.value;
  if (!el) return;

  if (e.target instanceof Node && !el.contains(e.target)) {
    close();
  }
};

const onKeyDown = (e: KeyboardEvent): void => {
  if (e.key === "Escape") {
    close();
  }
};

onMounted(() => {
  document.addEventListener("click", onDocClick);
  document.addEventListener("keydown", onKeyDown);
});

onBeforeUnmount(() => {
  document.removeEventListener("click", onDocClick);
  document.removeEventListener("keydown", onKeyDown);
});
</script>