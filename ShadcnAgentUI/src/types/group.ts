export interface ChatGroup {
  id: number
  name: string
  avatar?: string
  createdBy: number
  createdAt: string
  updatedAt: string
}

export interface GroupMember {
  id: number
  groupId: number
  userId: number
  /** 用户名（后端从 User 表关联填充） */
  username: string
  role: 'CREATOR' | 'MEMBER'
  joinedAt: string
}

export interface GroupAgent {
  id: number
  groupId: number
  agentId: number
  addedBy: number
  addedAt: string
}

export interface UnifiedMember {
  id: number
  memberType: 'USER' | 'AGENT'
  refId: number
  name: string
  avatar?: string
  icon?: string
  role: 'CREATOR' | 'MEMBER'
}

export interface GroupMessage {
  id: number
  groupId: number
  senderId: number
  senderType: 'USER' | 'AGENT'
  content: string
  replyToMsgId?: number
  createdAt: string
}

export interface GroupFile {
  id: number
  groupId: number
  uploaderId: number
  originalName: string
  fileSize: number
  mimeType: string
  uploadedAt: string
}

export interface ChatPrivateMessage {
  id: number
  senderId: number
  receiverId: number
  content: string
  fileId?: number
  createdAt: string
}

export interface EmojiPack {
  id: number
  name: string
  imageUrl: string
  category?: string
}
