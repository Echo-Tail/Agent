import { describe, expect, it } from 'vitest'
import type { ImageJob, ImageRecord } from '@/api/image'
import { imageJobPollDelay, imageJobResult, isImageJobTerminal } from './imageJobRuntime'

const job = {
  id: 9, modelId: 2, retryOfJobId: null, mode: 'TEXT_TO_IMAGE', prompt: 'test', negativePrompt: null,
  targetCount: 2, provider: 'qwen', protocol: 'BAILIAN_IMAGE', remoteModelName: 'qwen-image-2.0-pro',
  capability: 'TEXT_TO_IMAGE', status: 'PARTIALLY_SUCCEEDED', executionPhase: null,
  successCount: 1, failureCount: 1, errorCode: null, safeErrorMessage: null, retryable: false,
  createdAt: '2026-07-14T10:00:00Z', startedAt: '2026-07-14T10:00:01Z',
  completedAt: '2026-07-14T10:00:06Z', updatedAt: '2026-07-14T10:00:06Z',
} satisfies ImageJob

describe('image job runtime helpers', () => {
  it('recognizes terminal states and applies bounded polling backoff', () => {
    expect(isImageJobTerminal('RUNNING')).toBe(false)
    expect(isImageJobTerminal('PARTIALLY_SUCCEEDED')).toBe(true)
    expect(imageJobPollDelay(0)).toBe(1000)
    expect(imageJobPollDelay(10)).toBe(2000)
    expect(imageJobPollDelay(30)).toBe(3000)
  })

  it('keeps successful records when a job partially succeeds', () => {
    const records = [
      { id: 1, resultPath: '/uploads/one.png', status: 'SUCCEEDED', revisedPrompt: 'revised', width: 100, height: 200 },
      { id: 2, resultPath: '', status: 'FAILED' },
    ] as ImageRecord[]
    const result = imageJobResult(job, records)
    expect(result.urls).toEqual(['/uploads/one.png'])
    expect(result.failedCount).toBe(1)
    expect(result.timeCostMs).toBe(5000)
  })
})
