import http from './request'
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
  return http.get<any, SkillDefinition[]>('/skills')
}

export function importFromUrlApi(url: string) {
  return http.post<any, void>('/skills/import-url', { url }, {
    timeout: 300000,
  })
}

export function uploadSkillZipApi(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<any, SkillUploadResult>('/skills/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function deleteSkillApi(name: string) {
  return http.delete<any, void>(`/skills/${encodeURIComponent(name)}`)
}
