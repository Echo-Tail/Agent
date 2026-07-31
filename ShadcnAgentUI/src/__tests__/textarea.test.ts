import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { Textarea } from '@/components/ui/textarea'

describe('Textarea', () => {
  it('limits content height and enables vertical scrolling when maxRows is set', () => {
    const wrapper = mount(Textarea, {
      props: {
        modelValue: Array.from({ length: 26 }, (_, index) => `line ${index + 1}`).join('\n'),
        maxRows: 25,
      },
    })

    const textarea = wrapper.get('textarea')
    expect(textarea.attributes('style')).toContain('max-height: calc(25 * 1.25rem + 1rem)')
    expect(textarea.classes()).toContain('overflow-y-auto')
  })

  it('keeps the default auto-growing behavior when maxRows is omitted', () => {
    const wrapper = mount(Textarea, {
      props: { modelValue: 'short prompt' },
    })

    const textarea = wrapper.get('textarea')
    expect(textarea.attributes('style')).toBeUndefined()
    expect(textarea.classes()).not.toContain('overflow-y-auto')
  })
})
