import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi, registerApi } from '../api/auth'
import { STORAGE_KEY_TOKEN, STORAGE_KEY_USER } from '../constants'
import type { UserDTO, LoginRequest, RegisterRequest } from '../types/api'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(STORAGE_KEY_TOKEN))
  const currentUser = ref<UserDTO | null>(_loadUser())

  function _loadUser(): UserDTO | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY_USER)
      return raw ? JSON.parse(raw) : null
    } catch {
      return null
    }
  }

  function _persist() {
    if (token.value) {
      localStorage.setItem(STORAGE_KEY_TOKEN, token.value)
    } else {
      localStorage.removeItem(STORAGE_KEY_TOKEN)
    }
    if (currentUser.value) {
      localStorage.setItem(STORAGE_KEY_USER, JSON.stringify(currentUser.value))
    } else {
      localStorage.removeItem(STORAGE_KEY_USER)
    }
  }

  const isAuthenticated = computed(() => !!(token.value && currentUser.value))
  const isAdmin = computed(() => currentUser.value?.role === 'admin')

  async function login(req: LoginRequest) {
    const res = await loginApi(req)
    const body = res.data
    if (body.code === 200 && body.data) {
      token.value = body.data.token
      currentUser.value = body.data.user
      _persist()
      return { success: true as const, user: body.data.user }
    }
    return { success: false as const, message: body.message || '登录失败' }
  }

  async function register(req: RegisterRequest) {
    const res = await registerApi(req)
    const body = res.data
    if (body.code === 200) {
      return { success: true as const }
    }
    return { success: false as const, message: body.message || '注册失败' }
  }

  function logout() {
    token.value = null
    currentUser.value = null
    _persist()
  }

  function initFromStorage() {
    token.value = localStorage.getItem(STORAGE_KEY_TOKEN)
    currentUser.value = _loadUser()
  }

  return {
    token,
    currentUser,
    isAuthenticated,
    isAdmin,
    login,
    register,
    logout,
    initFromStorage,
  }
})
