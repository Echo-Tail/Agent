import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import i18n from '@/locales'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import SearchInput from '@/components/SearchInput.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { Badge } from '@/components/ui/badge'
import { Card, CardAction, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'

vi.mock('lucide-vue-next', () => ({
  Search: { template: '<span data-icon="search" />' },
  Loader2: { template: '<span data-icon="loader" />' },
}))

vi.mock('reka-ui', () => ({
  Primitive: { template: '<span><slot /></span>', props: ['as', 'asChild'] },
  AvatarRoot: { template: '<div><slot /></div>' },
  AvatarFallback: { template: '<span><slot /></span>' },
  AvatarImage: { template: '<img />' },
}))

const InputStub = {
  template: '<input :id="id" :name="name" :value="value" :placeholder="placeholder" :class="$attrs.class" @input="$emit(\'input\', $event)" @keydown="$attrs.onKeydown" />',
  props: ['id', 'name', 'value', 'placeholder'],
  inheritAttrs: false,
}

const ButtonStub = {
  template: '<button :disabled="disabled" :data-variant="variant" @click="$emit(\'click\', $event)"><slot /></button>',
  props: ['disabled', 'variant'],
  inheritAttrs: false,
}

describe('shared components', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders sanitized markdown and copies code blocks', async () => {
    const wrapper = mount(MarkdownRenderer, {
      props: {
        content: '## Title\n\n<script>alert(1)</script>\n\n```ts\nconst a = 1\n```',
      },
    })

    expect(wrapper.html()).toContain('Title')
    expect(wrapper.html()).not.toContain('<script>')

    const copyButton = wrapper.find('[data-md-copy-btn]')
    expect(copyButton.exists()).toBe(true)
    await copyButton.trigger('click')

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('const a = 1')
    expect(copyButton.classes()).toContain('copied')
    vi.advanceTimersByTime(1500)
    await nextTick()
    expect(copyButton.classes()).not.toContain('copied')
  })

  it('emits input and search events from SearchInput', async () => {
    const wrapper = mount(SearchInput, {
      props: {
        id: 'q',
        name: 'query',
        modelValue: 'old',
        placeholder: 'Search',
        inputClass: 'custom-class',
      },
      global: {
        plugins: [i18n],
        stubs: { Input: InputStub },
      },
    })

    const input = wrapper.find('input')
    expect(input.attributes('id')).toBe('q')
    expect(input.attributes('name')).toBe('query')
    expect(input.attributes('placeholder')).toBe('Search')
    expect(input.classes()).toContain('custom-class')

    await input.setValue('new')
    input.element.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }))
    input.element.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['new'])
    expect(wrapper.emitted('search')).toHaveLength(1)
  })

  it('renders ConfirmDialog defaults and emits cancel', async () => {
    const wrapper = mount(ConfirmDialog, {
      props: { open: true, description: 'Delete item?', loading: true },
      global: {
        plugins: [i18n],
        stubs: {
          Dialog: { template: '<div><slot /></div>', props: ['open'] },
          DialogContent: { template: '<section><slot /></section>' },
          DialogFooter: { template: '<footer><slot /></footer>' },
          DialogHeader: { template: '<header><slot /></header>' },
          DialogTitle: { template: '<h2><slot /></h2>' },
          Button: ButtonStub,
        },
      },
    })

    expect(wrapper.text()).toContain('Delete item?')
    expect(wrapper.find('[data-icon="loader"]').exists()).toBe(true)

    const buttons = wrapper.findAll('button')
    await buttons.at(-2)!.trigger('click')

    expect(wrapper.emitted('update:open')?.[0]).toEqual([false])
  })

  it('emits ConfirmDialog confirm when not loading', async () => {
    const wrapper = mount(ConfirmDialog, {
      props: { open: true, title: 'Confirm', confirmText: 'OK', loading: false },
      global: {
        plugins: [i18n],
        stubs: {
          Dialog: { template: '<div><slot /></div>', props: ['open'] },
          DialogContent: { template: '<section><slot /></section>' },
          DialogFooter: { template: '<footer><slot /></footer>' },
          DialogHeader: { template: '<header><slot /></header>' },
          DialogTitle: { template: '<h2><slot /></h2>' },
          Button: ButtonStub,
        },
      },
    })

    await wrapper.findAll('button').at(-1)!.trigger('click')

    expect(wrapper.emitted('confirm')).toHaveLength(1)
  })

  it('renders basic ui primitives with slots and model updates', async () => {
    const badge = mount(Badge, { props: { variant: 'secondary', class: 'extra' }, slots: { default: 'Badge' } })
    expect(badge.text()).toBe('Badge')

    const card = mount(Card, {
      props: { size: 'sm', class: 'custom-card' },
      slots: {
        default: '<CardHeader><CardTitle>Title</CardTitle><CardDescription>Description</CardDescription><CardAction>Action</CardAction></CardHeader><CardContent>Body</CardContent><CardFooter>Footer</CardFooter>',
      },
      global: { components: { CardHeader, CardTitle, CardDescription, CardAction, CardContent, CardFooter } },
    })
    expect(card.text()).toContain('Title')
    expect(card.text()).toContain('Description')
    expect(card.text()).toContain('Action')
    expect(card.text()).toContain('Body')
    expect(card.text()).toContain('Footer')
    expect(card.attributes('data-size')).toBe('sm')

    const avatar = mount(Avatar, {
      props: { size: 'lg', class: 'custom-avatar' },
      slots: { default: '<AvatarFallback>AB</AvatarFallback>' },
      global: { components: { AvatarFallback } },
    })
    expect(avatar.text()).toContain('AB')

    const input = mount(Input, { props: { modelValue: 'old', type: 'search', class: 'custom-input' } })
    await input.find('input').setValue('new')
    expect(input.emitted('update:modelValue')?.[0]).toEqual(['new'])
    expect(input.find('input').attributes('type')).toBe('search')

    const skeleton = mount(Skeleton, { props: { class: 'h-4' } })
    expect(skeleton.attributes('data-slot')).toBe('skeleton')
  })
})
