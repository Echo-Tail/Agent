import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ChartTooltipContent from './ChartTooltipContent.vue'

describe('ChartTooltipContent', () => {
  it('renders a dimension label and its review count', () => {
    const wrapper = mount(ChartTooltipContent, {
      props: {
        payload: {
          key: 'critical',
          label: 'P0 · 安全风险',
          count: 12,
          color: 'var(--chart-1)',
        },
        config: {
          count: { label: '评论数量', color: 'var(--chart-1)' },
        },
        labelKey: 'label',
      },
    })

    expect(wrapper.text()).toContain('P0 · 安全风险')
    expect(wrapper.text()).toContain('评论数量')
    expect(wrapper.text()).toContain('12')
  })
})
