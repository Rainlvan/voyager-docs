<script setup lang="ts">
import AppShell from './components/AppShell.vue'
import LoginView from './components/LoginView.vue'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
</script>

<template>
  <AppShell v-if="auth.isAuthenticated" />
  <LoginView v-else />
</template>
