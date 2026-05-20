import http from './request'
import type { ApiResponse, LoginRequest, RegisterRequest, LoginResponse, UserDTO } from '../types/api'

export function loginApi(req: LoginRequest) {
  return http.post<ApiResponse<LoginResponse>>('/login', req)
}

export function registerApi(req: RegisterRequest) {
  return http.post<ApiResponse<UserDTO>>('/register', req)
}

export function getCurrentUserApi() {
  return http.get<ApiResponse<UserDTO>>('/auth/me')
}
