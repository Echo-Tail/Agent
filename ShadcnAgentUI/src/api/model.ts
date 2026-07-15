import { api } from './request'
import type { AiModel, AiModelCapability, ModelCredential } from '@/types/api'

export function listModelsApi() {
  return api.get<AiModel[]>('/models')
}

export function getModelApi(id: number) {
  return api.get<AiModel>(`/models/${id}`)
}

export function getDefaultModelApi() {
  return api.get<AiModel>('/models/default')
}

export function getImageModelsApi(capability: 'TEXT_TO_IMAGE' | 'IMAGE_TO_IMAGE' = 'TEXT_TO_IMAGE') {
  return api.get<AiModel[]>('/models/image', { params: { capability } })
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

export function listModelCapabilitiesApi(modelId: number) {
  return api.get<AiModelCapability[]>(`/models/${modelId}/capabilities`)
}

export function replaceModelCapabilitiesApi(modelId: number, capabilities: AiModelCapability[]) {
  return api.put<AiModelCapability[]>(`/models/${modelId}/capabilities`, capabilities)
}

export function listModelCredentialsApi() {
  return api.get<ModelCredential[]>('/model-credentials')
}

export function createModelCredentialApi(data: { name: string; provider: string; secret: string }) {
  return api.post<ModelCredential>('/model-credentials', data)
}

export function rotateModelCredentialApi(id: number, secret: string) {
  return api.put<ModelCredential>(`/model-credentials/${id}/secret`, { secret })
}

export function deleteModelCredentialApi(id: number) {
  return api.delete(`/model-credentials/${id}`)
}
