import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useChatStore } from '@/stores/chat'

vi.mock('@/api/session', () => ({
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
      const { listFoldersApi } = await import('@/api/session')
      const mockFolders = [{ id: 1, name: '客服', orderNum: 0 }]
      vi.mocked(listFoldersApi).mockResolvedValue(mockFolders as any)

      const store = useChatStore()
      await store.fetchFolders()

      expect(store.folders).toEqual(mockFolders)
    })

    it('does not throw on API error', async () => {
      const { listFoldersApi } = await import('@/api/session')
      vi.mocked(listFoldersApi).mockRejectedValue(new Error('fail'))

      const store = useChatStore()
      await expect(store.fetchFolders()).resolves.toBeUndefined()
      expect(store.folders).toEqual([])
    })
  })

  describe('session management', () => {
    it('fetchSessions populates sessions on success', async () => {
      const { listSessionsApi } = await import('@/api/session')
      const mockSessions = [
        { id: 1, agentId: 1, title: 'Chat 1', folderId: null, tags: [],
          messageCount: 0, lastMessage: null, createdAt: '', updatedAt: '' },
      ]
      vi.mocked(listSessionsApi).mockResolvedValue(mockSessions as any)

      const store = useChatStore()
      expect(store.sessionLoading).toBe(false)
      const promise = store.fetchSessions()
      expect(store.sessionLoading).toBe(true)
      await promise

      expect(store.sessions).toHaveLength(1)
      expect(store.sessionLoading).toBe(false)
    })

    it('createSession sets active session and messages', async () => {
      const { createSessionApi } = await import('@/api/session')
      const newSession = {
        id: 10, agentId: 1, title: '新对话', folderId: null,
        tags: [], messages: [], createdAt: '', updatedAt: '',
      }
      vi.mocked(createSessionApi).mockResolvedValue(newSession as any)

      const store = useChatStore()
      const result = await store.createSession(1)

      expect(result).toEqual(newSession)
      expect(store.activeSession).toEqual(newSession)
      expect(store.messages).toEqual([])
    })

    it('createSession throws on failure', async () => {
      const { createSessionApi } = await import('@/api/session')
      vi.mocked(createSessionApi).mockRejectedValue(new Error('创建失败'))

      const store = useChatStore()
      await expect(store.createSession(1)).rejects.toThrow('创建失败')
    })

    it('loadSession loads session and messages', async () => {
      const { getSessionApi } = await import('@/api/session')
      const mockSession = {
        id: 1, agentId: 1, title: 'Chat',
        messages: [{ role: 'user', content: 'hi', timestamp: '2024-01-01' }],
        createdAt: '', updatedAt: '',
      }
      vi.mocked(getSessionApi).mockResolvedValue(mockSession as any)

      const store = useChatStore()
      await store.loadSession(1)

      expect(store.activeSession).toEqual(mockSession)
      expect(store.messages).toHaveLength(1)
    })

    it('switchToAgent can preserve the active history session', async () => {
      const store = useChatStore()
      const mockSession = {
        id: 1, agentId: 1, title: 'Chat',
        messages: [{ role: 'user', content: 'hi', timestamp: '2024-01-01' }],
        createdAt: '', updatedAt: '',
      }
      store.activeSession = mockSession as any
      store.messages = mockSession.messages as any

      store.switchToAgent(1, { preserveSession: true })

      expect(store.activeAgentId).toBe(1)
      expect(store.activeSession).toEqual(mockSession)
      expect(store.messages).toHaveLength(1)
    })

    it('removeSession clears active session when deleted', async () => {
      const { deleteSessionApi, listSessionsApi } = await import('@/api/session')

      const store = useChatStore()
      store.activeSession = { id: 5 } as any

      vi.mocked(deleteSessionApi).mockResolvedValue(undefined as any)
      vi.mocked(listSessionsApi).mockResolvedValue([] as any)

      const result = await store.removeSession(5)

      expect(result).toBe(true)
      expect(store.activeSession).toBeNull()
      expect(store.messages).toEqual([])
    })

    it('updateSession calls API and refreshes', async () => {
      const { updateSessionApi, listSessionsApi } = await import('@/api/session')
      vi.mocked(updateSessionApi).mockResolvedValue(undefined as any)
      vi.mocked(listSessionsApi).mockResolvedValue([] as any)

      const store = useChatStore()
      const result = await store.updateSession(1, { title: '新标题' })

      expect(result).toBe(true)
    })

    it('updateSession throws on failure', async () => {
      const { updateSessionApi } = await import('@/api/session')
      vi.mocked(updateSessionApi).mockRejectedValue(new Error('fail'))

      const store = useChatStore()
      await expect(store.updateSession(1, { title: '新标题' })).rejects.toThrow('fail')
    })
  })

  describe('folder management', () => {
    it('addFolder calls create then refetch', async () => {
      const { createFolderApi, listFoldersApi } = await import('@/api/session')
      vi.mocked(createFolderApi).mockResolvedValue({ id: 1, name: 'New', orderNum: 0 } as any)
      vi.mocked(listFoldersApi).mockResolvedValue([{ id: 1, name: 'New', orderNum: 0 }] as any)

      const store = useChatStore()
      const result = await store.addFolder('New Folder')

      expect(result).toBe(true)
      expect(store.folders).toHaveLength(1)
      expect(store.folders[0].name).toBe('New')
    })

    it('renameFolder updates folder name', async () => {
      const { updateFolderApi, listFoldersApi } = await import('@/api/session')
      vi.mocked(updateFolderApi).mockResolvedValue({ id: 1, name: 'Renamed', orderNum: 0 } as any)
      vi.mocked(listFoldersApi).mockResolvedValue([{ id: 1, name: 'Renamed', orderNum: 0 }] as any)

      const store = useChatStore()
      const result = await store.renameFolder(1, 'Renamed')

      expect(result).toBe(true)
      expect(store.folders[0].name).toBe('Renamed')
    })

    it('removeFolder deletes and refreshes', async () => {
      const { deleteFolderApi, listFoldersApi } = await import('@/api/session')
      vi.mocked(deleteFolderApi).mockResolvedValue(undefined as any)
      vi.mocked(listFoldersApi).mockResolvedValue([] as any)

      const store = useChatStore()
      const result = await store.removeFolder(1)

      expect(result).toBe(true)
      expect(store.folders).toEqual([])
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

  describe('mode switching', () => {
    it('switchToDirect clears agent ID and session', () => {
      const store = useChatStore()
      store.activeAgentId = 1
      store.activeSession = { id: 5 } as any

      store.switchToDirect()

      expect(store.chatMode).toBe('direct')
      expect(store.activeAgentId).toBeNull()
      expect(store.activeSession).toBeNull()
    })

    it('switchToAgent sets agent ID and clears session', () => {
      const store = useChatStore()

      store.switchToAgent(3)

      expect(store.chatMode).toBe('agent')
      expect(store.activeAgentId).toBe(3)
      expect(store.activeSession).toBeNull()
    })
  })
})
