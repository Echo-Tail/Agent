import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useChatStore } from '../../stores/chat'
import type { SessionFolder } from '../../types/session'

vi.mock('../../api/session', () => ({
  listSessionsApi: vi.fn(),
  getSessionApi: vi.fn(),
  createSessionApi: vi.fn(),
  updateSessionApi: vi.fn(),
  deleteSessionApi: vi.fn(),
  listFoldersApi: vi.fn(),
  createFolderApi: vi.fn(),
  updateFolderApi: vi.fn(),
  deleteFolderApi: vi.fn(),
  streamChat: vi.fn(),
}))

beforeEach(() => {
  setActivePinia(createPinia())
})

describe('useChatStore', () => {
  describe('folderTree', () => {
    it('returns flat folder list sorted by orderNum', () => {
      const store = useChatStore()
      store.folders = [
        { id: 1, name: 'A', orderNum: 0 },
        { id: 2, name: 'B', orderNum: 1 },
      ]
      expect(store.folderTree).toHaveLength(2)
      expect(store.folderTree[0].name).toBe('A')
      expect(store.folderTree[1].name).toBe('B')
    })

    it('sorts by orderNum', () => {
      const store = useChatStore()
      store.folders = [
        { id: 2, name: 'Second', orderNum: 1 },
        { id: 1, name: 'First', orderNum: 0 },
        { id: 3, name: 'Third', orderNum: 2 },
      ]
      expect(store.folderTree.map((f) => f.name)).toEqual(['First', 'Second', 'Third'])
    })

    it('returns empty array when no folders', () => {
      const store = useChatStore()
      expect(store.folderTree).toEqual([])
    })
  })

  describe('fetchFolders', () => {
    it('populates folders on success', async () => {
      const { listFoldersApi } = await import('../../api/session')
      const mockFolders: SessionFolder[] = [
        { id: 1, name: '客服', orderNum: 0 },
      ]
      vi.mocked(listFoldersApi).mockResolvedValue({
        data: { code: 200, message: 'ok', data: mockFolders },
      } as any)

      const store = useChatStore()
      await store.fetchFolders()

      expect(store.folders).toEqual(mockFolders)
    })

    it('does not throw on API error', async () => {
      const { listFoldersApi } = await import('../../api/session')
      vi.mocked(listFoldersApi).mockRejectedValue(new Error('fail'))

      const store = useChatStore()
      await expect(store.fetchFolders()).resolves.toBeUndefined()
      expect(store.folders).toEqual([])
    })
  })

  describe('session management', () => {
    it('fetchSessions populates sessions on success', async () => {
      const { listSessionsApi } = await import('../../api/session')
      const mockSessions = [
        { id: 1, agentId: 1, title: 'Chat 1', folderId: null, tags: [],
          messageCount: 0, lastMessage: null, createdAt: '', updatedAt: '' },
      ]
      vi.mocked(listSessionsApi).mockResolvedValue({
        data: { code: 200, message: 'ok', data: mockSessions },
      } as any)

      const store = useChatStore()
      expect(store.sessionLoading).toBe(false)
      const promise = store.fetchSessions()
      expect(store.sessionLoading).toBe(true)
      await promise

      expect(store.sessions).toHaveLength(1)
      expect(store.sessionLoading).toBe(false)
    })

    it('createSession sets active session and messages', async () => {
      const { createSessionApi } = await import('../../api/session')
      const newSession = {
        id: 10, agentId: 1, title: '新对话', folderId: null,
        tags: [], messages: [], createdAt: '', updatedAt: '',
      }
      vi.mocked(createSessionApi).mockResolvedValue({
        data: { code: 200, message: 'ok', data: newSession },
      } as any)

      const store = useChatStore()
      const result = await store.createSession(1)

      expect(result).toEqual(newSession)
      expect(store.activeSession).toEqual(newSession)
      expect(store.messages).toEqual([])
    })

    it('removeSession clears active session when deleted', async () => {
      const { deleteSessionApi, listSessionsApi } = await import('../../api/session')

      const store = useChatStore()
      store.activeSession = { id: 5 } as any

      vi.mocked(deleteSessionApi).mockResolvedValue({
        data: { code: 200, message: 'ok', data: null },
      } as any)
      vi.mocked(listSessionsApi).mockResolvedValue({
        data: { code: 200, message: 'ok', data: [] },
      } as any)

      const result = await store.removeSession(5)

      expect(result).toBe(true)
      expect(store.activeSession).toBeNull()
      expect(store.messages).toEqual([])
    })
  })

  describe('folder management', () => {
    it('addFolder calls create then refetch', async () => {
      const { createFolderApi, listFoldersApi } = await import('../../api/session')
      vi.mocked(createFolderApi).mockResolvedValue({
        data: { code: 200, message: 'ok', data: null },
      } as any)
      vi.mocked(listFoldersApi).mockResolvedValue({
        data: { code: 200, message: 'ok', data: [{ id: 1, name: 'New', orderNum: 0 }] },
      } as any)

      const store = useChatStore()
      const result = await store.addFolder('New Folder')

      expect(result).toBe(true)
      expect(store.folders).toHaveLength(1)
      expect(store.folders[0].name).toBe('New')
    })
  })

  describe('clearActiveSession', () => {
    it('resets session, messages, and input', () => {
      const store = useChatStore()
      store.activeSession = { id: 1 } as any
      store.messages = [{ role: 'user', content: 'hi', timestamp: '' }]
      store.inputText = 'typing...'

      store.clearActiveSession()

      expect(store.activeSession).toBeNull()
      expect(store.messages).toEqual([])
      expect(store.inputText).toBe('')
    })
  })
})
