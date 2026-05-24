import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi, registerApi, getCurrentUserApi } from '@/api/auth'
import { STORAGE_KEY_TOKEN, STORAGE_KEY_USER } from '@/constants'
import type { UserDTO, LoginRequest, RegisterRequest } from '@/types/api'
import i18n from '@/locales'

const { t } = i18n.global

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(STORAGE_KEY_TOKEN))
  const currentUser = ref<UserDTO | null>(_loadUser())
  const initialized = ref(false)

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
    try {
      const data = await loginApi(req)
      token.value = data.token
      currentUser.value = data.user
      _persist()
      return { success: true as const, user: data.user }
    } catch (e) {
      return { success: false as const, message: e instanceof Error ? e.message : t('error.loginFailed') }
    }
  }

  async function register(req: RegisterRequest) {
    try {
      await registerApi(req)
      return { success: true as const }
    } catch (e) {
      return { success: false as const, message: e instanceof Error ? e.message : t('error.registerFailed') }
    }
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

  async function verifyAuth(): Promise<boolean> {
    if (!token.value) return false
    try {
      currentUser.value = await getCurrentUserApi()
      _persist()
      return true
    } catch { /* 网络错误或 401，下方统一清理 */ }
    logout()
    return false
  }

  return {
    token, currentUser, initialized,
    isAuthenticated, isAdmin,
    login, register, logout, initFromStorage, verifyAuth,
  }
})
