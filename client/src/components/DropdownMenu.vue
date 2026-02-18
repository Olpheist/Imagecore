<template>
  <div class="relative inline-block" ref="rootRef">
    <!-- Trigger -->
    <div @click="toggle" class="inline-flex">
      <slot name="trigger" :open="open" />
    </div>
    <!-- Menu -->
    <div
        v-if="open"
        :class="menuClasses"
        role="menu"
    >
      <slot name="menu" :close="close" />
    </div>
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
    "mt-2",
    props.widthClass,
    "bg-white",
    "border",
    "border-gray-200",
    "rounded-lg",
    "shadow-lg",
    "p-1",
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

<style scoped>

</style>
