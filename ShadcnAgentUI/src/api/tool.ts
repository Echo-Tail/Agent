import { api } from './request'

export interface ToolDefinition {
  id: string
  name: string
  description: string
  category: string
  enabled: boolean
  configJson: string
}

export function listToolsApi() {
  return api.get<ToolDefinition[]>('/tools')
}

export function updateToolApi(id: string, data: { name?: string; description?: string }) {
  return api.put<ToolDefinition>(`/tools/${id}`, data)
}

export function toggleToolApi(id: string) {
  return api.patch<ToolDefinition>(`/tools/${id}/toggle`)
}

export function saveToolConfigApi(id: string, configJson: string) {
  return api.put<ToolDefinition>(`/tools/${id}/config`, { configJson })
}
