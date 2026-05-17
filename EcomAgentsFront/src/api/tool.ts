import http from './request'
import type { ApiResponse } from '../types/api'

export interface ToolDefinition {
  id: string
  name: string
  description: string
  category: string
  enabled: boolean
  configJson: string
}

export function listToolsApi() {
  return http.get<ApiResponse<ToolDefinition[]>>('/tools')
}

export function updateToolApi(id: string, data: { name?: string; description?: string }) {
  return http.put<ApiResponse<ToolDefinition>>(`/tools/${id}`, data)
}

export function toggleToolApi(id: string) {
  return http.patch<ApiResponse<ToolDefinition>>(`/tools/${id}/toggle`)
}

export function saveToolConfigApi(id: string, configJson: string) {
  return http.put<ApiResponse<ToolDefinition>>(`/tools/${id}/config`, { configJson })
}
