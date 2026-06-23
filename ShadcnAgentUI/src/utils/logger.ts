/**
 * 前端日志工具 — 统一管理控制台日志，方便排查问题。
 * 日志级别：DEBUG < INFO < WARN < ERROR
 * 生产环境默认只显示 WARN 及以上级别。
 */

type LogLevel = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR'

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
  } catch {}
  // Production: default to WARN, development: default to DEBUG
  return import.meta.env.DEV ? LOG_LEVELS['DEBUG'] : LOG_LEVELS['WARN']
}

const LEVEL = getEffectiveLevel()

function formatMessage(level: LogLevel, context: string, message: string, data?: unknown): string {
  const ts = new Date().toISOString()
  const prefix = `[${ts}] [${level}] [${context}]`
  if (data !== undefined) {
    return `${prefix} ${message} ${typeof data === 'string' ? data : JSON.stringify(data)}`
  }
  return `${prefix} ${message}`
}

function log(level: LogLevel, context: string, message: string, data?: unknown) {
  if (LOG_LEVELS[level] < LEVEL) return
  const formatted = formatMessage(level, context, message, data)
  switch (level) {
    case 'ERROR':
      console.error(formatted)
      break
    case 'WARN':
      console.warn(formatted)
      break
    case 'DEBUG':
      console.debug(formatted)
      break
    default:
      console.log(formatted)
  }
}

export const logger = {
  debug: (ctx: string, msg: string, data?: unknown) => log('DEBUG', ctx, msg, data),
  info: (ctx: string, msg: string, data?: unknown) => log('INFO', ctx, msg, data),
  warn: (ctx: string, msg: string, data?: unknown) => log('WARN', ctx, msg, data),
  error: (ctx: string, msg: string, data?: unknown) => log('ERROR', ctx, msg, data),
}

/**
 * 注册全局错误/未捕获 Promise 异常监听。
 * 在 main.ts 中调用一次即可。
 */
export function setupGlobalErrorLogging() {
  window.addEventListener('error', (event) => {
    logger.error('GLOBAL', 'Uncaught error', {
      message: event.message,
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

  // Vue router errors (if available)
  const originalOnError = console.error
  console.error = function (...args: unknown[]) {
    logger.error('CONSOLE', args.map(a => String(a)).join(' '))
    originalOnError.apply(console, args)
  }

  logger.info('LOGGER', 'Global error logging initialized')
}
