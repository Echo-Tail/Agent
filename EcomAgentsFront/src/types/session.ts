export interface SessionMessage {
  role: 'user' | 'assistant'
  content: string
  timestamp: string
  isError?: boolean
  /** 流中断前已产生的部分内容（仅当 isError 且有部分响应时设置） */
  partialContent?: string
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

export interface SseReasoningEvent {
  type: 'reasoning'
  content: string
}

export interface SseToolCallEvent {
  type: 'tool_call'
  tool: string
  status: string
}

export interface SseToolResultEvent {
  type: 'tool_result'
  tool: string
  status: string
  summary?: string
}

export type SseEvent = SseTokenEvent | SseDoneEvent | SseErrorEvent | SseReasoningEvent | SseToolCallEvent | SseToolResultEvent
