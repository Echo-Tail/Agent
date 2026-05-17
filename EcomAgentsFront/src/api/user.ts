import http from './request'
import type { ApiResponse } from '../types/api'
import type { UserDTO } from '../types/api'

export function listUsersApi() {
  return http.get<ApiResponse<UserDTO[]>>('/users')
}

export function toggleUserStatusApi(id: number) {
  return http.post<ApiResponse<UserDTO>>(`/users/${id}/toggle`)
}
