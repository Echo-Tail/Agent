import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useThemeStore } from '@/stores/theme'
import { STORAGE_KEY_THEME } from '@/constants'

beforeEach(() => {
  localStorage.clear()
  setActivePinia(createPinia())
})

describe('useThemeStore', () => {
  it('defaults to light theme', () => {
    const store = useThemeStore()
    expect(store.theme).toBe('light')
    expect(store.isDark).toBe(false)
  })

  it('loads saved theme from localStorage', () => {
    localStorage.setItem(STORAGE_KEY_THEME, 'dark')
    const store = useThemeStore()
    expect(store.theme).toBe('dark')
    expect(store.isDark).toBe(true)
  })

  it('setDark toggles to dark and persists', () => {
    const store = useThemeStore()
    store.setDark(true)
    expect(store.theme).toBe('dark')
    expect(store.isDark).toBe(true)
    expect(localStorage.getItem(STORAGE_KEY_THEME)).toBe('dark')
  })

  it('setDark toggles to light and persists', () => {
    localStorage.setItem(STORAGE_KEY_THEME, 'dark')
    const store = useThemeStore()
    store.setDark(false)
    expect(store.theme).toBe('light')
    expect(store.isDark).toBe(false)
    expect(localStorage.getItem(STORAGE_KEY_THEME)).toBe('light')
  })

  it('toggle switches between dark and light', () => {
    const store = useThemeStore()
    store.toggle()
    expect(store.isDark).toBe(true)
    store.toggle()
    expect(store.isDark).toBe(false)
  })

  it('init applies the theme class to document', () => {
    const store = useThemeStore()
    store.setDark(true)
    store.init()
    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })
})
