<template>
  {{ decoded }}
</template>

<script setup>
import { ref } from "vue";
import { getToken } from "~/utils/authToken";
import { decodeToken } from "~/utils/jwt";

const token = getToken();
const decoded = decodeToken(token);

</script>

<style scoped>

</style>