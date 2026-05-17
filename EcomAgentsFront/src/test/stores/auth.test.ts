import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '../../stores/auth'
import { STORAGE_KEY_TOKEN, STORAGE_KEY_USER } from '../../constants'
import type { UserDTO } from '../../types/api'

vi.mock('../../api/auth', () => ({
  loginApi: vi.fn(),
  registerApi: vi.fn(),
}))

const mockUser: UserDTO = {
  id: 1, username: 'admin', email: 'admin@test.com',
  role: 'admin', status: 'active', createdAt: '2024-01-01',
}

const mockToken = 'mock-token-1'

beforeEach(() => {
  localStorage.clear()
  setActivePinia(createPinia())
})

describe('useAuthStore', () => {
  it('starts with no user when storage is empty', () => {
    const store = useAuthStore()
    expect(store.token).toBeNull()
    expect(store.currentUser).toBeNull()
    expect(store.isAuthenticated).toBe(false)
    expect(store.isAdmin).toBe(false)
  })

  it('hydrates from localStorage on init', () => {
    localStorage.setItem(STORAGE_KEY_TOKEN, mockToken)
    localStorage.setItem(STORAGE_KEY_USER, JSON.stringify(mockUser))

    const store = useAuthStore()
    expect(store.token).toBe(mockToken)
    expect(store.currentUser).toEqual(mockUser)
    expect(store.isAuthenticated).toBe(true)
    expect(store.isAdmin).toBe(true)
  })

  describe('login', () => {
    it('succeeds and persists data', async () => {
      const { loginApi } = await import('../../api/auth')
      vi.mocked(loginApi).mockResolvedValue({
        data: { code: 200, message: 'ok', data: { user: mockUser, token: mockToken } },
      } as any)

      const store = useAuthStore()
      const result = await store.login({ username: 'admin', password: '123456' })

      expect(result.success).toBe(true)
      if (result.success) {
        expect(result.user).toEqual(mockUser)
      }
      expect(store.token).toBe(mockToken)
      expect(store.currentUser).toEqual(mockUser)
      expect(localStorage.getItem(STORAGE_KEY_TOKEN)).toBe(mockToken)
      expect(localStorage.getItem(STORAGE_KEY_USER)).toBe(JSON.stringify(mockUser))
    })

    it('fails with error message', async () => {
      const { loginApi } = await import('../../api/auth')
      vi.mocked(loginApi).mockResolvedValue({
        data: { code: 401, message: '用户名或密码错误', data: null },
      } as any)

      const store = useAuthStore()
      const result = await store.login({ username: 'bad', password: 'wrong' })

      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.message).toBe('用户名或密码错误')
      }
      expect(store.token).toBeNull()
    })
  })

  describe('register', () => {
    it('succeeds', async () => {
      const { registerApi } = await import('../../api/auth')
      vi.mocked(registerApi).mockResolvedValue({
        data: { code: 200, message: '注册成功', data: mockUser },
      } as any)

      const store = useAuthStore()
      const result = await store.register({ username: 'new', password: '123456', inviteCode: 'CODE' })

      expect(result.success).toBe(true)
    })

    it('fails with error message', async () => {
      const { registerApi } = await import('../../api/auth')
      vi.mocked(registerApi).mockResolvedValue({
        data: { code: 400, message: '邀请码无效', data: null },
      } as any)

      const store = useAuthStore()
      const result = await store.register({ username: 'new', password: '123456', inviteCode: 'BAD' })

      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.message).toBe('邀请码无效')
      }
    })
  })

  describe('logout', () => {
    it('clears user and token', () => {
      localStorage.setItem(STORAGE_KEY_TOKEN, mockToken)
      localStorage.setItem(STORAGE_KEY_USER, JSON.stringify(mockUser))

      const store = useAuthStore()
      store.logout()

      expect(store.token).toBeNull()
      expect(store.currentUser).toBeNull()
      expect(store.isAuthenticated).toBe(false)
      expect(localStorage.getItem(STORAGE_KEY_TOKEN)).toBeNull()
      expect(localStorage.getItem(STORAGE_KEY_USER)).toBeNull()
    })
  })

  describe('isAdmin', () => {
    it('is true when role is admin', () => {
      localStorage.setItem(STORAGE_KEY_USER, JSON.stringify(mockUser))
      const store = useAuthStore()
      expect(store.isAdmin).toBe(true)
    })

    it('is false when role is user', () => {
      localStorage.setItem(STORAGE_KEY_USER, JSON.stringify({ ...mockUser, role: 'user' }))
      const store = useAuthStore()
      expect(store.isAdmin).toBe(false)
    })
  })
})
