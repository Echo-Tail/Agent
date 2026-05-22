import type { FileRecord } from './api'

export type TicketStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED'
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH'
export type AffectedMenu =
  | 'DASHBOARD'
  | 'CHAT'
  | 'AGENT_LIST'
  | 'HISTORY'
  | 'MY_TICKETS'
  | 'KNOWLEDGE_BASE'
  | 'SETTINGS'
  | 'TOKEN_USAGE'
  | 'LOGS'
  | 'USER_MANAGE'
  | 'MODEL_MANAGE'
  | 'TOOL_MANAGE'
  | 'SKILL_MANAGE'
  | 'OTHER'

export interface Ticket {
  id: number
  ticketNumber: string
  title: string
  affectedMenu: AffectedMenu
  priority: TicketPriority
  content: string
  status: TicketStatus
  submitterId: number
  submitterName?: string
  handlerId?: number
  handlerName?: string
  handlingNote?: string
  createdAt: string
  updatedAt: string
  startedAt?: string
  completedAt?: string
  attachments: FileRecord[]
}

export interface TicketChangeRecord {
  id: number
  fieldName: string
  oldValue?: string
  newValue?: string
  changedBy: number
  changedByName?: string
  changedAt: string
}

export interface TicketRequest {
  title: string
  affectedMenu: AffectedMenu
  priority: TicketPriority
  content: string
  attachmentIds: number[]
}

export interface TicketFilters {
  status?: TicketStatus | null
  affectedMenu?: AffectedMenu | null
  priority?: TicketPriority | null
  title?: string
  submitterId?: number | null
}

export const ticketStatusLabels: Record<TicketStatus, string> = {
  PENDING: '待处理',
  IN_PROGRESS: '处理中',
  COMPLETED: '已完成',
}

export const ticketPriorityLabels: Record<TicketPriority, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
}

export const affectedMenuLabels: Record<AffectedMenu, string> = {
  DASHBOARD: '工作台',
  CHAT: '对话',
  AGENT_LIST: '我的Agent',
  HISTORY: '历史会话',
  MY_TICKETS: '我的工单',
  KNOWLEDGE_BASE: '知识库',
  SETTINGS: '设置',
  TOKEN_USAGE: 'Token用量',
  LOGS: '日志',
  USER_MANAGE: '用户管理',
  MODEL_MANAGE: '模型管理',
  TOOL_MANAGE: '工具管理',
  SKILL_MANAGE: '技能管理',
  OTHER: '其他',
}

export const ticketStatusOptions = Object.entries(ticketStatusLabels).map(([value, label]) => ({ value, label }))
export const ticketPriorityOptions = Object.entries(ticketPriorityLabels).map(([value, label]) => ({ value, label }))

export const allAffectedMenuOptions = Object.entries(affectedMenuLabels).map(([value, label]) => ({ value, label }))

export const submitterAffectedMenuOptions = [
  'DASHBOARD',
  'CHAT',
  'AGENT_LIST',
  'HISTORY',
  'MY_TICKETS',
  'KNOWLEDGE_BASE',
  'SETTINGS',
  'OTHER',
].map((value) => ({ value, label: affectedMenuLabels[value as AffectedMenu] }))

export const affectedMenuOptions = submitterAffectedMenuOptions
