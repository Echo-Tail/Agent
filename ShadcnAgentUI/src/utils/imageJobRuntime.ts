import type { GeneratedImage, ImageGenerationResult, ImageJob, ImageJobStatus, ImageRecord } from '@/api/image'

const terminalStatuses = new Set<ImageJobStatus>([
  'SUCCEEDED', 'PARTIALLY_SUCCEEDED', 'FAILED', 'CANCELLED',
])

export function isImageJobTerminal(status: ImageJobStatus): boolean {
  return terminalStatuses.has(status)
}

export function imageJobPollDelay(attempts: number): number {
  if (attempts < 10) return 1000
  if (attempts < 30) return 2000
  return 3000
}

export function imageJobResult(job: ImageJob, records: ImageRecord[]): ImageGenerationResult {
  const successful = records.filter(record => record.status !== 'FAILED' && Boolean(record.resultPath))
  const images: GeneratedImage[] = successful.map(record => ({
    recordId: record.id,
    url: record.resultPath,
    width: record.width ?? null,
    height: record.height ?? null,
  }))
  const started = job.startedAt ? new Date(job.startedAt).getTime() : new Date(job.createdAt).getTime()
  const completed = job.completedAt ? new Date(job.completedAt).getTime() : Date.now()
  return {
    urls: images.map(image => image.url),
    revisedPrompt: successful.find(record => record.revisedPrompt)?.revisedPrompt ?? null,
    timeCostMs: Math.max(0, completed - started),
    recordId: images[0]?.recordId ?? 0,
    failedCount: job.failureCount,
    images,
  }
}
