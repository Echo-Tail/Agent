import http from './request'
import type { ApiResponse } from '../types/api'

export interface TokenUsageSummary {
  modelName: string
  modelType: string
  callCount: number
  totalTokens: number
  promptTokens: number
  completionTokens: number
}

export interface TokenUsageRecord {
  id: number
  modelId: number
  modelName: string
  modelType: string
  userId: number
  agentId: number
  promptTokens: number
  completionTokens: number
  totalTokens: number
  success: boolean
  errorMessage: string | null
  createdAt: string
}

export function getTokenUsageSummaryApi(startDate: string, endDate: string) {
  return http.get<ApiResponse<TokenUsageSummary[]>>('/token-usage/summary', {
    params: { startDate, endDate },
  })
}

export function getImageModelCallsApi(startDate: string, endDate: string) {
  return http.get<ApiResponse<number>>('/token-usage/image-calls', {
    params: { startDate, endDate },
  })
}

export function getTokenUsageDetailApi(startDate: string, endDate: string) {
  return http.get<ApiResponse<TokenUsageRecord[]>>('/token-usage/detail', {
    params: { startDate, endDate },
  })
}
