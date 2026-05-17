import type { LogEntry, LogLevel, LogCategory, ILogStorage, LogFilters, LogStats } from './types'
import { maskPII } from './pii'
import { InMemoryStorage } from './storage'

let storage: ILogStorage = new InMemoryStorage()
let currentRoute = ''
let currentUserId: number | undefined

export function initLogger(s: ILogStorage) {
  storage = s
}

export function setLogRoute(route: string) {
  currentRoute = route
}

export function setLogUserId(id: number | undefined) {
  currentUserId = id
}

function createEntry(level: LogLevel, category: LogCategory, message: string, data?: Record<string, unknown>, duration?: number): LogEntry {
  return {
    id: crypto.randomUUID(),
    timestamp: new Date().toISOString(),
    level,
    category,
    message,
    data: data ? (maskPII(data) as Record<string, unknown>) : undefined,
    duration,
    route: currentRoute || undefined,
    userId: currentUserId,
  }
}

export async function log(level: LogLevel, category: LogCategory, message: string, data?: Record<string, unknown>, duration?: number) {
  const entry = createEntry(level, category, message, data, duration)
  await storage.add(entry)
}

export async function getLogs(filters: LogFilters = {}): Promise<LogEntry[]> {
  return storage.query(filters)
}

export async function getLogStats(): Promise<LogStats> {
  return storage.stats()
}

export async function clearLogs(before?: Date): Promise<void> {
  return storage.clear(before)
}

export async function exportLogs(filters: LogFilters = {}): Promise<Blob> {
  return storage.exportData(filters)
}
