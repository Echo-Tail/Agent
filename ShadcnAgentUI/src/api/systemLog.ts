import { api } from './request'

export interface SystemLogDTO {
  id: number
  level: string
  category: string
  message: string
  data: string | null
  duration: number | null
  route: string | null
  userId: number | null
  createdAt: string
}

export interface LogPageDTO {
  content: SystemLogDTO[]
  page: {
    size: number
    totalElements: number
    totalPages: number
    number: number
  }
}

export interface LogStatsDTO {
  total: number
  byLevel: Record<string, number>
  byCategory: Record<string, number>
  errorRate: number
  last24h: Array<{ hour: string; count: number }>
}

export interface SubmitLogRequest {
  level: string
  category: string
  message: string
  data?: string
  duration?: number
  route?: string
  userId?: number
}

export function submitLogApi(req: SubmitLogRequest) {
  return api.post<SystemLogDTO>('/system-logs', req)
}

export function queryLogsApi(params: {
  page?: number
  size?: number
  levels?: string[]
  categories?: string[]
  startDate?: string
  endDate?: string
  search?: string
}) {
  return api.get<LogPageDTO>('/system-logs', {
    params: {
      page: params.page ?? 0,
      size: params.size ?? 20,
      levels: params.levels?.length ? params.levels.join(',') : undefined,
      categories: params.categories?.length ? params.categories.join(',') : undefined,
      startDate: params.startDate || undefined,
      endDate: params.endDate || undefined,
      search: params.search || undefined,
    },
  })
}

export function getLogStatsApi() {
  return api.get<LogStatsDTO>('/system-logs/stats')
}

export function clearLogsApi() {
  return api.delete('/system-logs')
}
