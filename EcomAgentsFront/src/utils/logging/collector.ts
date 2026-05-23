import type { App } from 'vue'
import type { Router } from 'vue-router'
import type { AxiosInstance } from 'axios'
import { log, setLogRoute, setLogUserId } from './writer'
import type { LogCategory, LogLevel } from './types'

export function setupLogCollector(app: App, router: Router, http: AxiosInstance) {
  // --- Router logging ---
  let navStart = Date.now()
  router.beforeEach((to) => {
    navStart = Date.now()
    setLogRoute(to.name as string || '')
  })
  router.afterEach((to) => {
    const duration = Date.now() - navStart
    log('INFO', 'ROUTER', `导航到 ${to.name as string}`, {
      from: to.name as string,
      path: to.path,
    }, duration)
  })

  // --- Axios logging ---
  http.interceptors.request.use((config) => {
    // Skip logging for log submission itself to avoid infinite loops
    if (config.url?.includes('/system-logs')) return config
    ;(config as unknown as Record<string, number>).__logStart = Date.now()
    log('INFO', 'API', `${(config.method || 'GET').toUpperCase()} ${config.url}`, {
      method: config.method,
      url: config.url,
      params: config.params,
    })
    return config
  })

  http.interceptors.response.use(
    (response) => {
      // Skip logging for log submission itself
      if (response.config.url?.includes('/system-logs')) return response
      const start = (response.config as unknown as Record<string, number>).__logStart || Date.now()
      const duration = Date.now() - start
      const level: LogLevel = response.status >= 400 ? 'WARN' : 'INFO'
      log(level, 'API', `${response.status} ${response.config.method?.toUpperCase()} ${response.config.url}`, {
        status: response.status,
        method: response.config.method,
        url: response.config.url,
      }, duration)
      return response
    },
    (error) => {
      // Skip logging for log submission itself
      if (error.config?.url?.includes('/system-logs')) return Promise.reject(error)
      const start = (error.config as unknown as Record<string, number>).__logStart || Date.now()
      const duration = Date.now() - start
      const status = error.response?.status || 0
      const url = error.config?.url || ''
      const method = error.config?.method || ''
      log('ERROR', 'API', `请求失败 ${status} ${method?.toUpperCase()} ${url}`, {
        status,
        method,
        url,
        message: error.message,
      }, duration)
      return Promise.reject(error)
    },
  )

  // --- Pinia action logging ---
  // Pinia plugin: called when store is created
  app.config.globalProperties.$piniaStoreAction = (actionName: string, storeName: string, args: unknown[], error?: unknown) => {
    const category: LogCategory = storeName === 'auth' ? 'AUTH' : 'USER_ACTION'
    if (error) {
      log('ERROR', category, `${storeName}.${actionName} 失败`, { action: actionName, store: storeName, args, error: String(error) })
    } else {
      log('INFO', category, `${storeName}.${actionName}`, { action: actionName, store: storeName, args })
    }
  }

  // --- Global error handlers ---
  app.config.errorHandler = (err, _instance, info) => {
    log('ERROR', 'ERROR', `Vue 错误: ${info}`, {
      message: err instanceof Error ? err.message : String(err),
      stack: err instanceof Error ? err.stack : undefined,
      info,
    })
  }

  if (typeof window !== 'undefined') {
    window.onerror = (_msg, _source, _line, _col, error) => {
      log('ERROR', 'ERROR', '未捕获的 JS 错误', {
        message: error?.message || String(_msg),
        stack: error?.stack,
        source: _source,
      })
    }

    window.onunhandledrejection = (event) => {
      log('ERROR', 'ERROR', '未处理的 Promise 拒绝', {
        message: event.reason?.message || String(event.reason),
        stack: event.reason?.stack,
      })
    }
  }

  // --- Sync user ID into logger ---
  const userStr = typeof localStorage !== 'undefined' ? localStorage.getItem('ecomagents_current_user') : null
  if (userStr) {
    try {
      const user = JSON.parse(userStr)
      setLogUserId(user.id)
    } catch { /* ignore */ }
  }
}
