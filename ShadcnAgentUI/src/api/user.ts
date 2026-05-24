import http from './request'
import type { UserDTO } from '@/types/api'

export function listUsersApi() {
  return http.get<any, UserDTO[]>('/users')
}

export function toggleUserStatusApi(id: number) {
  return http.post<any, UserDTO>(`/users/${id}/toggle`)
}
