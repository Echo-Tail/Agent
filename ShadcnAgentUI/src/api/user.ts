import { api } from './request'
import type { UserDTO } from '@/types/api'

export function listUsersApi() {
  return api.get<UserDTO[]>('/users')
}

export function toggleUserStatusApi(id: number) {
  return api.post<UserDTO>(`/users/${id}/toggle`)
}

export function getUserApi(id: number) {
  return api.get<{ id: number; username: string }>(`/user/${id}`)
}

export function searchUsersApi(keyword: string) {
  return api.get<UserDTO[]>('/users/search', { params: { keyword } })
}
