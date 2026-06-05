import { api } from './request'
import type { KnowledgeBase, KnowledgeDocument } from '@/types/knowledge'

export function listKnowledgeBasesApi() {
  return api.get<KnowledgeBase[]>('/knowledge-bases')
}

export function getKnowledgeBaseApi(id: number) {
  return api.get<KnowledgeBase>(`/knowledge-bases/${id}`)
}

export function createKnowledgeBaseApi(data: { name: string; description?: string }) {
  return api.post<KnowledgeBase>('/knowledge-bases', data)
}

export function updateKnowledgeBaseApi(id: number, data: { name?: string; description?: string }) {
  return api.put<KnowledgeBase>(`/knowledge-bases/${id}`, data)
}

export function deleteKnowledgeBaseApi(id: number) {
  return api.delete(`/knowledge-bases/${id}`)
}

export function listDocumentsApi(kbId: number) {
  return api.get<KnowledgeDocument[]>(`/knowledge-bases/${kbId}/documents`)
}

export function uploadDocumentApi(kbId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return api.post<KnowledgeDocument>(`/knowledge-bases/${kbId}/documents`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function uploadDocumentsApi(kbId: number, files: File[]) {
  const formData = new FormData()
  files.forEach((file) => formData.append('files', file))
  return api.post<KnowledgeDocument[]>(`/knowledge-bases/${kbId}/documents/batch`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function deleteDocumentApi(kbId: number, docId: number) {
  return api.delete(`/knowledge-bases/${kbId}/documents/${docId}`)
}

export function searchDocumentsApi(q: string) {
  return api.get<KnowledgeDocument[]>('/knowledge-bases/search', { params: { q } })
}
