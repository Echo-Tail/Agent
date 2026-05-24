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

export const ticketStatusKeys: Record<TicketStatus, string> = {
  PENDING: 'ticket.status.PENDING',
  IN_PROGRESS: 'ticket.status.IN_PROGRESS',
  COMPLETED: 'ticket.status.COMPLETED',
}

export const ticketPriorityKeys: Record<TicketPriority, string> = {
  LOW: 'ticket.priority.LOW',
  MEDIUM: 'ticket.priority.MEDIUM',
  HIGH: 'ticket.priority.HIGH',
}

export const affectedMenuKeys: Record<AffectedMenu, string> = {
  DASHBOARD: 'ticket.menu.DASHBOARD',
  CHAT: 'ticket.menu.CHAT',
  AGENT_LIST: 'ticket.menu.AGENT_LIST',
  HISTORY: 'ticket.menu.HISTORY',
  MY_TICKETS: 'ticket.menu.MY_TICKETS',
  KNOWLEDGE_BASE: 'ticket.menu.KNOWLEDGE_BASE',
  SETTINGS: 'ticket.menu.SETTINGS',
  TOKEN_USAGE: 'ticket.menu.TOKEN_USAGE',
  LOGS: 'ticket.menu.LOGS',
  USER_MANAGE: 'ticket.menu.USER_MANAGE',
  MODEL_MANAGE: 'ticket.menu.MODEL_MANAGE',
  TOOL_MANAGE: 'ticket.menu.TOOL_MANAGE',
  SKILL_MANAGE: 'ticket.menu.SKILL_MANAGE',
  OTHER: 'ticket.menu.OTHER',
}

export const ticketStatusOptions = Object.entries(ticketStatusKeys).map(([value, label]) => ({ value, label }))
export const ticketPriorityOptions = Object.entries(ticketPriorityKeys).map(([value, label]) => ({ value, label }))
export const allAffectedMenuOptions = Object.entries(affectedMenuKeys).map(([value, label]) => ({ value, label }))

export const submitterAffectedMenuOptions = [
  'DASHBOARD',
  'CHAT',
  'AGENT_LIST',
  'HISTORY',
  'MY_TICKETS',
  'KNOWLEDGE_BASE',
  'SETTINGS',
  'OTHER',
].map((value) => ({ value, label: affectedMenuKeys[value as AffectedMenu] }))
