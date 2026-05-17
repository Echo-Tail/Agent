export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  email?: string
  inviteCode: string
}

export interface LoginResponse {
  user: UserDTO
  token: string
}

export interface UserDTO {
  id: number
  username: string
  email: string
  role: string
  status: string
  inviteCode?: string
  createdAt: string
}

export interface UserStats {
  total: number
  active: number
  disabled: number
  admin: number
}

export interface InviteCode {
  code: string
  used: boolean
  usedBy: string | null
  usedByUserId: number | null
  createdAt: string
}

export interface FileRecord {
  id: number
  originalName: string
  fileSize: number
  mimeType: string
  uploadedAt: string
  url: string
  storedPath: string
}

export interface AiModel {
  id: number
  name: string
  provider: string
  modelName: string
  apiUrl: string
  apiKey?: string
  apiType: string
  apiVersion: string
  maxTokens: number
  temperature: number
  isDefault: boolean
  enabled: boolean
  createdAt: string
  createdBy: number
}
