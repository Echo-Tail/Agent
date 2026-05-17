import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import MessageBubble from '../../components/MessageBubble.vue'
import type { SessionMessage } from '../../types/session'

beforeEach(() => {
  vi.stubGlobal('navigator', {
    clipboard: {
      writeText: vi.fn().mockResolvedValue(undefined),
    },
  })
})

function createWrapper(msg: SessionMessage) {
  return mount(MessageBubble, {
    props: { msg },
    global: {
      stubs: {
        'n-avatar': {
          name: 'NAvatar',
          template: '<div class="n-avatar"><slot /></div>',
          props: ['size', 'round', 'color'],
        },
        'n-icon': {
          name: 'NIcon',
          template: '<div class="n-icon"><slot /></div>',
          props: ['size', 'color'],
        },
      },
    },
  })
}

describe('MessageBubble', () => {
  describe('user messages', () => {
    const userMsg: SessionMessage = {
      role: 'user',
      content: 'Hello from user',
      timestamp: new Date().toISOString(),
    }

    it('renders user message as plain text', () => {
      const wrapper = createWrapper(userMsg)
      expect(wrapper.text()).toContain('Hello from user')
      expect(wrapper.find('pre').exists()).toBe(true)
    })

    it('does not show copy button for user messages', () => {
      const wrapper = createWrapper(userMsg)
      expect(wrapper.find('.msg-copy-btn').exists()).toBe(false)
    })
  })

  describe('assistant messages', () => {
    const assistantMsg: SessionMessage = {
      role: 'assistant',
      content: 'Hello from **agent**\n\n```js\nconst x = 1\n```',
      timestamp: new Date().toISOString(),
    }

    it('renders assistant message with MarkdownRenderer', () => {
      const wrapper = createWrapper(assistantMsg)
      expect(wrapper.text()).toContain('Hello from')
      expect(wrapper.findComponent({ name: 'MarkdownRenderer' }).exists()).toBe(true)
    })

    it('shows copy button for assistant messages', () => {
      const wrapper = createWrapper(assistantMsg)
      expect(wrapper.find('.msg-copy-btn').exists()).toBe(true)
    })

    it('copies full content when copy button is clicked', async () => {
      const wrapper = createWrapper(assistantMsg)

      const btn = wrapper.find('.msg-copy-btn')
      await btn.trigger('click')

      expect(navigator.clipboard.writeText).toHaveBeenCalledWith(assistantMsg.content)
    })

    it('shows copied state on click', async () => {
      const wrapper = createWrapper(assistantMsg)
      const btn = wrapper.find('.msg-copy-btn')

      await btn.trigger('click')
      expect(btn.classes()).toContain('copied')
    })
  })

  describe('error messages', () => {
    const errorMsg: SessionMessage = {
      role: 'assistant',
      content: 'Something went wrong',
      timestamp: new Date().toISOString(),
      isError: true,
    }

    it('renders error message as plain text', () => {
      const wrapper = createWrapper(errorMsg)
      expect(wrapper.text()).toContain('Something went wrong')
      expect(wrapper.find('pre').exists()).toBe(true)
    })

    it('does not show copy button for error messages', () => {
      const wrapper = createWrapper(errorMsg)
      expect(wrapper.find('.msg-copy-btn').exists()).toBe(false)
    })
  })
})
