import http from './request'
import type { InviteCode } from '@/types/api'

export function listInviteCodesApi() {
  return http.get<any, InviteCode[]>('/invite-codes')
}

export function batchGenerateApi(count: number) {
  return http.post<any, InviteCode[]>('/invite-codes/batch', { count })
}

export function deleteInviteCodeApi(code: string) {
  return http.delete<any, void>(`/invite-codes/${code}`)
}
