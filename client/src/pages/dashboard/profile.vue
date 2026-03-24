<template>
  <UserProfile :user="user"/>
</template>

<script setup lang="ts">
import {useUserStore} from "~/stores/user";

const userStore = useUserStore();
const user = userStore.user;
</script>