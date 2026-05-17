import { describe, it, expect } from 'vitest'
import { maskPII } from '../../../utils/logging/pii'

describe('maskPII', () => {
  it('masks username field', () => {
    const result = maskPII({ username: 'admin', role: 'admin' }) as Record<string, unknown>
    expect(result.username).toBe('***')
    expect(result.role).toBe('admin')
  })

  it('masks email field', () => {
    const result = maskPII({ email: 'user@example.com' }) as Record<string, unknown>
    expect(result.email).toBe('***')
  })

  it('masks password field', () => {
    const result = maskPII({ password: '123456' }) as Record<string, unknown>
    expect(result.password).toBe('***')
  })

  it('masks apiKey field', () => {
    const result = maskPII({ apiKey: 'sk-xxxxxxxx' }) as Record<string, unknown>
    expect(result.apiKey).toBe('***')
  })

  it('masks token and authorization fields', () => {
    const result = maskPII({ token: 'abc', authorization: 'Bearer xxx' }) as Record<string, unknown>
    expect(result.token).toBe('***')
    expect(result.authorization).toBe('***')
  })

  it('masks case-insensitively', () => {
    const result = maskPII({ UserName: 'admin', PASSWORD: '123' }) as Record<string, unknown>
    expect(result.UserName).toBe('***')
    expect(result.PASSWORD).toBe('***')
  })

  it('masks nested sensitive fields', () => {
    const data = { user: { username: 'admin', email: 'a@b.com' }, request: { headers: { authorization: 'Bearer token' } } }
    const result = maskPII(data) as Record<string, unknown>
    expect((result.user as Record<string, unknown>).username).toBe('***')
    expect((result.user as Record<string, unknown>).email).toBe('***')
    expect((result.request as Record<string, unknown>).headers).toEqual({ authorization: '***' })
  })

  it('masks sensitive fields in arrays', () => {
    const data = [{ username: 'user1' }, { username: 'user2' }]
    const result = maskPII(data) as Array<Record<string, unknown>>
    expect(result[0].username).toBe('***')
    expect(result[1].username).toBe('***')
  })

  it('preserves non-sensitive primitive values', () => {
    const data = { id: 1, name: 'test', active: true, count: 42 }
    const result = maskPII(data) as Record<string, unknown>
    expect(result.id).toBe(1)
    expect(result.name).toBe('test')
    expect(result.active).toBe(true)
    expect(result.count).toBe(42)
  })

  it('returns primitive values as-is', () => {
    expect(maskPII('string')).toBe('string')
    expect(maskPII(42)).toBe(42)
    expect(maskPII(null)).toBe(null)
    expect(maskPII(undefined)).toBe(undefined)
  })

  it('returns empty object unchanged', () => {
    expect(maskPII({})).toEqual({})
  })
})
