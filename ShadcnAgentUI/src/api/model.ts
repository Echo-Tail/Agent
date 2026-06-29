import { api } from './request'
import type { AiModel } from '@/types/api'

export function listModelsApi() {
  return api.get<AiModel[]>('/models')
}

export function getModelApi(id: number) {
  return api.get<AiModel>(`/models/${id}`)
}

export function getDefaultModelApi() {
  return api.get<AiModel>('/models/default')
}

export function getImageModelsApi() {
  return api.get<AiModel[]>('/models/image')
}

export function createModelApi(data: Partial<AiModel>) {
  return api.post<AiModel>('/models', data)
}

export function updateModelApi(id: number, data: Partial<AiModel>) {
  return api.put<AiModel>(`/models/${id}`, data)
}

export function deleteModelApi(id: number) {
  return api.delete(`/models/${id}`)
}

export interface ModelValidateRequest {
  baseUrl: string
  provider?: string
  apiType: string
  apiKey: string
}

export function validateModelApi(data: ModelValidateRequest) {
  return api.post<string[]>('/models/validate', data)
}
