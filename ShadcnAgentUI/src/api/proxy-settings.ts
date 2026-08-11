import { api } from './request'

export interface ProxySettings {
  enabled: boolean
  proxyUrl: string | null
  updatedBy: number | null
  updatedAt: string | null
}

export interface ProxyCandidate {
  proxyUrl: string
  source: string
  reachable: boolean
}

export interface ProxyDetection {
  detected: boolean
  suggestedProxyUrl: string | null
  candidates: ProxyCandidate[]
}

export interface ProxyTestResult {
  success: boolean
  message: string
  httpStatus: number | null
  durationMs: number
}

export function getProxySettingsApi() {
  return api.get<ProxySettings>('/admin/proxy-settings')
}

export function updateProxySettingsApi(enabled: boolean, proxyUrl: string | null) {
  return api.put<ProxySettings>('/admin/proxy-settings', { enabled, proxyUrl })
}

export function detectProxyApi() {
  return api.post<ProxyDetection>('/admin/proxy-settings/detect', undefined, {
    skipRetry: true,
  })
}

export function testProxyApi(proxyUrl: string | null) {
  return api.post<ProxyTestResult>('/admin/proxy-settings/test', { proxyUrl }, {
    skipRetry: true,
  })
}
