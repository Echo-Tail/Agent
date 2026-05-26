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

/** 记录请求耗时，自动跳过 system-logs 自身避免循环 */
function logApiCall(method: string | undefined, url: string | undefined, status: number, duration: number, errMsg?: string) {
  if (!url || url.includes('/system-logs')) return
  try {
    const userStr = localStorage.getItem(STORAGE_KEY_USER)
    const userId = userStr ? (JSON.parse(userStr) as Record<string, unknown>)?.id as number | undefined : undefined
    const body: Record<string, unknown> = {
      level: status >= 400 ? 'ERROR' : 'INFO',
      category: 'API',
      message: `${method?.toUpperCase()} ${url} → ${status}${errMsg ? `: ${errMsg}` : ''}`,
      duration,
      route: url,
      userId,
    }
    if (errMsg) body.data = JSON.stringify({ error: errMsg })
    // 静默提交，失败不处理
    const token = localStorage.getItem(STORAGE_KEY_TOKEN)
    fetch(`/v1/system-logs`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: JSON.stringify(body),
    }).catch(() => {})
  } catch { /* ignore */ }
}

const startTimes = new WeakMap<InternalAxiosRequestConfig, number>()

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem(STORAGE_KEY_TOKEN)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  startTimes.set(config, performance.now())
  return config
})

http.interceptors.response.use(
  (response) => {
    const start = startTimes.get(response.config)
    startTimes.delete(response.config)
    const duration = start ? Math.round(performance.now() - start) : 0
    logApiCall(response.config.method, response.config.url, response.status, duration)
    const body = response.data as ApiResponse<unknown>
    if (body.code === 200) {
      return body.data as any
    }
    const msg = body.message || i18n.global.t('error.operationFailed')
    toast.error(msg)
    return Promise.reject(new Error(msg))
  },
  (error: AxiosError<{ message?: string }>) => {
    const start = error.config ? startTimes.get(error.config) : undefined
    if (error.config) startTimes.delete(error.config)
    const duration = start ? Math.round(performance.now() - start) : 0
    logApiCall(error.config?.method, error.config?.url, error.response?.status ?? 0, duration, error.message)
    if (error.response?.status === 401) {
      localStorage.removeItem(STORAGE_KEY_TOKEN)
      localStorage.removeItem(STORAGE_KEY_USER)
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
      return Promise.reject(error)
    }
    const msg = error.response?.data?.message
      || (error.response?.status === 403 ? i18n.global.t('error.forbidden') : i18n.global.t('error.networkError'))
    toast.error(msg)
    return Promise.reject(new Error(msg))
  },
)

export default http
