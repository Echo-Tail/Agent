import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAgentStore } from '@/stores/agent'
import type { Agent } from '@/types/agent'
import i18n from '@/locales'

vi.mock('@/api/agent', () => ({
  listAgentsApi: vi.fn(),
  listAgentsByScopeApi: vi.fn(),
}))

beforeEach(() => {
  i18n.global.locale.value = 'zh-CN' as 'zh-CN' | 'en'
})

const mockAgents: Agent[] = [
  {
    id: 1, name: '客服助手', icon: 'bot', description: '客服',
    tags: ['对话'], systemPrompt: 'prompt', greeting: '你好',
    tools: ['web'], skills: [], knowledgeBaseIds: [], modelId: 1,
    status: 'active', ragMode: 'AGENTIC', createdAt: '2024-01-01', createdBy: 1,
  },
  {
    id: 2, name: '订单管家', icon: 'bot', description: '订单',
    tags: ['订单'], systemPrompt: 'prompt', greeting: '你好',
    tools: ['web'], skills: [], knowledgeBaseIds: [], modelId: 1,
    status: 'disabled', ragMode: 'AGENTIC', createdAt: '2024-01-15', createdBy: 1,
  },
]

beforeEach(() => {
  setActivePinia(createPinia())
})

describe('useAgentStore', () => {
  it('starts with empty state', () => {
    const store = useAgentStore()
    expect(store.agents).toEqual([])
    expect(store.myAgents).toEqual([])
    expect(store.plazaAgents).toEqual([])
    expect(store.loading).toBe(false)
    expect(store.error).toBeNull()
    expect(store.summary).toEqual({ total: 0, active: 0, disabled: 0 })
  })

  it('fetchAgents populates agents and summary', async () => {
    const { listAgentsApi } = await import('@/api/agent')
    vi.mocked(listAgentsApi).mockResolvedValue(mockAgents)

    const store = useAgentStore()
    await store.fetchAgents()

    expect(store.agents).toHaveLength(2)
    expect(store.loading).toBe(false)
    expect(store.error).toBeNull()
    expect(store.summary).toEqual({ total: 2, active: 1, disabled: 1 })
  })

  it('fetchAgents sets loading state correctly', async () => {
    const { listAgentsApi } = await import('@/api/agent')
    vi.mocked(listAgentsApi).mockResolvedValue([])

    const store = useAgentStore()
    const promise = store.fetchAgents()
    expect(store.loading).toBe(true)
    await promise
    expect(store.loading).toBe(false)
  })

  it('fetchAgents sets error on API failure', async () => {
    const { listAgentsApi } = await import('@/api/agent')
    vi.mocked(listAgentsApi).mockRejectedValue(new Error('服务器错误'))

    const store = useAgentStore()
    await store.fetchAgents()

    expect(store.error).toBe('获取Agent 列表失败')
    expect(store.agents).toEqual([])
  })

  it('fetchAgents sets error on network exception', async () => {
    const { listAgentsApi } = await import('@/api/agent')
    vi.mocked(listAgentsApi).mockRejectedValue(new Error('网络异常'))

    const store = useAgentStore()
    await store.fetchAgents()

    expect(store.error).toBe('获取Agent 列表失败')
  })

  it('summary reflects current agent list', () => {
    const store = useAgentStore()
    store.agents = mockAgents
    expect(store.summary).toEqual({ total: 2, active: 1, disabled: 1 })
  })

  it('fetchMyAgents calls listAgentsByScopeApi with "my" and populates myAgents', async () => {
    const { listAgentsByScopeApi } = await import('@/api/agent')
    vi.mocked(listAgentsByScopeApi).mockResolvedValue([mockAgents[0]])

    const store = useAgentStore()
    await store.fetchMyAgents()

    expect(listAgentsByScopeApi).toHaveBeenCalledWith('my')
    expect(store.myAgents).toHaveLength(1)
  })

  it('fetchPlazaAgents calls listAgentsByScopeApi with "plaza" and populates plazaAgents', async () => {
    const { listAgentsByScopeApi } = await import('@/api/agent')
    vi.mocked(listAgentsByScopeApi).mockResolvedValue([mockAgents[1]])

    const store = useAgentStore()
    await store.fetchPlazaAgents()

    expect(listAgentsByScopeApi).toHaveBeenCalledWith('plaza')
    expect(store.plazaAgents).toHaveLength(1)
  })

  it('fetchAgents filters out system agents', async () => {
    const { listAgentsApi } = await import('@/api/agent')
    const systemAgent = { ...mockAgents[0], id: 99, isSystem: true }
    vi.mocked(listAgentsApi).mockResolvedValue([mockAgents[0], systemAgent])

    const store = useAgentStore()
    await store.fetchAgents()

    expect(store.agents).toHaveLength(1)
    expect(store.agents[0].id).toBe(1)
  })

  it('fetchAgents with scope parameter uses listAgentsByScopeApi', async () => {
    const { listAgentsByScopeApi } = await import('@/api/agent')
    vi.mocked(listAgentsByScopeApi).mockResolvedValue([mockAgents[0]])

    const store = useAgentStore()
    await store.fetchAgents('my')

    expect(listAgentsByScopeApi).toHaveBeenCalledWith('my')
  })
})
