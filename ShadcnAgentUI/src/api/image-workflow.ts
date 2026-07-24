import { api } from './request'

export interface ImageCanvasSession {
  id: number
  title: string
  status: 'ACTIVE' | 'ARCHIVED'
  thumbnailAssetId: number | null
  assetCount: number
  createdAt: string
  updatedAt: string
}

export interface ImageCanvasDocument {
  sessionId: number
  revision: number
  schemaVersion: number
  snapshot: Record<string, unknown>
  updatedAt: string
}

export interface ImageCanvasWorkspace {
  session: ImageCanvasSession
  canvas: ImageCanvasDocument | null
  assets: Array<{
    id: number
    sessionId: number
    type: string
    mimeType: string
    width: number
    height: number
    fileSize: number
    url: string
    createdAt: string
  }>
}

export interface ImageCanvasAsset {
  id: number
  sessionId: number
  type: string
  mimeType: string
  width: number
  height: number
  fileSize: number
  url: string
  createdAt: string
}

export function listImageCanvasSessions() {
  return api.get<ImageCanvasSession[]>('/image-sessions')
}

export function createImageCanvasSession(title: string) {
  return api.post<ImageCanvasSession>('/image-sessions', { title })
}

export function getImageCanvasWorkspace(sessionId: number) {
  return api.get<ImageCanvasWorkspace>(`/image-sessions/${sessionId}/workspace`)
}

export function saveImageCanvas(
  sessionId: number,
  request: { revision: number; schemaVersion: number; snapshot: Record<string, unknown> },
) {
  return api.put<ImageCanvasDocument>(`/image-sessions/${sessionId}/canvas`, request)
}

export function uploadImageCanvasAsset(sessionId: number, file: File, type = 'ORIGINAL') {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('type', type)
  return api.post<ImageCanvasAsset>(`/image-sessions/${sessionId}/assets`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
