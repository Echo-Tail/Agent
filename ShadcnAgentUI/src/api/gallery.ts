import http from './request'
import type { PageResponse } from './image'

/** 画廊作品响应 */
export interface GalleryItem {
  id: number
  recordId: number
  userId: number
  title: string
  categoryTags: string | null
  styleTags: string | null
  negativePrompt: string | null
  status: string
  viewCount: number
  imageUrl: string
  prompt: string
  revisedPrompt: string | null
  size: string
  quality: string
  mode: string
  authorName: string
  createdAt: string
  updatedAt: string | null
}

/** 发布到画廊的请求 */
export interface GalleryPublishRequest {
  recordId: number
  title?: string
  categoryTags?: string
  styleTags?: string
  negativePrompt?: string
}

/**
 * 发布作品到画廊。
 */
export function publishToGallery(data: GalleryPublishRequest) {
  return http.post<any, GalleryItem>('/gallery/items', data)
}

/**
 * 分页查询画廊作品列表。
 */
export function getGalleryItems(page = 0, size = 20) {
  return http.get<any, PageResponse<GalleryItem>>('/gallery/items', {
    params: { page, size },
  })
}

/**
 * 获取画廊作品详情。
 */
export function getGalleryDetail(id: number) {
  return http.get<any, GalleryItem>(`/gallery/items/${id}`)
}

/**
 * 创作者取消发布自己的作品。
 */
export function unpublishFromGallery(id: number) {
  return http.delete<any, void>(`/gallery/items/${id}`)
}

/**
 * 管理员下架作品。
 */
export function adminRemoveGalleryItem(id: number) {
  return http.delete<any, void>(`/gallery/items/${id}/admin`)
}

/**
 * 获取当前用户可发布的历史记录（排除已发布的）。
 */
export function getPublishableRecords() {
  return http.get<any, Array<{
    id: number
    prompt: string
    size: string
    resultPath: string
    createdAt: string
    mode: string
  }>>('/gallery/my-records')
}
