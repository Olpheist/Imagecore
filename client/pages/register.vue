<template>
  <div>
    <Input
        v-model="username"
        placeholder="Username"
    />
    <Input
        v-model="email"
        placeholder="Email"
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
      Register
    </Button>
  </div>
</template>

<script setup>
import {ref} from 'vue';

const username = ref('');
const email = ref('');
const password = ref('');

</script>

<style scoped>

</style>