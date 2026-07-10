import { api } from './request'

export interface GeneratedImage {
  recordId: number
  url: string
  width: number | null
  height: number | null
}

export interface ImageGenerationResult {
  urls: string[]
  revisedPrompt: string | null
  timeCostMs: number
  recordId: number
  failedCount: number
  images: GeneratedImage[]
}


export interface SuperResolutionRequest {
  recordId?: number
  imageUrl?: string
  mode?: string
  upscaleFactor?: number
  outputFormat?: 'png' | 'jpg' | 'bmp'
  outputQuality?: number
  saveToLocal?: boolean
}

export interface SuperResolutionResult {
  sourceUrl: string
  remoteUrl: string
  savedPath: string | null
  mode: string
  upscaleFactor: number
  outputFormat: string
  width: number | null
  height: number | null
  timeCostMs: number
}
export type SuperResolutionOrigin = 'IMAGE_GENERATION' | 'SUPER_RESOLUTION_PAGE'

export interface SuperResolutionJob {
  id: number
  sourceRecordId: number | null
  sourceType: 'HISTORY' | 'UPLOAD'
  origin: SuperResolutionOrigin
  upscaleFactor: number
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  sourcePath: string
  sourceWidth: number | null
  sourceHeight: number | null
  resultPath: string | null
  resultRecordId: number | null
  width: number | null
  height: number | null
  timeCostMs: number | null
  errorMessage: string | null
  sourceAvailable: boolean
  createdAt: string
  completedAt: string | null
}
export interface ImageRecord {
  id: number
  userId: number
  mode: 'GENERATE' | 'EDIT' | 'SUPER_RESOLUTION'
  prompt: string
  revisedPrompt: string | null
  size: string
  quality: string
  resultPath: string
  timeCostMs: number
  width?: number
  height?: number
  sourceRecordId?: number | null
  upscaleFactor?: number | null
  /** 参考图片路径（图生图模式），多张以换行分隔 */
  referenceImagePaths?: string | null
  /** 遮罩图路径（图生图模式） */
  maskImagePath?: string | null
  createdAt: string
}

/** Spring Data Page 结构 */
export interface PageResponse<T> {
  content: T[]
  page: {
    size: number
    number: number
    totalElements: number
    totalPages: number
  }
}

export interface RecordQuery {
  page?: number
  size?: number
  startDate?: string
  endDate?: string
  prompt?: string
}

/**
 * 文生图 — 根据文字描述生成图片。
 * @param n 生成张数（1~10），默认 1
 */
export function generateImage(prompt: string, size?: string, quality?: string, n: number = 1, modelId?: number) {
  const body: Record<string, string> = { prompt, n: String(n) }
  if (size) body.size = size
  if (quality) body.quality = quality
  if (modelId) body.modelId = String(modelId)
  return api.post<ImageGenerationResult>('/images/generate', body, {
    timeout: 600_000,
  })
}

/**
 * 图生图 — 上传参考图片进行编辑。
 * @param mask 可选遮罩图（PNG，透明区域=重绘区域，作用于第一张参考图）
 * @param n 生成张数（1~10），默认 1
 */
export function editImage(prompt: string, images: File[], size?: string, quality?: string, mask?: File, n: number = 1, modelId?: number) {
  const formData = new FormData()
  formData.append('prompt', prompt)
  if (size) formData.append('size', size)
  if (quality) formData.append('quality', quality)
  formData.append('n', String(n))
  if (modelId) formData.append('modelId', String(modelId))
  images.forEach(file => formData.append('image', file))
  if (mask) formData.append('mask', mask)
  return api.post<ImageGenerationResult>('/images/edit', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 600_000, // 10 minutes for image generation
  })
}

/**
 * 分页查询当前用户的图片生成历史记录。
 */
export function listImageRecords(query?: RecordQuery) {
  const params: Record<string, any> = {}
  if (query) {
    if (query.page != null) params.page = query.page
    if (query.size != null) params.size = query.size
    if (query.startDate) params.startDate = query.startDate
    if (query.endDate) params.endDate = query.endDate
    if (query.prompt) params.prompt = query.prompt
  }
  return api.get<PageResponse<ImageRecord>>('/images/records', { params })
}

/**
 * 查询单条图片生成记录详情（含参考图和遮罩图路径）。
 */
export function getImageRecordApi(id: number) {
  return api.get<ImageRecord>(`/images/records/${id}`)
}

/**
 * 删除一条历史记录。
 */
export function deleteImageRecord(id: number) {
  return api.delete(`/images/records/${id}`)
}

export function analyzeImageExpression(imageUrl: string) {
  return api.post<string>('/images/analyze-expression', null, {
    params: { imageUrl },
    timeout: 360_000,
  })
}

export function collectAsinImages(asin: string) {
  return api.post<string[]>('/images/collect-asin-images', null, {
    params: { asin },
    timeout: 120_000,
  })
}

export function analyzeExpressionCached(imageUrl: string) {
  return api.post<string>('/images/analyze-expression-cached', null, {
    params: { imageUrl },
    timeout: 360_000,
  })
}

export function uploadLocalImage(file: File) {
  const fd = new FormData()
  fd.append('file', file)
  return api.post<string>('/images/upload-local', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function upscaleImage(request: SuperResolutionRequest) {
  return api.post<SuperResolutionResult>('/images/super-resolution', request, {
    timeout: 600_000,
  })
}
export function createSuperResolutionJob(
  recordId: number,
  upscaleFactor: number,
  origin: SuperResolutionOrigin = 'IMAGE_GENERATION',
) {
  return api.post<SuperResolutionJob>('/images/super-resolution/jobs', { recordId, upscaleFactor, origin })
}

export function uploadSuperResolutionJob(
  file: File,
  upscaleFactor: number,
  origin: SuperResolutionOrigin = 'SUPER_RESOLUTION_PAGE',
) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('upscaleFactor', String(upscaleFactor))
  formData.append('origin', origin)
  return api.post<SuperResolutionJob>('/images/super-resolution/jobs/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function retrySuperResolutionJob(jobId: number) {
  return api.post<SuperResolutionJob>(`/images/super-resolution/jobs/${jobId}/retry`)
}

export function listSuperResolutionJobs(origin?: SuperResolutionOrigin) {
  return api.get<SuperResolutionJob[]>('/images/super-resolution/jobs', {
    params: origin ? { origin } : undefined,
    silent: true,
    skipRetry: true,
  })
}

export function getActiveSuperResolutionJobCount() {
  return api.get<number>('/images/super-resolution/jobs/active-count', { silent: true, skipRetry: true })
}

export function listSuperResolutionSources(query?: RecordQuery) {
  const params: Record<string, unknown> = { page: query?.page ?? 0, size: query?.size ?? 12 }
  if (query?.startDate) params.startDate = query.startDate
  if (query?.endDate) params.endDate = query.endDate
  if (query?.prompt) params.prompt = query.prompt
  return api.get<PageResponse<ImageRecord>>('/images/super-resolution/sources', { params })
}
