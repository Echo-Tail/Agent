import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { setActivePinia, createPinia } from 'pinia'
import { NConfigProvider, NMessageProvider, zhCN, dateZhCN } from 'naive-ui'
import ToolManage from '../views/admin/ToolManage.vue'
import type { ToolDefinition } from '../api/tool'

const mockListToolsApi = vi.hoisted(() => vi.fn())
const mockListModelsApi = vi.hoisted(() => vi.fn())
const mockSaveToolConfigApi = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ name: 'ToolManage' }),
}))

vi.mock('../api/tool', () => ({
  listToolsApi: mockListToolsApi,
  updateToolApi: vi.fn().mockResolvedValue({ data: { code: 200, message: 'ok', data: {} } }),
  toggleToolApi: vi.fn(),
  saveToolConfigApi: mockSaveToolConfigApi,
}))

vi.mock('../api/model', () => ({
  listModelsApi: mockListModelsApi,
}))

const mockTools: ToolDefinition[] = [
  {
    id: 'web_search', name: '网页搜索',
    description: '搜索互联网获取最新信息',
    category: 'web', enabled: true, configJson: '',
  },
  {
    id: 'image_generation', name: '图片生成',
    description: '根据文字描述生成图片',
    category: 'media', enabled: true, configJson: '{"apiKey":"sk-old","modelId":2}',
  },
]

const mockModels = [
  { id: 1, name: 'DALL-E 3', provider: 'openai', modelName: 'dall-e-3', enabled: true },
  { id: 2, name: 'Stable Diffusion', provider: 'openai', modelName: 'stable-diffusion-3', enabled: true },
  { id: 3, name: 'Disabled Model', provider: 'openai', modelName: 'disabled', enabled: false },
]

async function mountToolManage() {
  const Wrapper = defineComponent({
    setup() {
      return () =>
        h(NConfigProvider, { locale: zhCN, 'date-locale': dateZhCN }, () =>
          h(NMessageProvider, null, () =>
            h(ToolManage),
          ),
        )
    },
  })
  const wrapper = mount(Wrapper, {
    global: {
      stubs: {
        'n-modal': {
          template: '<div v-if="show" class="n-modal-stub"><slot /></div>',
          props: ['show'],
        },
        'n-data-table': {
          template: '<div class="n-data-table-stub"><slot name="empty" /></div>',
        },
        'n-form': { template: '<div><slot /></div>' },
        'n-form-item': { template: '<div class="n-form-item-stub"><slot /></div>' },
        'n-input': { template: '<input class="n-input-stub" />' },
        'n-select': { template: '<select class="n-select-stub" />' },
        'n-button': { template: '<button class="n-button-stub"><slot /></button>' },
        'n-space': { template: '<div><slot /></div>' },
        'n-h3': { template: '<h3><slot /></h3>' },
        'n-divider': { template: '<hr />' },
        'n-empty': { template: '<div class="n-empty-stub"><slot name="description" /></div>' },
        'n-alert': { template: '<div class="n-alert-stub"><slot /></div>' },
        'n-switch': true,
      },
    },
  })
  // Wait for initial fetch (listToolsApi is called on mount)
  await new Promise(resolve => setTimeout(resolve, 50))
  return wrapper
}

describe('ToolManage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockListToolsApi.mockResolvedValue({
      data: { code: 200, message: 'ok', data: mockTools },
    })
    mockSaveToolConfigApi.mockResolvedValue({
      data: { code: 200, message: 'ok', data: { configJson: '{"apiKey":"sk-xxx","modelId":1}' } },
    })
  })

  it('renders page title', async () => {
    const wrapper = await mountToolManage()
    expect(wrapper.text()).toContain('工具管理')
  })

  describe('fetchEnabledModels', () => {
    it('loads and filters enabled models', async () => {
      mockListModelsApi.mockResolvedValue({
        data: { code: 200, message: 'ok', data: mockModels },
      })
      const wrapper = await mountToolManage()
      const vm = wrapper.findComponent({ name: 'ToolManage' }).vm as any

      await vm.fetchEnabledModels()

      expect(mockListModelsApi).toHaveBeenCalledTimes(1)
      // Should only contain enabled models (2 out of 3)
      expect(vm.models).toHaveLength(2)
      expect(vm.models[0].label).toContain('DALL-E 3')
      expect(vm.models[1].label).toContain('Stable Diffusion')
      // Disabled model should be filtered out
      expect(vm.models.some((m: any) => m.label.includes('Disabled'))).toBe(false)
    })

    it('handles loading failure gracefully', async () => {
      mockListModelsApi.mockRejectedValue(new Error('network error'))
      const wrapper = await mountToolManage()
      const vm = wrapper.findComponent({ name: 'ToolManage' }).vm as any

      await vm.fetchEnabledModels()

      expect(vm.models).toHaveLength(0)
      expect(vm.loadingModels).toBe(false)
    })

    it('handles API error response', async () => {
      mockListModelsApi.mockResolvedValue({
        data: { code: 500, message: '服务器错误', data: null },
      })
      const wrapper = await mountToolManage()
      const vm = wrapper.findComponent({ name: 'ToolManage' }).vm as any

      await vm.fetchEnabledModels()

      expect(vm.models).toHaveLength(0)
    })
  })

  describe('openConfig for image_generation', () => {
    it('loads models and parses existing config', async () => {
      mockListModelsApi.mockResolvedValue({
        data: { code: 200, message: 'ok', data: mockModels },
      })
      const wrapper = await mountToolManage()
      const vm = wrapper.findComponent({ name: 'ToolManage' }).vm as any

      await vm.openConfig(mockTools[1]) // image_generation

      // Should parse existing configJson
      expect(vm.configApiKey).toBe('sk-old')
      expect(vm.configModelId).toBe(2)
      // Should have loaded models
      expect(vm.models).toHaveLength(2)
    })

    it('parses config without modelId (backward compat)', async () => {
      mockListModelsApi.mockResolvedValue({
        data: { code: 200, message: 'ok', data: mockModels },
      })
      const wrapper = await mountToolManage()
      const vm = wrapper.findComponent({ name: 'ToolManage' }).vm as any

      const toolWithoutModel = { ...mockTools[1], configJson: '{"apiKey":"sk-old"}' }
      await vm.openConfig(toolWithoutModel)

      expect(vm.configApiKey).toBe('sk-old')
      expect(vm.configModelId).toBeNull()
    })

    it('does not load models for non-image tools', async () => {
      const wrapper = await mountToolManage()
      const vm = wrapper.findComponent({ name: 'ToolManage' }).vm as any

      await vm.openConfig(mockTools[0]) // web_search
      expect(mockListModelsApi).not.toHaveBeenCalled()
    })
  })

  describe('modal visibility', () => {
    it('opens modal when openConfig is called', async () => {
      const wrapper = await mountToolManage()
      const vm = wrapper.findComponent({ name: 'ToolManage' }).vm as any

      expect(vm.showConfigModal).toBe(false)
      await vm.openConfig(mockTools[1])
      expect(vm.showConfigModal).toBe(true)
    })
  })
})
