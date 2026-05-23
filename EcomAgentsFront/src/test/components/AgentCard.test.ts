import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import AgentCard from '../../components/AgentCard.vue'
import type { Agent } from '../../types/agent'

const mockPush = vi.hoisted(() => vi.fn())
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => ({}),
}))

const activeAgent: Agent = {
  id: 1,
  name: '客服助手',
  icon: 'bi-robot',
  description: '处理客户咨询问题',
  tags: ['对话', '查询'],
  systemPrompt: '你是一个客服助手',
  greeting: '你好',
  tools: ['web'],
  skills: [],
  knowledgeBaseIds: [],
  modelId: 1,
  status: 'active',
  ragMode: 'AGENTIC',
  createdAt: '2024-01-01',
  createdBy: 1,
}

const disabledAgent: Agent = {
  ...activeAgent,
  id: 2,
  name: '停用助手',
  status: 'disabled',
}

describe('AgentCard', () => {
  function createWrapper(props: { agent: Agent; editable?: boolean }) {
    return mount(AgentCard, {
      props,
      global: {
        stubs: {
          'n-card': {
            name: 'NCard',
            template: '<div class="n-card" @click="$emit(\'click\')"><slot /></div>',
          },
          'n-avatar': {
            name: 'NAvatar',
            template: '<div class="n-avatar"><slot /></div>',
            props: ['color'],
          },
          'n-icon': {
            name: 'NIcon',
            template: '<div class="n-icon"><slot /></div>',
            props: ['size', 'color'],
          },
          'n-tag': {
            name: 'NTag',
            template: '<span class="n-tag"><slot /></span>',
            props: ['type', 'size', 'bordered', 'round'],
          },
          'n-ellipsis': {
            name: 'NEllipsis',
            template: '<div class="n-ellipsis"><slot /></div>',
            props: ['lineClamp'],
          },
          'n-button': {
            name: 'NButton',
            template: '<button class="n-button" @click="$emit(\'click\', { stopPropagation: () => {} })"><slot /></button>',
            props: ['quaternary', 'circle', 'size'],
          },
        },
      },
    })
  }

  it('renders agent name and description', () => {
    const wrapper = createWrapper({ agent: activeAgent })
    expect(wrapper.text()).toContain('客服助手')
    expect(wrapper.text()).toContain('处理客户咨询问题')
  })

  it('renders tags', () => {
    const wrapper = createWrapper({ agent: activeAgent })
    expect(wrapper.text()).toContain('对话')
    expect(wrapper.text()).toContain('查询')
  })

  it('shows 启用 tag for active agent', () => {
    const wrapper = createWrapper({ agent: activeAgent })
    expect(wrapper.text()).toContain('启用')
  })

  it('shows 停用 tag for disabled agent', () => {
    const wrapper = createWrapper({ agent: disabledAgent })
    expect(wrapper.text()).toContain('停用')
  })

  it('does not show edit/delete buttons in default mode', () => {
    const wrapper = createWrapper({ agent: activeAgent })
    expect(wrapper.find('.card-actions').exists()).toBe(false)
  })

  it('shows edit/delete buttons in editable mode', () => {
    const wrapper = createWrapper({ agent: activeAgent, editable: true })
    expect(wrapper.findComponent({ name: 'NButton' }).exists()).toBe(true)
  })

  it('emits delete event on delete button click', async () => {
    const wrapper = createWrapper({ agent: activeAgent, editable: true })
    const buttons = wrapper.findAllComponents({ name: 'NButton' })
    expect(buttons.length).toBeGreaterThanOrEqual(2)

    await buttons[buttons.length - 1].trigger('click')
    expect(wrapper.emitted('delete')).toBeTruthy()
    expect(wrapper.emitted('delete')![0]).toEqual([activeAgent])
  })

  it('navigates to agent edit on edit button click', async () => {
    mockPush.mockClear()
    const wrapper = createWrapper({ agent: activeAgent, editable: true })
    const buttons = wrapper.findAllComponents({ name: 'NButton' })

    await buttons[0].trigger('click')
    expect(mockPush).toHaveBeenCalledWith({ name: 'AgentEdit', params: { id: 1 } })
  })
})
