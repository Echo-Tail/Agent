import { api } from './request'
import type { Agent, AgentCreateRequest, AgentUpdateRequest, ToolAvailability } from '@/types/agent'

export function listAgentsApi(scope?: string) {
  const params = scope ? { scope } : undefined
  return api.get<Agent[]>('/agents', { params })
}

export function listAgentsByScopeApi(scope: string) {
  return api.get<Agent[]>('/agents', { params: { scope } })
}

export function getAgentApi(id: number) {
  return api.get<Agent>(`/agents/${id}`)
}

export function createAgentApi(req: AgentCreateRequest) {
  return api.post<Agent>('/agents', req)
}

export function updateAgentApi(id: number, req: AgentUpdateRequest) {
  return api.put<Agent>(`/agents/${id}`, req)
}

export function deleteAgentApi(id: number) {
  return api.delete(`/agents/${id}`)
}

export function getSystemAgentApi() {
  return api.get<Agent>('/agents/system')
}

export function getWebSearchAvailabilityApi(id: number) {
  return api.get<ToolAvailability>(`/agents/${id}/web-search-availability`)
}

export function uploadAgentAvatarApi(id: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return api.post<string>(`/agents/${id}/avatar`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
