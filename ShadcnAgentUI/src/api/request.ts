import axios from 'axios'
import type { AxiosInstance, AxiosError, InternalAxiosRequestConfig } from 'axios'
import axiosRetry from 'axios-retry'
import { toast } from 'sonner'
import { STORAGE_KEY_TOKEN, STORAGE_KEY_USER, API_BASE_URL, API_TIMEOUT } from '@/constants'
import type { ApiResponse } from '@/types/api'
import i18n from '@/locales'

const http: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: API_TIMEOUT,
  headers: { 'Content-Type': 'application/json' },
})

// Retry: network errors + 5xx, 2 retries, exponential backoff
axiosRetry(http, {
  retries: 2,
  retryDelay: axiosRetry.exponentialDelay,
  retryCondition: (error: AxiosError) => {
    if (error.config?.url?.includes('/auth/login')) return false
    if (!error.response) return true
    return error.response.status >= 500
  },
})

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem(STORAGE_KEY_TOKEN)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse
    if (body.code === 200) {
      return body.data
    }
    const msg = body.message || i18n.global.t('error.operationFailed')
    toast.error(msg)
    return Promise.reject(new Error(msg))
  },
  (error: AxiosError<{ message?: string }>) => {
    if (error.response?.status === 401 || error.response?.status === 403) {
      localStorage.removeItem(STORAGE_KEY_TOKEN)
      localStorage.removeItem(STORAGE_KEY_USER)
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
      return Promise.reject(error)
    }
    const msg = error.response?.data?.message || i18n.global.t('error.networkError')
    toast.error(msg)
    return Promise.reject(new Error(msg))
  },
)

export default http
