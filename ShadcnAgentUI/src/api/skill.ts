import { api } from './request'
import type { SkillDefinition } from '@/types/api'

export interface SkillUploadFailedItem {
  name: string
  reason: string
}

export interface SkillUploadResult {
  successCount: number
  totalCount: number
  imported: string[]
  failed: SkillUploadFailedItem[]
}

export function listSkillsApi() {
  return api.get<SkillDefinition[]>('/skills')
}

export function importFromUrlApi(url: string) {
  return api.post<void>('/skills/import-url', { url }, {
    timeout: 300000,
  })
}

export function uploadSkillZipApi(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return api.post<SkillUploadResult>('/skills/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function deleteSkillApi(name: string) {
  return api.delete(`/skills/${encodeURIComponent(name)}`)
}
