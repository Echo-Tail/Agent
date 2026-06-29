import { api } from './request'
import type { Ticket, TicketChangeRecord, TicketFilters, TicketRequest } from '@/types/ticket'

function cleanParams(filters: TicketFilters = {}) {
  return Object.fromEntries(
    Object.entries(filters).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  )
}

export function listMyTicketsApi(filters?: TicketFilters) {
  return api.get<Ticket[]>('/tickets/my', { params: cleanParams(filters) })
}

export function listAdminTicketsApi(filters?: TicketFilters) {
  return api.get<Ticket[]>('/admin/tickets', { params: cleanParams(filters) })
}

export function getMyTicketApi(id: number) {
  return api.get<Ticket>(`/tickets/my/${id}`)
}

export function getAdminTicketApi(id: number) {
  return api.get<Ticket>(`/admin/tickets/${id}`)
}

export function createTicketApi(req: TicketRequest) {
  return api.post<Ticket>('/tickets', req)
}

export function updateTicketApi(id: number, req: TicketRequest) {
  return api.put<Ticket>(`/tickets/${id}`, req)
}

export function startTicketApi(id: number) {
  return api.post<Ticket>(`/admin/tickets/${id}/start`)
}

export function completeTicketApi(id: number, handlingNote: string) {
  return api.post<Ticket>(`/admin/tickets/${id}/complete`, { handlingNote })
}

export function listTicketChangesApi(id: number) {
  return api.get<TicketChangeRecord[]>(`/tickets/${id}/changes`)
}
