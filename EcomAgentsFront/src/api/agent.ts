import http from './request'
import type { ApiResponse } from '../types/api'
import type { Agent, AgentCreateRequest, AgentUpdateRequest } from '../types/agent'

export function listAgentsApi(scope?: string) {
  const params = scope ? { scope } : undefined
  return http.get<ApiResponse<Agent[]>>('/agents', { params })
}

export function listAgentsByScopeApi(scope: string) {
  return http.get<ApiResponse<Agent[]>>('/agents', { params: { scope } })
}

export function getAgentApi(id: number) {
  return http.get<ApiResponse<Agent>>(`/agents/${id}`)
}

export function createAgentApi(req: AgentCreateRequest) {
  return http.post<ApiResponse<Agent>>('/agents', req)
}

export function updateAgentApi(id: number, req: AgentUpdateRequest) {
  return http.put<ApiResponse<Agent>>(`/agents/${id}`, req)
}

export function deleteAgentApi(id: number) {
  return http.delete<ApiResponse<Agent>>(`/agents/${id}`)
}

export function getSystemAgentApi() {
  return http.get<ApiResponse<Agent>>('/agents/system')
}
