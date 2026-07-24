/**
 * 前端日志工具 — 统一管理控制台日志 + 文件日志上报。
 *
 * 功能：
 * - 控制台输出（按级别过滤）
 * - 内存缓冲 + 批量上报后端（30s 或 50 条刷新）
 * - 请求/响应追踪日志
 * - 全局错误自动捕获
 * - 页面卸载前 sendBeacon 兜底
 * - 敏感信息自动脱敏
 */

import { maskHeaders, maskBody, maskUrl, maskString } from './mask'
const consoleSink = ((globalThis as any).__ECOM_CONSOLE_SINK__ ??= {
  error: console.error.bind(console),
  warn: console.warn.bind(console),
  debug: console.debug.bind(console),
  log: console.log.bind(console),
}) as Pick<Console, 'error' | 'warn' | 'debug' | 'log'>

type LogLevel = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR'

interface LogEntry {
  timestamp: string
  level: LogLevel
  context: string
  message: string
  data?: unknown
}

const LOG_LEVELS: Record<LogLevel, number> = {
  DEBUG: 0,
  INFO: 1,
  WARN: 2,
  ERROR: 3,
}

function getEffectiveLevel(): number {
  try {
    const stored = localStorage.getItem('logLevel')
    if (stored && stored in LOG_LEVELS) return LOG_LEVELS[stored as LogLevel]
  } catch { /* ignore */ }
  return import.meta.env.DEV ? LOG_LEVELS['DEBUG'] : LOG_LEVELS['WARN']
}

const LEVEL = getEffectiveLevel()

// ---- 缓冲 & 批量上传 ----

/** 缓冲队列最大条数，达到此数立即上传。 */
const UPLOAD_BATCH_SIZE = 50
/** 缓冲上传间隔（毫秒）。 */
const UPLOAD_INTERVAL_MS = 30_000

let logBuffer: LogEntry[] = []
let uploadTimerId: ReturnType<typeof setInterval> | null = null

/**
 * 添加日志至缓冲队列，达到批量阈值自动上传。
 */
function enqueue(entry: LogEntry) {
  logBuffer.push(entry)
  if (logBuffer.length >= UPLOAD_BATCH_SIZE) {
    flushLogs()
  }
}

/**
 * 将缓冲中的日志批量发送到后端 /v1/client-logs。
 * 使用 fetch 静默发送，失败不处理。
 */
function flushLogs() {
  if (logBuffer.length === 0) return
  const batch = logBuffer.slice()
  logBuffer = []

  try {
    const token = localStorage.getItem('ecomagents_token')
    const payload = batch.map(e => ({
      timestamp: e.timestamp,
      level: e.level,
      context: e.context,
      message: e.message,
      data: e.data !== undefined ? JSON.stringify(e.data) : undefined,
    }))

    // 使用 keepalive 确保页面卸载时能发送
    fetch('/v1/client-logs', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ logs: payload }),
      keepalive: true,
    }).catch(() => { /* 静默失败 */ })
  } catch { /* ignore */ }
}

/**
 * 启动定时上传定时器。
 * 在首次日志写入时自动调用，可重复调用（幂等）。
 */
function ensureUploadTimer() {
  if (uploadTimerId !== null) return
  uploadTimerId = setInterval(() => flushLogs(), UPLOAD_INTERVAL_MS)
  // 页面卸载前刷出剩余日志
  window.addEventListener('beforeunload', () => {
    if (uploadTimerId !== null) {
      clearInterval(uploadTimerId)
      uploadTimerId = null
    }
    flushLogs()
  })
}

/**
 * 公开接口：强制立即刷新缓冲日志到后端。
 */
export function flushLogsNow() {
  flushLogs()
}

// ---- 格式化 & 输出 ----

function formatMessage(level: LogLevel, context: string, message: string, data?: unknown): string {
  const ts = new Date().toISOString()
  const prefix = `[${ts}] [${level}] [${context}]`
  if (data !== undefined) {
    const dataStr = typeof data === 'string' ? data : JSON.stringify(data)
    return `${prefix} ${message} ${dataStr}`
  }
  return `${prefix} ${message}`
}

function log(level: LogLevel, context: string, message: string, data?: unknown) {
  if (LOG_LEVELS[level] < LEVEL) return
  const formatted = formatMessage(level, context, message, data)
  switch (level) {
    case 'ERROR':
      consoleSink.error(formatted)
      break
    case 'WARN':
      consoleSink.warn(formatted)
      break
    case 'DEBUG':
      consoleSink.debug(formatted)
      break
    default:
      consoleSink.log(formatted)
  }

  // 缓冲入队等待上传
  ensureUploadTimer()
  enqueue({ timestamp: new Date().toISOString(), level, context, message, data })
}

export const logger = {
  debug: (ctx: string, msg: string, data?: unknown) => log('DEBUG', ctx, msg, data),
  info: (ctx: string, msg: string, data?: unknown) => log('INFO', ctx, msg, data),
  warn: (ctx: string, msg: string, data?: unknown) => log('WARN', ctx, msg, data),
  error: (ctx: string, msg: string, data?: unknown) => log('ERROR', ctx, msg, data),

  /**
   * 追踪一次 HTTP 请求。
   * @param method 请求方法（大写）
   * @param url 请求 URL（自动脱敏）
   * @param headers 请求头（自动脱敏 Authorization）
   * @param body 请求体（自动脱敏敏感字段）
   */
  traceRequest(method: string, url: string, headers?: Record<string, unknown>, body?: unknown) {
    this.debug('HTTP', `→ ${method} ${maskUrl(url)}`, {
      headers: headers ? maskHeaders(headers as Record<string, string | string[] | undefined>) : undefined,
      body: body ? maskBody(body) : undefined,
    })
  },

  /**
   * 追踪一次 HTTP 响应。
   * @param method 请求方法
   * @param url 请求 URL（自动脱敏）
   * @param status HTTP 状态码
   * @param duration 耗时（毫秒）
   * @param data 响应数据（自动脱敏）
   */
  traceResponse(method: string, url: string, status: number, duration: number, data?: unknown) {
    if (status < 400) return
    const level = status >= 400 ? 'WARN' : 'INFO'
    const ctx = 'HTTP'
    const msg = `← ${method?.toUpperCase()} ${maskUrl(url)} → ${status} (${duration}ms)`
    if (level === 'WARN') {
      this.warn(ctx, msg, data ? { data: maskBody(data) } : undefined)
    } else {
      this.info(ctx, msg, data ? { data: maskBody(data) } : undefined)
    }
  },
}

// ---- 全局错误监听 ----

const BENIGN_RESIZE_OBSERVER_MESSAGES = [
  'ResizeObserver loop completed with undelivered notifications',
  'ResizeObserver loop limit exceeded',
]

/**
 * 浏览器会在同一帧内发生多轮布局测量时派发该通知。Vue Flow 等依赖
 * ResizeObserver 的组件可能正常触发它，它不代表应用异常或数据丢失。
 */
export function isBenignResizeObserverError(message: unknown): boolean {
  const text = typeof message === 'string' ? message : ''
  return BENIGN_RESIZE_OBSERVER_MESSAGES.some(item => text.includes(item))
}

/**
 * 注册全局错误/未捕获 Promise 异常监听。
 * 在 main.ts 中调用一次即可。
 */
export function setupGlobalErrorLogging() {
  window.addEventListener('error', (event) => {
    const message = event.message || event.error?.message
    if (isBenignResizeObserverError(message)) {
      // 阻止浏览器把非致命的 ResizeObserver 通知继续输出为未捕获错误。
      event.preventDefault()
      return
    }
    logger.error('GLOBAL', 'Uncaught error', {
      message,
      filename: event.filename,
      lineno: event.lineno,
      colno: event.colno,
      error: event.error?.stack || event.error?.message || String(event.error),
    })
  })

  window.addEventListener('unhandledrejection', (event) => {
    const reason = event.reason
    logger.error('GLOBAL', 'Unhandled promise rejection', {
      message: reason?.message || String(reason),
      stack: reason?.stack || '',
    })
  })

  // 拦截 console.error 以捕获第三方库的错误
  console.error = function (...args: unknown[]) {
    const message = args.map(a => (typeof a === 'string' ? maskString(a) : String(a))).join(' ')
    if (isBenignResizeObserverError(message)) return
    logger.error('CONSOLE', message)
    consoleSink.error(...args)
  }

  logger.info('LOGGER', 'Global error logging initialized')
}
