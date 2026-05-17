import http from './request'
import type { ApiResponse } from '../types/api'

export interface ToolDefinition {
  id: string
  name: string
  description: string
  category: string
}

export function listToolsApi() {
  return http.get<ApiResponse<ToolDefinition[]>>('/tools')
}
