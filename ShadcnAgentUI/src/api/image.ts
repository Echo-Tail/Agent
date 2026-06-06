import { api } from './request'

export interface ImageGenerationResult {
  url: string
  revisedPrompt: string | null
  timeCostMs: number
  recordId: number
}

export interface ImageRecord {
  id: number
  userId: number
  mode: 'GENERATE' | 'EDIT'
  prompt: string
  revisedPrompt: string | null
  size: string
  quality: string
  resultPath: string
  timeCostMs: number
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
 */
export function generateImage(prompt: string, size?: string, quality?: string) {
  return api.post<ImageGenerationResult>('/images/generate', { prompt, size, quality }, {
    timeout: 600_000, // 10 minutes for image generation
  })
}

/**
 * 图生图 — 上传参考图片进行编辑。
 */
export function editImage(prompt: string, images: File[], size?: string, quality?: string) {
  const formData = new FormData()
  formData.append('prompt', prompt)
  if (size) formData.append('size', size)
  if (quality) formData.append('quality', quality)
  images.forEach(file => formData.append('image', file))
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
 * 删除一条历史记录。
 */
export function deleteImageRecord(id: number) {
  return api.delete(`/images/records/${id}`)
}
