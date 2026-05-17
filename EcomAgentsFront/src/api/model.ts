import http from './request'
import type { ApiResponse, AiModel } from '../types/api'

export function listModelsApi() {
  return http.get<ApiResponse<AiModel[]>>('/models')
}

export function getDefaultModelApi() {
  return http.get<ApiResponse<AiModel>>('/models/default')
}

export function createModelApi(data: Partial<AiModel>) {
  return http.post<ApiResponse<AiModel>>('/models', data)
}

export function updateModelApi(id: number, data: Partial<AiModel>) {
  return http.put<ApiResponse<AiModel>>(`/models/${id}`, data)
}

export function deleteModelApi(id: number) {
  return http.delete<ApiResponse<void>>(`/models/${id}`)
}

export interface ModelValidateRequest {
  baseUrl: string
  apiType: string
  apiVersion: string
  apiKey: string
}

export function validateModelApi(data: ModelValidateRequest) {
  return http.post<ApiResponse<string[]>>('/models/validate', data)
}
