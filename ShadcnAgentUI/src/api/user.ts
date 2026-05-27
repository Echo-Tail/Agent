import http from './request'
import type { UserDTO } from '@/types/api'

export function listUsersApi() {
  return http.get<any, UserDTO[]>('/users')
}

export function toggleUserStatusApi(id: number) {
  return http.post<any, UserDTO>(`/users/${id}/toggle`)
}

export function getUserApi(id: number) {
  return http.get<any, { id: number; username: string }>(`/user/${id}`)
}

export function searchUsersApi(keyword: string) {
  return http.get<any, UserDTO[]>('/users/search', { params: { keyword } })
}
