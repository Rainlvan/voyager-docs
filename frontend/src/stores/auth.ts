import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { User } from '../types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('voyager-token') || '')
  const savedUser = localStorage.getItem('voyager-user')
  const user = ref<User | null>(savedUser ? JSON.parse(savedUser) : null)
  const isAuthenticated = computed(() => Boolean(token.value && user.value))

  function setSession(nextToken: string, nextUser: User) {
    token.value = nextToken
    user.value = nextUser
    localStorage.setItem('voyager-token', nextToken)
    localStorage.setItem('voyager-user', JSON.stringify(nextUser))
  }

  function setUser(nextUser: User) {
    user.value = nextUser
    localStorage.setItem('voyager-user', JSON.stringify(nextUser))
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('voyager-token')
    localStorage.removeItem('voyager-user')
  }

  return { token, user, isAuthenticated, setSession, setUser, logout }
})
