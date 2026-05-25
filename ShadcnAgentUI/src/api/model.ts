import http from './request'
import type { AiModel } from '@/types/api'

export function listModelsApi() {
  return http.get<any, AiModel[]>('/models')
}

export function getDefaultModelApi() {
  return http.get<any, AiModel>('/models/default')
}

export function createModelApi(data: Partial<AiModel>) {
  return http.post<any, AiModel>('/models', data)
}

export function updateModelApi(id: number, data: Partial<AiModel>) {
  return http.put<any, AiModel>(`/models/${id}`, data)
}

export function deleteModelApi(id: number) {
  return http.delete<any, void>(`/models/${id}`)
}

export interface ModelValidateRequest {
  baseUrl: string
  provider?: string
  apiType: string
  apiKey: string
}

export function validateModelApi(data: ModelValidateRequest) {
  return http.post<any, string[]>('/models/validate', data)
}
