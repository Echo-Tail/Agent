import http from './request'
import type { ApiResponse } from '../types/api'
import type { KnowledgeBase, KnowledgeDocument } from '../types/knowledge'

/* ====== Knowledge Base CRUD ====== */

export function listKnowledgeBasesApi() {
  return http.get<ApiResponse<KnowledgeBase[]>>('/knowledge-bases')
}

export function getKnowledgeBaseApi(id: number) {
  return http.get<ApiResponse<KnowledgeBase>>(`/knowledge-bases/${id}`)
}

export function createKnowledgeBaseApi(data: { name: string; description?: string }) {
  return http.post<ApiResponse<KnowledgeBase>>('/knowledge-bases', data)
}

export function updateKnowledgeBaseApi(id: number, data: { name?: string; description?: string }) {
  return http.put<ApiResponse<KnowledgeBase>>(`/knowledge-bases/${id}`, data)
}

export function deleteKnowledgeBaseApi(id: number) {
  return http.delete<ApiResponse<void>>(`/knowledge-bases/${id}`)
}

/* ====== Documents ====== */

export function listDocumentsApi(kbId: number) {
  return http.get<ApiResponse<KnowledgeDocument[]>>(`/knowledge-bases/${kbId}/documents`)
}

export function uploadDocumentApi(kbId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<ApiResponse<KnowledgeDocument>>(`/knowledge-bases/${kbId}/documents`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function deleteDocumentApi(kbId: number, docId: number) {
  return http.delete<ApiResponse<void>>(`/knowledge-bases/${kbId}/documents/${docId}`)
}

/* ====== Search ====== */

export function searchDocumentsApi(q: string) {
  return http.get<ApiResponse<KnowledgeDocument[]>>('/knowledge-bases/search', { params: { q } })
}
