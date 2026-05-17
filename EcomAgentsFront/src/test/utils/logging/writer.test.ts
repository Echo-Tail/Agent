import { describe, it, expect, beforeEach } from 'vitest'
import { initLogger, log, getLogs, getLogStats, clearLogs, exportLogs, setLogRoute, setLogUserId } from '../../../utils/logging/writer'
import { InMemoryStorage } from '../../../utils/logging/storage'
import type { LogEntry } from '../../../utils/logging/types'

describe('LogWriter', () => {
  beforeEach(() => {
    initLogger(new InMemoryStorage())
  })

  it('writes and reads back a log entry', async () => {
    await log('INFO', 'API', 'test message', { key: 'value' }, 100)
    const entries = await getLogs()
    expect(entries).toHaveLength(1)
    expect(entries[0].level).toBe('INFO')
    expect(entries[0].category).toBe('API')
    expect(entries[0].message).toBe('test message')
  })

  it('assigns id, timestamp, and shape to each entry', async () => {
    await log('DEBUG', 'PERFORMANCE', 'entry shape test')
    const entries = await getLogs()
    const e = entries[0]
    expect(e.id).toBeDefined()
    expect(typeof e.id).toBe('string')
    expect(e.id.length).toBeGreaterThan(0)
    expect(e.timestamp).toBeDefined()
    expect(new Date(e.timestamp).getTime()).not.toBeNaN()
    expect(e.level).toBe('DEBUG')
    expect(e.category).toBe('PERFORMANCE')
    expect(e.message).toBe('entry shape test')
  })

  it('writes entries at all four log levels', async () => {
    await log('DEBUG', 'API', 'debug')
    await log('INFO', 'API', 'info')
    await log('WARN', 'API', 'warn')
    await log('ERROR', 'API', 'error')
    const entries = await getLogs()
    expect(entries).toHaveLength(4)
    const levels = entries.map((e) => e.level).sort()
    expect(levels).toEqual(['DEBUG', 'ERROR', 'INFO', 'WARN'])
  })

  it('writes entries in all categories', async () => {
    await log('INFO', 'API', 'a')
    await log('INFO', 'USER_ACTION', 'b')
    await log('INFO', 'ROUTER', 'c')
    await log('INFO', 'ERROR', 'd')
    await log('INFO', 'PERFORMANCE', 'e')
    await log('INFO', 'AUTH', 'f')
    const entries = await getLogs()
    expect(entries).toHaveLength(6)
    const cats = entries.map((e) => e.category).sort()
    expect(cats).toEqual(['API', 'AUTH', 'ERROR', 'PERFORMANCE', 'ROUTER', 'USER_ACTION'])
  })

  it('returns entries in reverse chronological order', async () => {
    await log('INFO', 'API', 'first')
    await new Promise((r) => setTimeout(r, 10))
    await log('INFO', 'API', 'second')
    await new Promise((r) => setTimeout(r, 10))
    await log('INFO', 'API', 'third')
    const entries = await getLogs()
    expect(entries[0].message).toBe('third')
    expect(entries[1].message).toBe('second')
    expect(entries[2].message).toBe('first')
  })
})

describe('getLogs - filters', () => {
  beforeEach(async () => {
    initLogger(new InMemoryStorage())
    await log('INFO', 'API', 'api call', { url: '/test' })
    await log('WARN', 'API', 'slow response', { durationMs: 3000 })
    await log('ERROR', 'API', 'request failed', { status: 500 })
    await log('INFO', 'AUTH', 'user login')
    await log('INFO', 'ROUTER', 'navigated to dashboard')
  })

  it('returns all entries with no filters', async () => {
    const entries = await getLogs()
    expect(entries).toHaveLength(5)
  })

  it('filters by single level', async () => {
    const entries = await getLogs({ levels: ['ERROR'] })
    expect(entries).toHaveLength(1)
    expect(entries[0].level).toBe('ERROR')
  })

  it('filters by multiple levels', async () => {
    const entries = await getLogs({ levels: ['INFO', 'WARN'] })
    expect(entries).toHaveLength(4)
    for (const e of entries) {
      expect(['INFO', 'WARN']).toContain(e.level)
    }
  })

  it('filters by category', async () => {
    const entries = await getLogs({ categories: ['AUTH'] })
    expect(entries).toHaveLength(1)
    expect(entries[0].category).toBe('AUTH')
  })

  it('filters by text search in message', async () => {
    const entries = await getLogs({ search: 'login' })
    expect(entries).toHaveLength(1)
    expect(entries[0].message).toBe('user login')
  })

  it('filters by text search in data', async () => {
    const entries = await getLogs({ search: '/test' })
    expect(entries).toHaveLength(1)
    expect(entries[0].message).toBe('api call')
  })

  it('filters with combined level and category', async () => {
    const entries = await getLogs({ levels: ['INFO'], categories: ['API'] })
    expect(entries).toHaveLength(1)
    expect(entries[0].level).toBe('INFO')
    expect(entries[0].category).toBe('API')
  })

  it('applies offset and limit', async () => {
    const all = await getLogs()
    const limited = await getLogs({ limit: 2 })
    expect(limited).toHaveLength(2)
    expect(limited[0].id).toBe(all[0].id)
    expect(limited[1].id).toBe(all[1].id)

    const offset = await getLogs({ offset: 2 })
    expect(offset).toHaveLength(3)
    expect(offset[0].id).toBe(all[2].id)
  })

  it('filters by date range', async () => {
    const now = new Date()
    const past = new Date(now.getTime() - 3600000)
    const future = new Date(now.getTime() + 3600000)
    const entries = await getLogs({ startDate: past.toISOString(), endDate: future.toISOString() })
    expect(entries).toHaveLength(5)
  })

  it('returns empty array when no matches', async () => {
    const entries = await getLogs({ search: 'nonexistent' })
    expect(entries).toHaveLength(0)
  })
})

describe('getLogStats', () => {
  beforeEach(async () => {
    initLogger(new InMemoryStorage())
    await log('INFO', 'API', 'api call')
    await log('WARN', 'API', 'slow')
    await log('ERROR', 'API', 'fail')
    await log('ERROR', 'AUTH', 'auth fail')
    await log('INFO', 'ROUTER', 'nav')
  })

  it('returns total count', async () => {
    const stats = await getLogStats()
    expect(stats.total).toBe(5)
  })

  it('returns correct byLevel breakdown', async () => {
    const stats = await getLogStats()
    expect(stats.byLevel.INFO).toBe(2)
    expect(stats.byLevel.WARN).toBe(1)
    expect(stats.byLevel.ERROR).toBe(2)
    expect(stats.byLevel.DEBUG).toBe(0)
  })

  it('returns correct byCategory breakdown', async () => {
    const stats = await getLogStats()
    expect(stats.byCategory.API).toBe(3)
    expect(stats.byCategory.AUTH).toBe(1)
    expect(stats.byCategory.ROUTER).toBe(1)
    expect(stats.byCategory.USER_ACTION).toBe(0)
    expect(stats.byCategory.ERROR).toBe(0)
    expect(stats.byCategory.PERFORMANCE).toBe(0)
  })

  it('calculates error rate', async () => {
    const stats = await getLogStats()
    expect(stats.errorRate).toBeCloseTo(0.4, 2)
  })

  it('returns zero error rate when no entries', async () => {
    initLogger(new InMemoryStorage())
    const stats = await getLogStats()
    expect(stats.errorRate).toBe(0)
  })

  it('returns last24h array with 24 buckets', async () => {
    const stats = await getLogStats()
    expect(stats.last24h).toHaveLength(24)
    for (const bucket of stats.last24h) {
      expect(bucket.hour).toMatch(/^\d{2}:00$/)
      expect(typeof bucket.count).toBe('number')
    }
  })
})

describe('PII integration', () => {
  beforeEach(() => {
    initLogger(new InMemoryStorage())
  })

  it('masks username in data', async () => {
    await log('INFO', 'API', 'login', { username: 'admin' })
    const entries = await getLogs()
    expect((entries[0].data as Record<string, unknown>)?.username).toBe('***')
  })

  it('masks password in data', async () => {
    await log('INFO', 'AUTH', 'login', { password: 'secret123' })
    const entries = await getLogs()
    expect((entries[0].data as Record<string, unknown>)?.password).toBe('***')
  })

  it('preserves non-sensitive data fields', async () => {
    await log('INFO', 'API', 'request', { id: 42, name: 'test' })
    const entries = await getLogs()
    const data = entries[0].data as Record<string, unknown>
    expect(data.id).toBe(42)
    expect(data.name).toBe('test')
  })

  it('handles log entry with no data', async () => {
    await log('INFO', 'API', 'simple message')
    const entries = await getLogs()
    expect(entries[0].data).toBeUndefined()
  })
})

describe('route and userId tracking', () => {
  beforeEach(() => {
    initLogger(new InMemoryStorage())
    setLogRoute('')
    setLogUserId(undefined)
  })

  it('records current route on entries', async () => {
    setLogRoute('Dashboard')
    await log('INFO', 'USER_ACTION', 'click')
    const entries = await getLogs()
    expect(entries[0].route).toBe('Dashboard')
  })

  it('records current userId on entries', async () => {
    setLogUserId(42)
    await log('INFO', 'API', 'action')
    const entries = await getLogs()
    expect(entries[0].userId).toBe(42)
  })

  it('updates route for subsequent entries', async () => {
    setLogRoute('Dashboard')
    await log('INFO', 'ROUTER', 'first')
    await new Promise((r) => setTimeout(r, 5))
    setLogRoute('Chat')
    await log('INFO', 'ROUTER', 'second')
    const entries = await getLogs()
    expect(entries[0].route).toBe('Chat')
    expect(entries[1].route).toBe('Dashboard')
  })

  it('leaves route undefined when not set', async () => {
    await log('INFO', 'API', 'orphan')
    const entries = await getLogs()
    expect(entries[0].route).toBeUndefined()
  })

  it('leaves userId undefined when not set', async () => {
    await log('INFO', 'API', 'orphan')
    const entries = await getLogs()
    expect(entries[0].userId).toBeUndefined()
  })
})

describe('clearLogs', () => {
  beforeEach(async () => {
    initLogger(new InMemoryStorage())
    await log('INFO', 'API', 'keep me')
  })

  it('clears all entries', async () => {
    expect(await (await getLogs()).length).toBe(1)
    await clearLogs()
    expect(await getLogs()).toHaveLength(0)
  })

  it('clears entries before a given date', async () => {
    const future = new Date(Date.now() + 86400000)
    await clearLogs(future)
    expect(await getLogs()).toHaveLength(0)
  })

  it('keeps entries after a given date', async () => {
    const past = new Date(Date.now() - 86400000)
    await clearLogs(past)
    expect(await getLogs()).toHaveLength(1)
  })
})

describe('exportLogs', () => {
  beforeEach(async () => {
    initLogger(new InMemoryStorage())
    await log('INFO', 'API', 'export test')
  })

  it('returns a Blob with content-type application/json', async () => {
    const blob = await exportLogs()
    expect(blob).toBeInstanceOf(Blob)
    expect(blob.type).toBe('application/json')
  })

  it('includes log entries in JSON body', async () => {
    const blob = await exportLogs()
    const text = await blob.text()
    const parsed = JSON.parse(text) as LogEntry[]
    expect(parsed).toHaveLength(1)
    expect(parsed[0].message).toBe('export test')
  })

  it('respects filters when exporting', async () => {
    const blob = await exportLogs({ levels: ['ERROR'] })
    const text = await blob.text()
    const parsed = JSON.parse(text) as LogEntry[]
    expect(parsed).toHaveLength(0)
  })
})
