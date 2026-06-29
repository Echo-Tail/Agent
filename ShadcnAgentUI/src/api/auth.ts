import { api } from './request'
import type { LoginRequest, RegisterRequest, LoginResponse, UserDTO } from '@/types/api'

export function loginApi(req: LoginRequest) {
  return api.post<LoginResponse>('/login', req)
}

export function registerApi(req: RegisterRequest) {
  return api.post<UserDTO>('/register', req)
}

export function getCurrentUserApi() {
  return api.get<UserDTO>('/auth/me')
}
