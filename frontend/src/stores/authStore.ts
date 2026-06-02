import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface UserInfo {
  id: number
  username: string
  avatar: string
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserInfo | null>(null)
  const isLoggedIn = computed(() => user.value !== null)

  function loadFromStorage() {
    const stored = localStorage.getItem('auth_user')
    if (stored) {
      try {
        user.value = JSON.parse(stored)
      } catch {
        localStorage.removeItem('auth_user')
      }
    }
  }

  function setUser(u: UserInfo) {
    user.value = u
    localStorage.setItem('auth_user', JSON.stringify(u))
  }

  function logout() {
    user.value = null
    localStorage.removeItem('auth_user')
  }

  function updateUser(partial: Partial<UserInfo>) {
    if (user.value) {
      user.value = { ...user.value, ...partial }
      localStorage.setItem('auth_user', JSON.stringify(user.value))
    }
  }

  loadFromStorage()

  return { user, isLoggedIn, setUser, logout, updateUser, loadFromStorage }
})
