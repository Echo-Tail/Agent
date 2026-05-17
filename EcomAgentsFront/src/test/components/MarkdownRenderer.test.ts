import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import MarkdownRenderer from '../../components/MarkdownRenderer.vue'

beforeEach(() => {
  vi.stubGlobal('navigator', {
    clipboard: {
      writeText: vi.fn().mockResolvedValue(undefined),
    },
  })
})

describe('MarkdownRenderer', () => {
  it('renders plain text content', () => {
    const wrapper = mount(MarkdownRenderer, {
      props: { content: 'Hello world' },
    })
    expect(wrapper.text()).toContain('Hello world')
  })

  it('renders markdown headings', () => {
    const wrapper = mount(MarkdownRenderer, {
      props: { content: '# Title\n## Subtitle' },
    })
    expect(wrapper.html()).toContain('<h1')
    expect(wrapper.html()).toContain('<h2')
  })

  it('renders fenced code block with pre/code structure', () => {
    const wrapper = mount(MarkdownRenderer, {
      props: { content: '```js\nconsole.log("hi")\n```' },
    })
    const html = wrapper.html()
    expect(html).toContain('<pre')
    expect(html).toContain('<code')
  })

  it('renders a copy button for each code block', () => {
    const wrapper = mount(MarkdownRenderer, {
      props: { content: '```js\nconst x = 1\n```\n\n```py\nprint("hello")\n```' },
    })
    const buttons = wrapper.findAll('.md-copy-btn')
    expect(buttons.length).toBe(2)
  })

  it('copies code content when copy button is clicked', async () => {
    const wrapper = mount(MarkdownRenderer, {
      props: { content: '```js\nconst x = 42\n```' },
    })

    const btn = wrapper.find('.md-copy-btn')
    expect(btn.exists()).toBe(true)

    await btn.trigger('click')

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('const x = 42')
  })

  it('adds copied class on click and removes it after timeout', async () => {
    vi.useFakeTimers()

    const wrapper = mount(MarkdownRenderer, {
      props: { content: '```js\nconst x = 1\n```' },
    })

    const btn = wrapper.find('.md-copy-btn')
    await btn.trigger('click')

    expect(btn.classes()).toContain('copied')

    vi.advanceTimersByTime(1500)

    expect(btn.classes()).not.toContain('copied')

    vi.useRealTimers()
  })

  it('renders inline code', () => {
    const wrapper = mount(MarkdownRenderer, {
      props: { content: 'Use the `console.log()` function' },
    })
    expect(wrapper.html()).toContain('<code')
  })

  it('renders links', () => {
    const wrapper = mount(MarkdownRenderer, {
      props: { content: '[click me](https://example.com)' },
    })
    const html = wrapper.html()
    expect(html).toContain('<a')
    expect(html).toContain('https://example.com')
  })

  it('renders tables', () => {
    const wrapper = mount(MarkdownRenderer, {
      props: { content: '| A | B |\n|---|---|\n| 1 | 2 |' },
    })
    const html = wrapper.html()
    expect(html).toContain('<table')
    expect(html).toContain('<th')
    expect(html).toContain('<td')
  })

  it('renders blockquotes', () => {
    const wrapper = mount(MarkdownRenderer, {
      props: { content: '> quoted text' },
    })
    expect(wrapper.html()).toContain('<blockquote')
  })
})
