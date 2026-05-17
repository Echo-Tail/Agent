import type { LogEntry, LogFilters, LogStats, ILogStorage, LogLevel, LogCategory } from './types'

const DB_NAME = 'ecomagents_logs'
const STORE_NAME = 'logs'
const DB_VERSION = 1
const MAX_ENTRIES = 10000

function openDB(timeoutMs = 5000): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      reject(new Error('IndexedDB open timeout'))
    }, timeoutMs)
    const req = indexedDB.open(DB_NAME, DB_VERSION)
    req.onupgradeneeded = () => {
      const db = req.result
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        const store = db.createObjectStore(STORE_NAME, { keyPath: 'id' })
        store.createIndex('timestamp', 'timestamp', { unique: false })
        store.createIndex('level', 'level', { unique: false })
        store.createIndex('category', 'category', { unique: false })
      }
    }
    req.onsuccess = () => { clearTimeout(timer); resolve(req.result) }
    req.onerror = () => { clearTimeout(timer); reject(req.error) }
  })
}

export class IndexedDBStorage implements ILogStorage {
  /** Quick connectivity check — returns false if IndexedDB is unavailable */
  async probe(timeoutMs = 3000): Promise<boolean> {
    try {
      const db = await openDB(timeoutMs)
      db.close()
      return true
    } catch {
      return false
    }
  }

  async add(entry: LogEntry): Promise<void> {
    const db = await openDB()
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readwrite')
      const store = tx.objectStore(STORE_NAME)
      store.add(entry)
      tx.oncomplete = () => {
        this.evictIfNeeded().finally(() => db.close())
        resolve()
      }
      tx.onerror = () => { db.close(); reject(tx.error) }
    })
  }

  async query(filters: LogFilters): Promise<LogEntry[]> {
    const db = await openDB()
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readonly')
      const store = tx.objectStore(STORE_NAME)
      const all = store.getAll()
      all.onsuccess = () => {
        try {
          db.close()
          let results = all.result as LogEntry[]
          if (filters.levels?.length) {
            results = results.filter((e) => filters.levels!.includes(e.level))
          }
          if (filters.categories?.length) {
            results = results.filter((e) => filters.categories!.includes(e.category))
          }
          if (filters.startDate) {
            results = results.filter((e) => e.timestamp >= filters.startDate!)
          }
          if (filters.endDate) {
            results = results.filter((e) => e.timestamp <= filters.endDate!)
          }
          if (filters.search) {
            const q = filters.search.toLowerCase()
            results = results.filter(
              (e) =>
                e.message.toLowerCase().includes(q) ||
                JSON.stringify(e.data ?? {}).toLowerCase().includes(q),
            )
          }
          results.sort((a, b) => b.timestamp.localeCompare(a.timestamp))
          if (filters.offset) results = results.slice(filters.offset)
          if (filters.limit) results = results.slice(0, filters.limit)
          resolve(results)
        } catch (err) {
          reject(err)
        }
      }
      all.onerror = () => { db.close(); reject(all.error) }
      tx.onerror = () => { db.close(); reject(tx.error) }
    })
  }

  async stats(): Promise<LogStats> {
    const all = await this.query({})
    const byLevel = { DEBUG: 0, INFO: 0, WARN: 0, ERROR: 0 } as Record<LogLevel, number>
    const byCategory = { API: 0, USER_ACTION: 0, ROUTER: 0, ERROR: 0, PERFORMANCE: 0, AUTH: 0 } as Record<LogCategory, number>
    const errors24h: LogEntry[] = []
    const now = Date.now()
    const dayAgo = now - 86400000

    for (const e of all) {
      byLevel[e.level]++
      byCategory[e.category]++
      if (e.level === 'ERROR' && new Date(e.timestamp).getTime() > dayAgo) {
        errors24h.push(e)
      }
    }

    const last24h: Array<{ hour: string; count: number }> = []
    for (let i = 23; i >= 0; i--) {
      const h = new Date(now - i * 3600000)
      const label = `${h.getHours().toString().padStart(2, '0')}:00`
      const count = errors24h.filter(
        (e) => {
          const et = new Date(e.timestamp).getTime()
          const start = now - (i + 1) * 3600000
          const end = now - i * 3600000
          return et >= start && et < end
        },
      ).length
      last24h.push({ hour: label, count })
    }

    return {
      total: all.length,
      byLevel,
      byCategory,
      errorRate: all.length ? byLevel.ERROR / all.length : 0,
      last24h,
    }
  }

  async count(): Promise<number> {
    const db = await openDB()
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readonly')
      const store = tx.objectStore(STORE_NAME)
      const req = store.count()
      req.onsuccess = () => { db.close(); resolve(req.result) }
      req.onerror = () => { db.close(); reject(req.error) }
    })
  }

  async clear(before?: Date): Promise<void> {
    const db = await openDB()
    return new Promise((resolve, reject) => {
      if (before) {
        const tx = db.transaction(STORE_NAME, 'readwrite')
        const store = tx.objectStore(STORE_NAME)
        const index = store.index('timestamp')
        const range = IDBKeyRange.upperBound(before.toISOString())
        index.openCursor(range).onsuccess = (event) => {
          const cursor = (event.target as IDBRequest<IDBCursorWithValue>).result
          if (cursor) {
            cursor.delete()
            cursor.continue()
          }
        }
        tx.oncomplete = () => { db.close(); resolve() }
        tx.onerror = () => { db.close(); reject(tx.error) }
      } else {
        const tx = db.transaction(STORE_NAME, 'readwrite')
        const store = tx.objectStore(STORE_NAME)
        store.clear()
        tx.oncomplete = () => { db.close(); resolve() }
        tx.onerror = () => { db.close(); reject(tx.error) }
      }
    })
  }

  async exportData(filters: LogFilters): Promise<Blob> {
    const entries = await this.query(filters)
    return new Blob([JSON.stringify(entries, null, 2)], { type: 'application/json' })
  }

  private async evictIfNeeded(): Promise<void> {
    const total = await this.count()
    if (total <= MAX_ENTRIES) return
    const excess = total - MAX_ENTRIES
    const db = await openDB()
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readwrite')
      const store = tx.objectStore(STORE_NAME)
      const index = store.index('timestamp')
      let deleted = 0
      index.openCursor(null, 'next').onsuccess = (event) => {
        const cursor = (event.target as IDBRequest<IDBCursorWithValue>).result
        if (cursor && deleted < excess) {
          cursor.delete()
          deleted++
          cursor.continue()
        }
      }
      tx.oncomplete = () => { db.close(); resolve() }
      tx.onerror = () => { db.close(); reject(tx.error) }
    })
  }
}

export class InMemoryStorage implements ILogStorage {
  private entries: LogEntry[] = []

  async add(entry: LogEntry): Promise<void> {
    this.entries.push(entry)
    if (this.entries.length > MAX_ENTRIES) {
      this.entries = this.entries.slice(-MAX_ENTRIES)
    }
  }

  async query(filters: LogFilters): Promise<LogEntry[]> {
    let results = [...this.entries]
    if (filters.levels?.length) {
      results = results.filter((e) => filters.levels!.includes(e.level))
    }
    if (filters.categories?.length) {
      results = results.filter((e) => filters.categories!.includes(e.category))
    }
    if (filters.startDate) {
      results = results.filter((e) => e.timestamp >= filters.startDate!)
    }
    if (filters.endDate) {
      results = results.filter((e) => e.timestamp <= filters.endDate!)
    }
    if (filters.search) {
      const q = filters.search.toLowerCase()
      results = results.filter(
        (e) =>
          e.message.toLowerCase().includes(q) ||
          JSON.stringify(e.data ?? {}).toLowerCase().includes(q),
      )
    }
    results.sort((a, b) => b.timestamp.localeCompare(a.timestamp))
    if (filters.offset) results = results.slice(filters.offset)
    if (filters.limit) results = results.slice(0, filters.limit)
    return results
  }

  async stats(): Promise<LogStats> {
    const all = [...this.entries]
    const byLevel = { DEBUG: 0, INFO: 0, WARN: 0, ERROR: 0 } as Record<LogLevel, number>
    const byCategory = { API: 0, USER_ACTION: 0, ROUTER: 0, ERROR: 0, PERFORMANCE: 0, AUTH: 0 } as Record<LogCategory, number>
    const errors24h: LogEntry[] = []
    const now = Date.now()
    const dayAgo = now - 86400000

    for (const e of all) {
      byLevel[e.level]++
      byCategory[e.category]++
      if (e.level === 'ERROR' && new Date(e.timestamp).getTime() > dayAgo) {
        errors24h.push(e)
      }
    }

    const last24h: Array<{ hour: string; count: number }> = []
    for (let i = 23; i >= 0; i--) {
      const h = new Date(now - i * 3600000)
      const label = `${h.getHours().toString().padStart(2, '0')}:00`
      const count = errors24h.filter((e) => {
        const et = new Date(e.timestamp).getTime()
        const start = now - (i + 1) * 3600000
        const end = now - i * 3600000
        return et >= start && et < end
      }).length
      last24h.push({ hour: label, count })
    }

    return {
      total: all.length,
      byLevel,
      byCategory,
      errorRate: all.length ? byLevel.ERROR / all.length : 0,
      last24h,
    }
  }

  async count(): Promise<number> {
    return this.entries.length
  }

  async clear(before?: Date): Promise<void> {
    if (before) {
      this.entries = this.entries.filter((e) => new Date(e.timestamp).getTime() > before.getTime())
    } else {
      this.entries = []
    }
  }

  async exportData(filters: LogFilters): Promise<Blob> {
    const entries = await this.query(filters)
    return new Blob([JSON.stringify(entries, null, 2)], { type: 'application/json' })
  }
}
