import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import TicketManage from '@/views/admin/TicketManage.vue'
import i18n from '@/locales'
import { listAdminTicketsApi } from '@/api/ticket'

vi.mock('@/api/ticket', () => ({
  listAdminTicketsApi: vi.fn(),
  listTicketChangesApi: vi.fn(),
  startTicketApi: vi.fn(),
  completeTicketApi: vi.fn(),
}))

vi.mock('sonner', () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
  },
}))

function createWrapper() {
  return mount(TicketManage, {
    global: {
      plugins: [i18n],
      stubs: {
        PageHeader: { template: '<header><slot /></header>', props: ['title', 'description'] },
        EmptyState: { template: '<div class="stub-empty-state"><slot /></div>' },
        Button: { template: '<button type="button"><slot /></button>', props: ['variant', 'size', 'disabled', 'loading'] },
        SearchInput: {
          template: '<input class="stub-search-input" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
          props: ['modelValue', 'placeholder', 'inputClass'],
        },
        Badge: { template: '<span><slot /></span>', props: ['variant'] },
        Skeleton: { template: '<div />' },
        Select: { template: '<div><slot /></div>', props: ['modelValue'] },
        SelectContent: { template: '<div><slot /></div>' },
        SelectItem: { template: '<div><slot /></div>', props: ['value'] },
        SelectTrigger: { template: '<button type="button"><slot /></button>' },
        SelectValue: { template: '<span />', props: ['placeholder'] },
        Dialog: { template: '<div><slot /></div>', props: ['open'] },
        DialogContent: { template: '<div><slot /></div>' },
        DialogFooter: { template: '<div><slot /></div>' },
        DialogHeader: { template: '<div><slot /></div>' },
        DialogTitle: { template: '<div><slot /></div>' },
        Sheet: { template: '<div><slot /></div>', props: ['open'] },
        SheetContent: { template: '<div><slot /></div>', props: ['side'] },
        SheetHeader: { template: '<div><slot /></div>' },
        SheetTitle: { template: '<div><slot /></div>' },
        SheetDescription: { template: '<div><slot /></div>' },
      },
    },
  })
}

describe('TicketManage', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'zh-CN' as 'zh-CN' | 'en'
    vi.mocked(listAdminTicketsApi).mockResolvedValue([])
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders submitter filter input without unresolved Input warning', async () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})

    const wrapper = createWrapper()
    await wrapper.vm.$nextTick()

    const submitterInput = wrapper.find('input[type="number"]')
    expect(submitterInput.exists()).toBe(true)
    expect(warnSpy).not.toHaveBeenCalledWith(expect.stringContaining('Failed to resolve component: Input'))
  })
})
