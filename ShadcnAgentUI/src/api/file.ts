import http from './request'
import type { FileRecord } from '@/types/api'

export function uploadFileApi(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<any, FileRecord>('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
