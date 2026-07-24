import { describe, expect, it } from 'vitest'
import { isBenignResizeObserverError } from '@/utils/logger'

describe('isBenignResizeObserverError', () => {
  it('recognizes browser ResizeObserver loop notifications', () => {
    expect(isBenignResizeObserverError(
      'ResizeObserver loop completed with undelivered notifications.',
    )).toBe(true)
    expect(isBenignResizeObserverError('ResizeObserver loop limit exceeded')).toBe(true)
  })

  it('does not hide real runtime errors', () => {
    expect(isBenignResizeObserverError('Cannot read properties of undefined')).toBe(false)
    expect(isBenignResizeObserverError(null)).toBe(false)
  })
})
