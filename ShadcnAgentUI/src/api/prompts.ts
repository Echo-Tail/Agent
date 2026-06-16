import { api } from './request'
import type { PageResponse } from './assets'

export interface PromptLibrary {
  id: number
  prompt: string
  category: string
  tags: string | null
  coverPath: string | null
  createdBy: number
  createdAt: string
}

export function listPrompts(params: {
  category?: string
  createdBy?: number
  excludeUser?: number
  keyword?: string
  page?: number
  size?: number
}) {
  return api.get<PageResponse<PromptLibrary>>('/prompts', { params })
}

export function getPrompt(id: number) {
  return api.get<PromptLibrary>('/prompts/' + id)
}

export function createPrompt(formData: FormData) {
  return api.post<PromptLibrary>('/prompts', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function updatePrompt(id: number, formData: FormData) {
  return api.put<PromptLibrary>('/prompts/' + id, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function setCoverRef(id: number, coverPath: string) {
  return api.put<PromptLibrary>('/prompts/' + id + '/cover-ref', { coverPath })
}

export function deletePrompt(id: number) {
  return api.delete('/prompts/' + id)
}
