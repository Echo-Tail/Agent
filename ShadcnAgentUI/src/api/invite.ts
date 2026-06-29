import { api } from './request'
import type { InviteCode } from '@/types/api'

export function listInviteCodesApi() {
  return api.get<InviteCode[]>('/invite-codes')
}

export function batchGenerateApi(count: number) {
  return api.post<InviteCode[]>('/invite-codes/batch', { count })
}

export function deleteInviteCodeApi(code: string) {
  return api.delete(`/invite-codes/${code}`)
}
