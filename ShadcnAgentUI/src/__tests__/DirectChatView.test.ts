import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import DirectChatView from '@/views/chat/DirectChatView.vue'
import i18n from '@/locales'
import { useChatStore } from '@/stores/chat'
import { createSessionApi, getSessionApi } from '@/api/session'
import { getAgentApi, listAgentsByScopeApi } from '@/api/agent'

const routeState = vi.hoisted(() => ({
  query: {} as Record<string, string>,
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
}))

vi.mock('sonner', () => ({
  toast: {
    error: vi.fn(),
  },
}))

vi.mock('@/api/file', () => ({
  uploadFileApi: vi.fn(),
}))

vi.mock('@/api/agent', () => ({
  listAgentsApi: vi.fn(),
  listAgentsByScopeApi: vi.fn(),
  getAgentApi: vi.fn(),
}))

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

function createAgent() {
  return {
    id: 1,
    name: 'DeepSeek Agent',
    icon: '',
    description: '',
    tags: [],
    systemPrompt: '',
    greeting: 'Hi',
    tools: [],
    skills: [],
    knowledgeBaseIds: [],
    modelId: 1,
    status: 'active' as const,
    createdAt: '2026-01-01',
    createdBy: 1,
    ragMode: 'GENERIC' as const,
  }
}

function createWrapper() {
  const pinia = createPinia()
  setActivePinia(pinia)

  return mount(DirectChatView, {
    global: {
      plugins: [pinia, i18n],
      stubs: {
        Button: { template: '<button :disabled="disabled" @click="$emit(\'click\', $event)"><slot /></button>', props: ['disabled'] },
        Textarea: { template: '<textarea :value="modelValue" :disabled="disabled" @input="$emit(\'update:modelValue\', $event.target.value)" />', props: ['modelValue', 'disabled'] },
        Card: { template: '<div><slot /></div>' },
        CardContent: { template: '<div><slot /></div>' },
        Badge: { template: '<span><slot /></span>' },
        Avatar: { template: '<div><slot /></div>' },
        AvatarFallback: { template: '<div><slot /></div>' },
        Skeleton: { template: '<div />' },
        MarkdownRenderer: { template: '<div>{{ content }}</div>', props: ['content'] },
      },
    },
  })
}

beforeEach(() => {
  routeState.query = {}
  i18n.global.locale.value = 'zh-CN' as 'zh-CN' | 'en'
  vi.clearAllMocks()
})

describe('DirectChatView', () => {
  it('loads an existing history session instead of creating a new one', async () => {
    const agent = createAgent()
    routeState.query = { agentId: '1', sessionId: '42' }
    vi.mocked(listAgentsByScopeApi).mockResolvedValue([agent] as any)
    vi.mocked(getAgentApi).mockResolvedValue(agent as any)
    vi.mocked(getSessionApi).mockResolvedValue({
      id: 42,
      agentId: 1,
      title: 'History',
      messages: [
        { role: 'user', content: 'previous prompt', timestamp: '2026-05-25T00:00:00Z' },
        { role: 'assistant', content: 'previous answer', timestamp: '2026-05-25T00:00:01Z' },
      ],
      createdAt: '2026-05-25T00:00:00Z',
      updatedAt: '2026-05-25T00:00:01Z',
    } as any)

    const wrapper = createWrapper()
    await flushPromises()
    await wrapper.vm.$nextTick()

    const chatStore = useChatStore()
    expect(getSessionApi).toHaveBeenCalledWith(42)
    expect(createSessionApi).not.toHaveBeenCalled()
    expect(chatStore.activeSession?.id).toBe(42)
    expect(chatStore.messages.map(message => message.content)).toEqual(['previous prompt', 'previous answer'])
    expect(wrapper.text()).toContain('previous prompt')
    expect(wrapper.text()).toContain('previous answer')
  })
})
