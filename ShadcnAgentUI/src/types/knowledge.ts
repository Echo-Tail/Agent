export interface KnowledgeBase {
  id: number
  name: string
  description: string
  createdAt: string
  createdBy: number
}

export interface KnowledgeDocument {
  id: number
  knowledgeBaseId: number
  fileName: string
  fileType: string
  content: string
  charCount: number
  uploadedAt: string
  uploadedBy: number
}
