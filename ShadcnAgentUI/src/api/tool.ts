import http from './request'

export interface ToolDefinition {
  id: string
  name: string
  description: string
  category: string
  enabled: boolean
  configJson: string
}

export function listToolsApi() {
  return http.get<any, ToolDefinition[]>('/tools')
}

export function updateToolApi(id: string, data: { name?: string; description?: string }) {
  return http.put<any, ToolDefinition>(`/tools/${id}`, data)
}

export function toggleToolApi(id: string) {
  return http.patch<any, ToolDefinition>(`/tools/${id}/toggle`)
}

export function saveToolConfigApi(id: string, configJson: string) {
  return http.put<any, ToolDefinition>(`/tools/${id}/config`, { configJson })
}
