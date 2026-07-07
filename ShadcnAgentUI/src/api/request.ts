import axios from 'axios'
import type { AxiosInstance, AxiosError, InternalAxiosRequestConfig, AxiosRequestConfig } from 'axios'
import axiosRetry from 'axios-retry'
import { toast } from 'sonner'
import { STORAGE_KEY_TOKEN, STORAGE_KEY_USER, API_BASE_URL, API_TIMEOUT } from '@/constants'
import type { ApiResponse } from '@/types/api'
import i18n from '@/locales'
import { logger } from '@/utils/logger'
import { maskRequestInfo, maskResponseInfo } from '@/utils/mask'

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

/** 记录耗时结构 */
const startTimes = new WeakMap<InternalAxiosRequestConfig, number>()

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem(STORAGE_KEY_TOKEN)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  startTimes.set(config, performance.now())

  // 脱敏记录请求日志（跳过 client-logs 和 system-logs 自身避免循环）
  if (!config.url?.includes('/client-logs') && !config.url?.includes('/system-logs')) {
    const reqInfo = maskRequestInfo({
      method: config.method,
      url: config.url,
      headers: config.headers as Record<string, unknown>,
      data: config.data,
    })
    // 只记录关键信息到控制台，不会阻塞请求
    logger.debug('HTTP', `→ ${reqInfo.method} ${reqInfo.url}`)
  }

  return config
})

http.interceptors.response.use(
  (response) => {
    const start = startTimes.get(response.config)
    startTimes.delete(response.config)
    const duration = start ? Math.round(performance.now() - start) : 0

    // 脱敏记录响应日志
    if (!response.config.url?.includes('/client-logs') && !response.config.url?.includes('/system-logs')) {
      const respInfo = maskResponseInfo({
        status: response.status,
        data: response.data,
      })
      logger.traceResponse(
        response.config.method ?? 'GET',
        response.config.url ?? '',
        response.status,
        duration,
        respInfo.data,
      )
    }

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

    // 脱敏记录错误响应日志
    if (!error.config?.url?.includes('/client-logs') && !error.config?.url?.includes('/system-logs')) {
      logger.traceResponse(
        error.config?.method ?? 'GET',
        error.config?.url ?? '',
        error.response?.status ?? 0,
        duration,
        error.response?.data,
      )
    }

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

/**
 * 类型安全的 API 调用包装。
 *
 * 自动解包 ApiResponse<T> 中的 .data 字段，同时保持 T 的类型信息。
 * 使用方式：api.get<T>(url) / api.post<T>(url, data) / 等
 *
 * 底层仍通过 http 实例发送请求，复用拦截器（Token 注入、401 跳转、日志记录）。
 */
export const api = {
  get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return http.get<any, T>(url, config)
  },
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return http.post<any, T>(url, data, config)
  },
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return http.put<any, T>(url, data, config)
  },
  patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return http.patch<any, T>(url, data, config)
  },
  delete<T = void>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return http.delete<any, T>(url, config)
  },
}
