import http from './request'
import type { Agent, AgentCreateRequest, AgentUpdateRequest } from '@/types/agent'

export function listAgentsApi(scope?: string) {
  const params = scope ? { scope } : undefined
  return http.get<any, Agent[]>('/agents', { params })
}

export function listAgentsByScopeApi(scope: string) {
  return http.get<any, Agent[]>('/agents', { params: { scope } })
}

export function getAgentApi(id: number) {
  return http.get<any, Agent>(`/agents/${id}`)
}

export function createAgentApi(req: AgentCreateRequest) {
  return http.post<any, Agent>('/agents', req)
}

export function updateAgentApi(id: number, req: AgentUpdateRequest) {
  return http.put<any, Agent>(`/agents/${id}`, req)
}

export function deleteAgentApi(id: number) {
  return http.delete<any, void>(`/agents/${id}`)
}

export function getSystemAgentApi() {
  return http.get<any, Agent>('/agents/system')
}
