<template>
  <Card variant="elevated" rounded class="w-full max-w-2xl">
    <div class="space-y-6">
      <div>
        <h2 class="text-xl font-semibold">Profile</h2>
        <p class="text-sm text-gray-500 mt-1">
          View your account information.
        </p>
      </div>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div class="space-y-1">
          <label class="text-sm text-gray-600">Username</label>
          <Input
              :model-value="user?.username ?? ''"
              disabled
          />
        </div>
        <div class="space-y-1">
          <label class="text-sm text-gray-600">Email</label>
          <Input
              :model-value="user?.email ?? ''"
              disabled
          />
        </div>
        <div class="space-y-1">
          <label class="text-sm text-gray-600">Roles</label>
          <Input
              :model-value="rolesDisplay"
              disabled
          />
        </div>
      </div>

      <div v-if="user?.createdAt" class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div class="space-y-1">
          <label class="text-sm text-gray-600">Created</label>
          <Input
              :model-value="formatDate(user.createdAt)"
              disabled
          />
        </div>
      </div>

      <div v-if="user?.subscription" class="pt-4 border-t border-gray-200 space-y-4">
        <h3 class="text-lg font-semibold">Subscription</h3>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div class="space-y-1">
            <label class="text-sm text-gray-600">Plan</label>
            <Input
                :model-value="user.subscription.tierCode"
                disabled
            />
          </div>

          <div class="space-y-1">
            <label class="text-sm text-gray-600">Auto Renew</label>
            <Input
                :model-value="user.subscription.autoRenew ? 'Yes' : 'No'"
                disabled
            />
          </div>

          <div class="space-y-1">
            <label class="text-sm text-gray-600">Current Period Start</label>
            <Input
                :model-value="formatDateNullable(user.subscription.currentPeriodStart)"
                disabled
            />
          </div>

          <div class="space-y-1">
            <label class="text-sm text-gray-600">Current Period End</label>
            <Input
                :model-value="formatDateNullable(user.subscription.currentPeriodEnd)"
                disabled
            />
          </div>
        </div>

        <div class="flex flex-wrap gap-3">
          <Button
              v-if="isFree"
              variant="primary"
              rounded
              :disabled="billingLoading"
              @click="handleUpgrade"
          >
            {{ billingLoading ? "Loading..." : "Upgrade to Pro" }}
          </Button>

          <Button
              v-if="isPro && user?.subscription?.autoRenew"
              variant="danger"
              rounded
              :disabled="billingLoading"
              @click="handleCancelAutoRenew"
          >
            {{ billingLoading ? "Loading..." : "Cancel Auto Renew" }}
          </Button>

          <Button
              v-if="isPro && !user?.subscription?.autoRenew"
              variant="primary"
              rounded
              :disabled="billingLoading"
              @click="handleResumeAutoRenew"
          >
            {{ billingLoading ? "Loading..." : "Resume Auto Renew" }}
          </Button>
        </div>

        <p
            v-if="isPro && !user?.subscription?.autoRenew"
            class="text-sm text-gray-500"
        >
          Your subscription will end at the end of the current billing period unless you resume auto renew.
        </p>
      </div>
    </div>
  </Card>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import type { UserDto } from "~/models/user";
import { useApiFetch } from "~/composables/useApiFetch";

const props = defineProps<{
  user: UserDto | null;
}>();

const emit = defineEmits<{
  (e: "billing-updated"): void;
}>();

const billingLoading = ref(false);

const rolesDisplay = computed(() => {
  const roles = props.user?.userRoles?.map((r) => r.roleName) ?? [];
  return roles.length ? roles.join(", ") : "None";
});

const isPro = computed(() => {
  return props.user?.subscription?.tierCode === "PRO";
});

const isFree = computed(() => {
  return props.user?.subscription?.tierCode === "FREE";
});

const handleUpgrade = async () => {
  try {
    billingLoading.value = true;

    const response = await useApiFetch<{ url: string }>("/billing/checkout", {
      method: "POST",
    });

    if (response?.url) {
      window.location.href = response.url;
    }
  } catch (e) {
    console.error("failed to start upgrade", e);
  } finally {
    billingLoading.value = false;
  }
};

const handleCancelAutoRenew = async () => {
  try {
    billingLoading.value = true;

    await useApiFetch<{ message: string }>("/billing/cancel", {
      method: "POST",
    });

    emit("billing-updated");
  } catch (e) {
    console.error("failed to cancel auto renew", e);
  } finally {
    billingLoading.value = false;
  }
};

const handleResumeAutoRenew = async () => {
  try {
    billingLoading.value = true;

    await useApiFetch<{ message: string }>("/billing/resume", {
      method: "POST",
    });

    emit("billing-updated");
  } catch (e) {
    console.error("failed to resume auto renew", e);
  } finally {
    billingLoading.value = false;
  }
};

const formatDate = (iso: string): string => {
  try {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return iso;
    return d.toLocaleString();
  } catch {
    return iso;
  }
};

const formatDateNullable = (iso: string | null): string => {
  if (!iso) return "—";
  return formatDate(iso);
};
</script>

<style scoped>
</style>