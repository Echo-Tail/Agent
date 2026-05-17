import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { setActivePinia, createPinia } from 'pinia'
import { NConfigProvider, NMessageProvider, zhCN, dateZhCN } from 'naive-ui'
import DashboardView from '../views/dashboard/DashboardView.vue'
import type { Agent } from '../types/agent'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ name: 'Dashboard' }),
}))

const mockListAgentsApi = vi.hoisted(() => vi.fn())
vi.mock('../api/agent', () => ({
  listAgentsApi: mockListAgentsApi,
}))

const mockAgents: Agent[] = [
  {
    id: 1, name: '客服助手', icon: 'bi-robot',
    description: '处理客户咨询', tags: ['对话'],
    systemPrompt: '客服助手', greeting: '你好',
    tools: ['web'], knowledgeBaseIds: [],
    modelId: 1, status: 'active',
    createdAt: '2024-01-01', createdBy: 1,
  },
]

describe('DashboardView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockListAgentsApi.mockResolvedValue({
      data: { code: 200, message: 'ok', data: [] },
    })
  })

  function createWrapper() {
    const Wrapper = defineComponent({
      setup() {
        return () =>
          h(NConfigProvider, { locale: zhCN, 'date-locale': dateZhCN }, () =>
            h(NMessageProvider, null, () =>
              h(DashboardView),
            ),
          )
      },
    })
    return mount(Wrapper, {
      global: {
        stubs: {
          'n-dialog-provider': { template: '<div><slot /></div>' },
          'n-notification-provider': { template: '<div><slot /></div>' },
          AgentCard: {
            props: ['agent'],
            template: '<div class="agent-card-stub">{{ agent.name }}</div>',
          },
        },
      },
    })
  }

  it('renders welcome message', () => {
    const wrapper = createWrapper()
    expect(wrapper.text()).toContain('欢迎使用 EcomAgents')
  })

  it('shows agent list when store has data', async () => {
    mockListAgentsApi.mockResolvedValue({
      data: { code: 200, message: 'ok', data: mockAgents },
    })
    const wrapper = createWrapper()
    await new Promise(resolve => setTimeout(resolve))
    expect(wrapper.findAll('.agent-card-stub').length).toBe(1)
  })

  it('shows empty state when no agents', () => {
    const wrapper = createWrapper()
    const resultEl = wrapper.find('n-result')
    expect(resultEl.exists()).toBe(true)
  })
})
