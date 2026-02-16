<template>
  <div>
    <Input v-model="username" placeholder="Username" />
    <Input v-model="email" placeholder="Email" />
    <Input v-model="password" type="password" placeholder="Password" />
    <Button variant="success" @click="register">Register</Button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';

const username = ref('');
const email = ref('');
const password = ref('');

type UserResponse = { id: number; email: string; username: string; };

const register = async () => {
  const created = await useApiFetch<UserResponse>('/auth/register', {
    method: 'POST',
    body: { username: username.value, email: email.value, password: password.value },
  });

  console.log('registered:', created);
};
</script>
