import { api } from './request'
import type { ImageGenerationResult, PageResponse } from './image'

export interface AmazonImageTask {
  id: number
  userId: number
  profileVersionId: number | null
  asin: string | null
  taskName: string
  marketplace: string
  category: string
  subcategory: string | null
  imageType: string
  mode: 'PRODUCTION' | 'EXPERIMENT'
  status: string
  sourceType: string | null
  sourceUrls: string | null
  referenceImageUrls: string | null
  productFactsJson: string | null
  imageExpressionJson: string | null
  sourceMaterialFactsJson: string | null
  selectedExpressionId: number | null
  checkedMaterialFactKeys: string | null
  promptJson: string | null
  promptText: string | null
  negativePrompt: string | null
  modelId: number | null
  generationRecordId: number | null
  resultPaths: string | null
  brightDataJobId: string | null
  collectionStatus: string | null
  notes: string | null
  createdAt: string
  updatedAt: string
}

export interface AmazonImageResult {
  id: number
  taskId: number
  imagePath: string
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED'
  imageIndex: number
  createdAt: string
}

export interface CreateAmazonImageTaskRequest {
  profileVersionId?: number
  taskName?: string
  asin?: string
  marketplace?: string
  category?: string
  subcategory?: string
  imageType: string
  mode?: string
  sourceType?: string
  sourceUrls?: string
  referenceImageUrls?: string
  notes?: string
}

export interface UpdateFactsRequest {
  taskName?: string
  subcategory?: string
  productFactsJson?: string
  notes?: string
}

export interface UpdatePromptRequest {
  promptJson?: string
  promptText?: string
  negativePrompt?: string
  modelId?: number
}

export interface AnalyzeExpressionRequest {
  imageExpressionJson: string
}

export interface UpdateMaterialFactsRequest {
  sourceMaterialFactsJson?: string
  checkedMaterialFactKeys?: string
}

export interface MarkResultRequest {
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED'
}

export interface GenerateTaskResult {
  task: AmazonImageTask
  generation: ImageGenerationResult
  results: AmazonImageResult[]
}

export function listAmazonImageTasks(params?: {
  page?: number
  size?: number
  asin?: string
  imageType?: string
  status?: string
}) {
  return api.get<PageResponse<AmazonImageTask>>('/amazon-image-tasks', { params })
}

export function createAmazonImageTask(data: CreateAmazonImageTaskRequest) {
  return api.post<AmazonImageTask>('/amazon-image-tasks', data)
}

export function getAmazonImageTask(id: number) {
  return api.get<AmazonImageTask>(`/amazon-image-tasks/${id}`)
}

export function updateAmazonImageTaskFacts(id: number, data: UpdateFactsRequest) {
  return api.put<AmazonImageTask>(`/amazon-image-tasks/${id}/facts`, data)
}

export function updateAmazonImageTaskPrompt(id: number, data: UpdatePromptRequest) {
  return api.put<AmazonImageTask>(`/amazon-image-tasks/${id}/prompt`, data)
}

export function analyzeAmazonImageExpression(id: number, data: AnalyzeExpressionRequest) {
  return api.post<AmazonImageTask>(`/amazon-image-tasks/${id}/analyze-expression`, data)
}

export function updateAmazonImageMaterialFacts(id: number, data: UpdateMaterialFactsRequest) {
  return api.put<AmazonImageTask>(`/amazon-image-tasks/${id}/material-facts`, data)
}

export function generateAmazonImageTask(id: number, data: {
  prompt?: string
  size?: string
  quality?: string
  images?: File[]
  n?: number
  modelId?: number
}) {
  const fd = new FormData()
  if (data.prompt) fd.append('prompt', data.prompt)
  if (data.size) fd.append('size', data.size)
  if (data.quality) fd.append('quality', data.quality)
  if (data.n != null) fd.append('n', String(data.n))
  if (data.modelId != null) fd.append('modelId', String(data.modelId))
  data.images?.forEach(file => fd.append('image', file))
  return api.post<GenerateTaskResult>(`/amazon-image-tasks/${id}/generate`, fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 600_000,
  })
}

export function getAmazonImageTaskResults(id: number) {
  return api.get<AmazonImageResult[]>(`/amazon-image-tasks/${id}/results`)
}

export function markAmazonImageResult(resultId: number, data: MarkResultRequest) {
  return api.put<AmazonImageResult>(`/amazon-image-tasks/results/${resultId}/status`, data)
}

export function completeAmazonImageTask(id: number) {
  return api.post<AmazonImageTask>(`/amazon-image-tasks/${id}/complete`)
}

export function deleteAmazonImageTask(id: number) {
  return api.delete(`/amazon-image-tasks/${id}`)
}
