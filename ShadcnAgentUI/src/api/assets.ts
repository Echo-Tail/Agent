import { api } from './request'
import type { AxiosProgressEvent } from 'axios'

export interface AssetSpace {
  id: number
  name: string
  description: string | null
  createdBy: number
  createdAt: string
  updatedAt: string
}

export interface PublicAsset {
  id: number
  fileName: string
  filePath: string
  fileSize: number
  mimeType: string
  space: AssetSpace | null
  uploadedBy: number
  createdAt: string
}

export interface PageResponse<T> {
  content: T[]
  page: { size: number; number: number; totalElements: number; totalPages: number }
}

export function listSpaces() {
  return api.get<AssetSpace[]>('/assets/spaces')
}

export function createSpace(name: string, description?: string) {
  return api.post<AssetSpace>('/assets/spaces', { name, description })
}

export function updateSpace(id: number, name: string, description?: string) {
  return api.put<AssetSpace>('/assets/spaces/' + id, { name, description })
}

export function deleteSpace(id: number) {
  return api.delete('/assets/spaces/' + id)
}

export function listAssets(params: { spaceId?: number; keyword?: string; uploadedBy?: number; startDate?: string; endDate?: string; page?: number; size?: number }) {
  return api.get<PageResponse<PublicAsset>>('/assets', { params })
}

export function uploadAsset(file: File, spaceId?: number, onProgress?: (e: AxiosProgressEvent) => void) {
  const fd = new FormData()
  fd.append('file', file)
  if (spaceId != null) fd.append('spaceId', String(spaceId))
  return api.post<PublicAsset>('/assets/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress,
  })
}

export function deleteAsset(id: number) {
  return api.delete('/assets/' + id)
}

export function importFromRecord(recordId: number, spaceId?: number) {
  return api.post<PublicAsset>('/assets/from-record/' + recordId, null, {
    params: { spaceId },
  })
}
