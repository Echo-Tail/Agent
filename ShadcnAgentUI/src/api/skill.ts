import http from './request'
import type { SkillDefinition } from '@/types/api'

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
  return http.post<any, void>('/skills/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function deleteSkillApi(name: string) {
  return http.delete<any, void>(`/skills/${encodeURIComponent(name)}`)
}
