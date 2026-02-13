<template>
  <div>
    <Input
        v-model="username"
        placeholder="Username"
    />
    <Input
        v-model="password"
        type="password"
        placeholder="Password"
    />
    <Button
        variant="success"
        @click="test"
    >
      Login
    </Button>
  </div>
</template>

<script setup>
import {ref} from 'vue';

const username = ref('');
const password = ref('');

</script>

<style scoped>

</style>