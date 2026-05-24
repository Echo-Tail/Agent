import http from './request'
import type { LoginRequest, RegisterRequest, LoginResponse, UserDTO } from '@/types/api'

export function loginApi(req: LoginRequest) {
  return http.post<any, LoginResponse>('/login', req)
}

export function registerApi(req: RegisterRequest) {
  return http.post<any, UserDTO>('/register', req)
}

export function getCurrentUserApi() {
  return http.get<any, UserDTO>('/auth/me')
}
