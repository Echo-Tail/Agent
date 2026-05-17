export interface SessionMessage {
  role: 'user' | 'assistant'
  content: string
  timestamp: string
  isError?: boolean
}

export interface Session {
  id: number
  agentId: number
  title: string
  folderId: number | null
  tags: string[]
  messages: SessionMessage[]
  createdAt: string
  updatedAt: string
}

export interface SessionSummary {
  id: number
  agentId: number
  title: string
  folderId: number | null
  tags: string[]
  messageCount: number
  lastMessage: { role: string; content: string; timestamp: string } | null
  createdAt: string
  updatedAt: string
}

export interface SessionFolder {
  id: number
  name: string
  parentId: number | null
  orderNum: number
}

export interface SseTokenEvent {
  type: 'token'
  content: string
}

export interface SseDoneEvent {
  type: 'done'
  content: string
}

export interface SseErrorEvent {
  type: 'error'
  message: string
}

export type SseEvent = SseTokenEvent | SseDoneEvent | SseErrorEvent
