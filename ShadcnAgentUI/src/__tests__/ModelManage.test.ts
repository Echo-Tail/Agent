import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import ModelManage from '@/views/admin/ModelManage.vue'
import i18n from '@/locales'
import { listModelsApi } from '@/api/model'

vi.mock('@/api/model', () => ({
  listModelsApi: vi.fn(),
  createModelApi: vi.fn(),
  updateModelApi: vi.fn(),
  deleteModelApi: vi.fn(),
  validateModelApi: vi.fn(),
}))

vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    warning: vi.fn(),
  },
}))

function createWrapper() {
  return mount(ModelManage, {
    global: {
      plugins: [i18n],
      stubs: {
        PageHeader: { template: '<header><slot /></header>', props: ['title', 'description'] },
        ConfirmDialog: { template: '<div />', props: ['open', 'title', 'description', 'confirmText'] },
        Button: { template: '<button type="button" v-bind="$attrs"><slot /></button>', props: ['variant', 'size', 'disabled', 'loading'] },
        Badge: { template: '<span><slot /></span>', props: ['variant'] },
        Skeleton: { template: '<div />' },
        Select: { template: '<div><slot /></div>', props: ['modelValue'] },
        SelectContent: { template: '<div><slot /></div>' },
        SelectItem: { template: '<div><slot /></div>', props: ['value'] },
        SelectTrigger: { template: '<button type="button" v-bind="$attrs"><slot /></button>' },
        SelectValue: { template: '<span />', props: ['placeholder'] },
        Dialog: { template: '<div><slot /></div>', props: ['open'] },
        DialogContent: { template: '<div><slot /></div>' },
        DialogFooter: { template: '<div><slot /></div>' },
        DialogHeader: { template: '<div><slot /></div>' },
        DialogTitle: { template: '<div><slot /></div>' },
      },
    },
  })
}

describe('ModelManage', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'zh-CN' as 'zh-CN' | 'en'
    vi.mocked(listModelsApi).mockResolvedValue([])
  })

  it('associates model form labels with named fields', async () => {
    const wrapper = createWrapper()
    await wrapper.find('header button').trigger('click')
    await wrapper.vm.$nextTick()

    const fieldIds = [
      'model-name',
      'model-provider',
      'model-type',
      'model-id',
      'model-api-url',
      'model-api-type',
      'model-temperature',
      'model-api-key',
      'model-max-tokens',
      'model-is-default',
      'model-enabled',
    ]

    for (const id of fieldIds) {
      expect(wrapper.find(`label[for="${id}"]`).exists()).toBe(true)
      const field = wrapper.find(`#${id}`)
      expect(field.exists()).toBe(true)
      expect(field.attributes('name')).toBe(id)
    }
  })
})
