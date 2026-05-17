import http from './request'
import type { ApiResponse } from '../types/api'
import type { FileRecord } from '../types/api'

export function uploadFileApi(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<ApiResponse<FileRecord>>('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
