export const LOG_LEVELS = ['DEBUG', 'INFO', 'WARN', 'ERROR'] as const
export type LogLevel = (typeof LOG_LEVELS)[number]

export const LOG_CATEGORIES = ['API', 'USER_ACTION', 'ROUTER', 'ERROR', 'PERFORMANCE', 'AUTH'] as const
export type LogCategory = (typeof LOG_CATEGORIES)[number]

export interface LogEntry {
  id: string
  timestamp: string
  level: LogLevel
  category: LogCategory
  message: string
  data?: Record<string, unknown>
  duration?: number
  route?: string
  userId?: number
}

export interface LogFilters {
  levels?: LogLevel[]
  categories?: LogCategory[]
  startDate?: string
  endDate?: string
  search?: string
  offset?: number
  limit?: number
}

export interface LogStats {
  total: number
  byLevel: Record<LogLevel, number>
  byCategory: Record<LogCategory, number>
  errorRate: number
  last24h: Array<{ hour: string; count: number }>
}

export interface ILogStorage {
  add(entry: LogEntry): Promise<void>
  query(filters: LogFilters): Promise<LogEntry[]>
  stats(): Promise<LogStats>
  count(): Promise<number>
  clear(before?: Date): Promise<void>
  exportData(filters: LogFilters): Promise<Blob>
}
