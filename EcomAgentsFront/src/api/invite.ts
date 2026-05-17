import http from './request'
import type { ApiResponse, InviteCode } from '../types/api'

export function listInviteCodesApi() {
  return http.get<ApiResponse<InviteCode[]>>('/invite-codes')
}

export function batchGenerateApi(count: number, prefix?: string) {
  return http.post<ApiResponse<InviteCode[]>>('/invite-codes/batch', { count, prefix })
}

export function deleteInviteCodeApi(code: string) {
  return http.delete<ApiResponse<void>>(`/invite-codes/${code}`)
}
