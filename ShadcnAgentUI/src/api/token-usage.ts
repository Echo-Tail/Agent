import http from './request'

export interface TokenUsageSummary {
  agentName: string
  modelName: string
  modelType: string
  username: string
  callCount: number
  totalTokens: number
  promptTokens: number
  completionTokens: number
  cnyCost: number
}

export interface TokenUsageRecord {
  id: number
  modelId: number
  modelName: string
  modelType: string
  userId: number
  agentId: number
  agentName: string
  username: string
  promptTokens: number
  completionTokens: number
  totalTokens: number
  cnyCost: number
  success: boolean
  errorMessage: string | null
  createdAt: string
}

export function getTokenUsageSummaryApi(startDate: string, endDate: string) {
  return http.get<any, TokenUsageSummary[]>('/token-usage/summary', {
    params: { startDate, endDate },
  })
}

export function getImageModelCallsApi(startDate: string, endDate: string) {
  return http.get<any, number>('/token-usage/image-calls', {
    params: { startDate, endDate },
  })
}

export function getTokenUsageDetailApi(startDate: string, endDate: string) {
  return http.get<any, TokenUsageRecord[]>('/token-usage/detail', {
    params: { startDate, endDate },
  })
}
