import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useChatStore } from '../stores/chat'
import type { SessionMessage } from '../types/session'

vi.mock('../api/session', () => ({
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

function makeGreetingMsg(content: string, ts?: string): SessionMessage {
  return {
    role: 'assistant',
    content,
    timestamp: ts || new Date().toISOString(),
  }
}

beforeEach(() => {
  setActivePinia(createPinia())
})

describe('Greeting message in chat store', () => {
  describe('store.messages.push', () => {
    it('can push a greeting message to empty messages', () => {
      const store = useChatStore()
      const greeting = makeGreetingMsg('你好！有什么可以帮助你的？')

      store.messages.push(greeting)

      expect(store.messages).toHaveLength(1)
      expect(store.messages[0].role).toBe('assistant')
      expect(store.messages[0].content).toBe('你好！有什么可以帮助你的？')
    })

    it('preserves existing messages when greeting is added', () => {
      const store = useChatStore()
      store.messages.push({
        role: 'user',
        content: '之前的消息',
        timestamp: new Date().toISOString(),
      })
      store.messages.push({
        role: 'assistant',
        content: '之前的回复',
        timestamp: new Date().toISOString(),
      })

      const greeting = makeGreetingMsg('欢迎语')
      store.messages.push(greeting)

      expect(store.messages).toHaveLength(3)
      expect(store.messages[2].content).toBe('欢迎语')
    })

    it('does not add greeting when messages already exist (no double insert)', () => {
      const store = useChatStore()
      store.messages.push({
        role: 'user',
        content: '用户消息',
        timestamp: new Date().toISOString(),
      })

      // Simulate the guard condition: only push when messages.length === 0
      if (store.messages.length === 0) {
        store.messages.push(makeGreetingMsg('欢迎语'))
      }

      expect(store.messages).toHaveLength(1)
      expect(store.messages[0].role).toBe('user')
    })
  })

  describe('clearActiveSession clears greeting', () => {
    it('removes greeting message along with all messages', () => {
      const store = useChatStore()
      store.messages.push(makeGreetingMsg('你好！'))
      store.messages.push({
        role: 'user',
        content: '问题',
        timestamp: new Date().toISOString(),
      })

      store.clearActiveSession()

      expect(store.messages).toEqual([])
    })
  })

  describe('createSession resets messages to empty', () => {
    it('createSession sets messages to empty array', async () => {
      const { createSessionApi } = await import('../api/session')
      vi.mocked(createSessionApi).mockResolvedValue({
        data: { code: 200, message: 'ok', data: { id: 1, agentId: 1, title: '新对话', folderId: null, tags: [], messages: [], createdAt: '', updatedAt: '' } },
      } as any)

      const store = useChatStore()
      store.messages.push(makeGreetingMsg('之前插入的欢迎语'))
      await store.createSession(1)

      expect(store.messages).toHaveLength(0)
    })

    it('allows greeting push after createSession', async () => {
      const { createSessionApi } = await import('../api/session')
      vi.mocked(createSessionApi).mockResolvedValue({
        data: { code: 200, message: 'ok', data: { id: 2, agentId: 1, title: '', folderId: null, tags: [], messages: [], createdAt: '', updatedAt: '' } },
      } as any)

      const store = useChatStore()
      await store.createSession(1)

      store.messages.push(makeGreetingMsg('欢迎来到新会话'))

      expect(store.messages).toHaveLength(1)
      expect(store.messages[0].content).toBe('欢迎来到新会话')
    })
  })

  describe('loadSession preserves greeting check', () => {
    it('can push greeting after loading an empty session', async () => {
      const { getSessionApi } = await import('../api/session')
      vi.mocked(getSessionApi).mockResolvedValue({
        data: { code: 200, message: 'ok', data: { id: 5, agentId: 1, title: '', folderId: null, tags: [], messages: [], createdAt: '', updatedAt: '' } },
      } as any)

      const store = useChatStore()
      await store.loadSession(5)

      // greeting can be pushed after load if messages are empty
      if (store.messages.length === 0) {
        store.messages.push(makeGreetingMsg('欢迎回来！'))
      }

      expect(store.messages).toHaveLength(1)
      expect(store.messages[0].content).toBe('欢迎回来！')
    })

    it('does not push greeting when loaded session has messages', async () => {
      const { getSessionApi } = await import('../api/session')
      vi.mocked(getSessionApi).mockResolvedValue({
        data: { code: 200, message: 'ok', data: { id: 6, agentId: 1, title: '', folderId: null, tags: [], messages: [{ role: 'user', content: '已有消息', timestamp: '' }], createdAt: '', updatedAt: '' } },
      } as any)

      const store = useChatStore()
      await store.loadSession(6)

      if (store.messages.length === 0) {
        store.messages.push(makeGreetingMsg('欢迎'))
      }

      expect(store.messages).toHaveLength(1)
      expect(store.messages[0].content).toBe('已有消息')
    })
  })
})
