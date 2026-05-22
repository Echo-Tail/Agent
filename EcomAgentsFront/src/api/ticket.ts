import http from './request'
import type { ApiResponse } from '../types/api'
import type { Ticket, TicketChangeRecord, TicketFilters, TicketRequest } from '../types/ticket'

function cleanParams(filters: TicketFilters = {}) {
  return Object.fromEntries(
    Object.entries(filters).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  )
}

export function listMyTicketsApi(filters?: TicketFilters) {
  return http.get<ApiResponse<Ticket[]>>('/tickets/my', { params: cleanParams(filters) })
}

export function listAdminTicketsApi(filters?: TicketFilters) {
  return http.get<ApiResponse<Ticket[]>>('/admin/tickets', { params: cleanParams(filters) })
}

export function getMyTicketApi(id: number) {
  return http.get<ApiResponse<Ticket>>(`/tickets/my/${id}`)
}

export function getAdminTicketApi(id: number) {
  return http.get<ApiResponse<Ticket>>(`/admin/tickets/${id}`)
}

export function createTicketApi(req: TicketRequest) {
  return http.post<ApiResponse<Ticket>>('/tickets', req)
}

export function updateTicketApi(id: number, req: TicketRequest) {
  return http.put<ApiResponse<Ticket>>(`/tickets/${id}`, req)
}

export function startTicketApi(id: number) {
  return http.post<ApiResponse<Ticket>>(`/admin/tickets/${id}/start`)
}

export function completeTicketApi(id: number, handlingNote: string) {
  return http.post<ApiResponse<Ticket>>(`/admin/tickets/${id}/complete`, { handlingNote })
}

export function listTicketChangesApi(id: number) {
  return http.get<ApiResponse<TicketChangeRecord[]>>(`/tickets/${id}/changes`)
}
