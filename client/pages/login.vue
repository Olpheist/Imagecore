<template>
  <div>
    <Input v-model="username" placeholder="Username" />
    <Input v-model="password" type="password" placeholder="Password" />
    <Button variant="success" @click="login">Login</Button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';

const username = ref('');
const password = ref('');

type UserResponse = { id: number; email: string; username: string; };

const login = async () => {
  const me = await useApiFetch<UserResponse>('/auth/login', {
    method: 'POST',
    body: { username: username.value, password: password.value },
  });

  console.log('logged in:', me);
};
</script>
