import { describe, it, expect, vi, beforeEach } from 'vitest'
import http from '@/api/request'
import {
  createGroupApi,
  listMyGroupsApi,
  getGroupApi,
  disbandGroupApi,
  inviteMemberApi,
  kickMemberApi,
  listMembersApi,
  addGroupAgentApi,
  removeGroupAgentApi,
  listGroupAgentsApi,
  sendGroupMessageApi,
  listGroupMessagesApi,
  sendPrivateMessageApi,
  getConversationApi,
  getContactsApi,
  listEmojiPacksApi,
} from '@/api/group'

vi.mock('@/api/request', () => ({
  default: {
    post: vi.fn(),
    get: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

describe('group API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('群 CRUD', () => {
    it('createGroupApi 应 POST /groups', async () => {
      vi.mocked(http.post).mockResolvedValue({ id: 1, name: '测试群' })
      const result = await createGroupApi('测试群')
      expect(http.post).toHaveBeenCalledWith('/groups', { name: '测试群', avatar: undefined })
      expect(result).toEqual({ id: 1, name: '测试群' })
    })

    it('createGroupApi 可传入 avatar', async () => {
      vi.mocked(http.post).mockResolvedValue({ id: 2, name: '群', avatar: 'url' })
      await createGroupApi('群', 'url')
      expect(http.post).toHaveBeenCalledWith('/groups', { name: '群', avatar: 'url' })
    })

    it('listMyGroupsApi 应 GET /groups', async () => {
      vi.mocked(http.get).mockResolvedValue([{ id: 1, name: '群A' }])
      const result = await listMyGroupsApi()
      expect(http.get).toHaveBeenCalledWith('/groups')
      expect(result).toHaveLength(1)
    })

    it('getGroupApi 应 GET /groups/{id}', async () => {
      vi.mocked(http.get).mockResolvedValue({ id: 5, name: '群X' })
      const result = await getGroupApi(5)
      expect(http.get).toHaveBeenCalledWith('/groups/5')
      expect(result.name).toBe('群X')
    })

    it('disbandGroupApi 应 DELETE /groups/{id}', async () => {
      vi.mocked(http.delete).mockResolvedValue(undefined)
      await disbandGroupApi(5)
      expect(http.delete).toHaveBeenCalledWith('/groups/5')
    })
  })

  describe('群成员', () => {
    it('inviteMemberApi 应 POST 含 userId', async () => {
      vi.mocked(http.post).mockResolvedValue(undefined)
      await inviteMemberApi(5, 10)
      expect(http.post).toHaveBeenCalledWith('/groups/5/members', { userId: 10 })
    })

    it('kickMemberApi 应 DELETE /groups/{id}/members/{userId}', async () => {
      vi.mocked(http.delete).mockResolvedValue(undefined)
      await kickMemberApi(5, 10)
      expect(http.delete).toHaveBeenCalledWith('/groups/5/members/10')
    })

    it('listMembersApi 应 GET /groups/{id}/members', async () => {
      vi.mocked(http.get).mockResolvedValue([{ userId: 1, role: 'CREATOR' }])
      const result = await listMembersApi(5)
      expect(http.get).toHaveBeenCalledWith('/groups/5/members')
      expect(result).toHaveLength(1)
    })
  })

  describe('群 Agent', () => {
    it('addGroupAgentApi 应 POST 含 agentId', async () => {
      vi.mocked(http.post).mockResolvedValue(undefined)
      await addGroupAgentApi(5, 100)
      expect(http.post).toHaveBeenCalledWith('/groups/5/agents', { agentId: 100 })
    })

    it('removeGroupAgentApi 应 DELETE', async () => {
      vi.mocked(http.delete).mockResolvedValue(undefined)
      await removeGroupAgentApi(5, 100)
      expect(http.delete).toHaveBeenCalledWith('/groups/5/agents/100')
    })

    it('listGroupAgentsApi 应 GET', async () => {
      vi.mocked(http.get).mockResolvedValue([{ agentId: 100 }])
      const result = await listGroupAgentsApi(5)
      expect(http.get).toHaveBeenCalledWith('/groups/5/agents')
      expect(result).toHaveLength(1)
    })
  })

  describe('群消息', () => {
    it('sendGroupMessageApi 应 POST 含 content', async () => {
      vi.mocked(http.post).mockResolvedValue({ id: 1, content: '你好' })
      const result = await sendGroupMessageApi(5, '你好')
      expect(http.post).toHaveBeenCalledWith('/groups/5/messages', { content: '你好' })
      expect(result.content).toBe('你好')
    })

    it('listGroupMessagesApi 应 GET 带分页参数', async () => {
      vi.mocked(http.get).mockResolvedValue([{ id: 1 }])
      await listGroupMessagesApi(5, 0, 50)
      expect(http.get).toHaveBeenCalledWith('/groups/5/messages', { params: { page: 0, size: 50 } })
    })
  })

  describe('用户私聊', () => {
    it('sendPrivateMessageApi 应 POST /messages', async () => {
      vi.mocked(http.post).mockResolvedValue({ id: 1, content: 'hi' })
      const result = await sendPrivateMessageApi(10, 'hi')
      expect(http.post).toHaveBeenCalledWith('/messages', { receiverId: 10, content: 'hi' })
      expect(result.content).toBe('hi')
    })

    it('getConversationApi 应 GET /messages/{userId}', async () => {
      vi.mocked(http.get).mockResolvedValue([{ id: 1 }])
      await getConversationApi(10)
      expect(http.get).toHaveBeenCalledWith('/messages/10', { params: { page: 0, size: 50 } })
    })

    it('getContactsApi 应 GET /messages/contacts', async () => {
      vi.mocked(http.get).mockResolvedValue([{ userId: 1, username: '张三' }])
      const result = await getContactsApi()
      expect(http.get).toHaveBeenCalledWith('/messages/contacts')
      expect(result[0].username).toBe('张三')
    })
  })

  describe('表情包', () => {
    it('listEmojiPacksApi 应 GET /emoji/packs', async () => {
      vi.mocked(http.get).mockResolvedValue([{ id: 1, name: '笑脸', imageUrl: 'url' }])
      const result = await listEmojiPacksApi()
      expect(http.get).toHaveBeenCalledWith('/emoji/packs')
      expect(result).toHaveLength(1)
    })
  })
})
