import http from './request'
import type { ApiResponse } from '../types/api'
import type { SkillDefinition } from '../types/api'

export function listSkillsApi() {
  return http.get<ApiResponse<SkillDefinition[]>>('/skills')
}

export function importFromUrlApi(url: string) {
  return http.post<ApiResponse<void>>('/skills/import-url', { url }, {
    timeout: 300000, // 5 minutes to match backend npx timeout
  })
}

export function uploadSkillZipApi(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<ApiResponse<void>>('/skills/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function deleteSkillApi(name: string) {
  return http.delete<ApiResponse<void>>(`/skills/${encodeURIComponent(name)}`)
}
