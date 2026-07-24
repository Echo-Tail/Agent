import { describe, expect, it } from 'vitest'
import { normalizeWorkflowImageUrl, validateWorkflowUpscale } from '@/utils/imageWorkflow'

describe('image workflow helpers', () => {
  it('normalizes backend image paths without changing remote URLs', () => {
    expect(normalizeWorkflowImageUrl('uploads\\images\\result.png')).toBe('/uploads/images/result.png')
    expect(normalizeWorkflowImageUrl('https://cdn.example/result.png')).toBe('https://cdn.example/result.png')
  })

  it('accepts supported Aliyun upscale input', () => {
    expect(validateWorkflowUpscale({ recordId: 1, width: 1920, height: 1080 }, 4)).toBe('')
  })

  it('rejects missing records, unsupported factors and oversized input', () => {
    expect(validateWorkflowUpscale({ width: 1024, height: 1024 }, 2)).toContain('缺少记录')
    expect(validateWorkflowUpscale({ recordId: 1, width: 1024, height: 1024 }, 5)).toContain('仅支持')
    expect(validateWorkflowUpscale({ recordId: 1, width: 1921, height: 1080 }, 2)).toContain('超出')
    expect(validateWorkflowUpscale({ recordId: 1, width: 1200, height: 1081 }, 2)).toContain('超出')
  })
})
