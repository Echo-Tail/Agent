import { api } from './request'

export interface ProviderRuntimeMetrics {
  provider: string
  completed: number
  failed: number
  successRate: number
  errors: number
  timeouts: number
  averageRequestDurationMs: number
  p95RequestDurationMs: number
}

export interface RuntimeFailure {
  id: number
  modelId: number
  provider: string
  capability: string
  errorCode: string | null
  message: string | null
  retryable: boolean
  completedAt: string | null
}

export interface ImageRuntimeMonitoring {
  generatedAt: string
  jobsByStatus: Record<string, number>
  workerActive: number
  completed: number
  successRate: number
  failureRate: number
  timeouts: number
  retries: number
  recovered: number
  averageJobDurationMs: number
  p95JobDurationMs: number
  providers: ProviderRuntimeMetrics[]
  recentFailures: RuntimeFailure[]
}

export function getImageRuntimeMonitoringApi() {
  return api.get<ImageRuntimeMonitoring>('/admin/image-runtime', { silent: true })
}
