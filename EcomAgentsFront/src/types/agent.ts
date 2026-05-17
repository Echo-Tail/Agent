export interface Agent {
  id: number
  name: string
  icon: string
  description: string
  tags: string[]
  systemPrompt: string
  greeting: string
  tools: string[]
  knowledgeBaseIds: number[]
  modelId: number
  isSystem?: boolean
  status: 'active' | 'disabled'
  createdAt: string
  createdBy: number
}

export interface AgentSummary {
  total: number
  active: number
  disabled: number
}

export interface AgentCreateRequest {
  name: string
  icon?: string
  description?: string
  tags?: string[]
  systemPrompt?: string
  greeting?: string
  tools?: string[]
  knowledgeBaseIds?: number[]
  modelId?: number
}

export interface AgentUpdateRequest {
  name?: string
  icon?: string
  description?: string
  tags?: string[]
  systemPrompt?: string
  greeting?: string
  tools?: string[]
  knowledgeBaseIds?: number[]
  modelId?: number
  status?: 'active' | 'disabled'
}
