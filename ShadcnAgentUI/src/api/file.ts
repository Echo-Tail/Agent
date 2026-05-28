/**
 * 文件上传 / 下载 / 列表 API
 *
 * 通用文件存储服务，支持上传、列表、下载操作。
 * 文件按对话上下文隔离（contextType + contextId）。
 */
import http from './request'
import type { FileRecord } from '@/types/api'

/**
 * 上传文件到通用文件存储
 *
 * @param file - 要上传的文件（支持 TXT/MD/PDF/PNG/JPG/JSON/CSV/XML 等）
 * @param contextType - 对话上下文类型（'PRIVATE' / 'AGENT'），可选
 * @param contextId - 对话上下文 ID（对方用户 ID / Agent ID），可选
 * @returns 上传成功后的 FileRecord（含 id 用于下载）
 */
export function uploadFileApi(file: File, contextType?: string, contextId?: number) {
  const formData = new FormData()
  formData.append('file', file)
  if (contextType) formData.append('contextType', contextType)
  if (contextId !== undefined) formData.append('contextId', String(contextId))
  return http.post<any, FileRecord>('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/**
 * 获取指定对话上下文中当前用户上传的文件列表
 *
 * @param contextType - 对话上下文类型
 * @param contextId - 对话上下文 ID
 * @returns 文件列表（按上传时间降序排列）
 */
export function listMyFilesApi(contextType: string, contextId: number) {
  return http.get<any, FileRecord[]>('/files', {
    params: { contextType, contextId },
  })
}

/**
 * 下载文件（返回 Blob 对象，用于浏览器触发下载）
 *
 * @param id - 文件记录 ID
 * @returns Blob 数据
 */
export async function downloadFileApi(id: number): Promise<Blob> {
  const token = localStorage.getItem('ecomagents_token')
  const response = await fetch(`/v1/files/${id}/download`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!response.ok) throw new Error('下载失败')
  return response.blob()
}
