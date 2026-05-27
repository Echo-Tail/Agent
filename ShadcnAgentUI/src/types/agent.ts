export interface Agent {
  id: number
  name: string
  icon: string
  avatar?: string
  description: string
  tags: string[]
  systemPrompt: string
  greeting: string
  tools: string[]
  skills: string[]
  knowledgeBaseIds: number[]
  modelId: number
  isSystem?: boolean
  status: 'active' | 'disabled'
  createdAt: string
  createdBy: number
  ragMode: 'GENERIC' | 'AGENTIC'
}

export interface ToolAvailability {
  toolId: string
  agentId: number
  boundToAgent: boolean
  globallyEnabled: boolean
  configured: boolean
  available: boolean
  message: string
}

export interface AgentSummary {
  total: number
  active: number
  disabled: number
}

export interface AgentCreateRequest {
  name: string
  icon?: string
  avatar?: string
  description?: string
  tags?: string[]
  systemPrompt?: string
  greeting?: string
  tools?: string[]
  skills?: string[]
  knowledgeBaseIds?: number[]
  modelId?: number
  ragMode?: 'GENERIC' | 'AGENTIC'
}

export interface AgentUpdateRequest {
  name?: string
  icon?: string
  avatar?: string
  description?: string
  tags?: string[]
  systemPrompt?: string
  greeting?: string
  tools?: string[]
  skills?: string[]
  knowledgeBaseIds?: number[]
  modelId?: number
  status?: 'active' | 'disabled'
  ragMode?: 'GENERIC' | 'AGENTIC'
}
