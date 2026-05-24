import http from './request'
import type { KnowledgeBase, KnowledgeDocument } from '@/types/knowledge'

export function listKnowledgeBasesApi() {
  return http.get<any, KnowledgeBase[]>('/knowledge-bases')
}

export function getKnowledgeBaseApi(id: number) {
  return http.get<any, KnowledgeBase>(`/knowledge-bases/${id}`)
}

export function createKnowledgeBaseApi(data: { name: string; description?: string }) {
  return http.post<any, KnowledgeBase>('/knowledge-bases', data)
}

export function updateKnowledgeBaseApi(id: number, data: { name?: string; description?: string }) {
  return http.put<any, KnowledgeBase>(`/knowledge-bases/${id}`, data)
}

export function deleteKnowledgeBaseApi(id: number) {
  return http.delete<any, void>(`/knowledge-bases/${id}`)
}

export function listDocumentsApi(kbId: number) {
  return http.get<any, KnowledgeDocument[]>(`/knowledge-bases/${kbId}/documents`)
}

export function uploadDocumentApi(kbId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<any, KnowledgeDocument>(`/knowledge-bases/${kbId}/documents`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function deleteDocumentApi(kbId: number, docId: number) {
  return http.delete<any, void>(`/knowledge-bases/${kbId}/documents/${docId}`)
}

export function searchDocumentsApi(q: string) {
  return http.get<any, KnowledgeDocument[]>('/knowledge-bases/search', { params: { q } })
}
