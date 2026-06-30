import { api } from './request'

import type { ChatGroup, GroupMember, GroupAgent, GroupMessage, GroupFile, UnifiedMember, ChatPrivateMessage, EmojiPack } from '@/types/group'

// ===== 群 CRUD =====

export function createGroupApi(name: string, avatar?: string) {
  return api.post<ChatGroup>('/groups', { name, avatar })
}

export function listMyGroupsApi() {
  return api.get<ChatGroup[]>('/groups')
}

export function getGroupApi(groupId: number) {
  return api.get<ChatGroup>(`/groups/${groupId}`)
}

export function updateGroupApi(groupId: number, data: { name?: string; avatar?: string }) {
  return api.put<ChatGroup>(`/groups/${groupId}`, data)
}

export function disbandGroupApi(groupId: number) {
  return api.delete(`/groups/${groupId}`)
}

export function markPrivateChatReadApi(otherUserId: number) {
  return api.put<void>(`/messages/${otherUserId}/read`)
}

export function markGroupReadApi(groupId: number) {
  return api.put<void>(`/groups/${groupId}/read`)
}

export async function downloadGroupFileApi(groupId: number, fileId: number): Promise<Blob> {
  const token = localStorage.getItem('ecomagents_token')
  const response = await fetch(`/v1/groups/${groupId}/files/${fileId}/download`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!response.ok) throw new Error('下载失败')
  return response.blob()
}

export function uploadGroupAvatarApi(groupId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return api.post<string>(`/groups/${groupId}/avatar`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// ===== 群成员 =====

export function inviteMemberApi(groupId: number, userId: number) {
  return api.post<void>(`/groups/${groupId}/members`, { userId })
}

export function kickMemberApi(groupId: number, targetUserId: number) {
  return api.delete(`/groups/${groupId}/members/${targetUserId}`)
}

export function listMembersApi(groupId: number) {
  return api.get<GroupMember[]>(`/groups/${groupId}/members`)
}

// ===== 群 Agent =====

export function addGroupAgentApi(groupId: number, agentId: number) {
  return api.post<void>(`/groups/${groupId}/agents`, { agentId })
}

export function removeGroupAgentApi(groupId: number, agentId: number) {
  return api.delete(`/groups/${groupId}/agents/${agentId}`)
}

export function listGroupAgentsApi(groupId: number) {
  return api.get<GroupAgent[]>(`/groups/${groupId}/agents`)
}

export function getUnifiedMembersApi(groupId: number) {
  return api.get<UnifiedMember[]>(`/groups/${groupId}/unified-members`)
}

export function getInvitableAgentsApi(groupId: number) {
  return api.get<Array<{ id: number; name: string; icon: string; avatar?: string }>>(`/groups/${groupId}/invitable-agents`)
}

// ===== 群消息 =====

export function sendGroupMessageApi(groupId: number, content: string) {
  return api.post<GroupMessage>(`/groups/${groupId}/messages`, { content })
}

export function listGroupMessagesApi(groupId: number, page = 0, size = 50) {
  return api.get<GroupMessage[]>(`/groups/${groupId}/messages`, { params: { page, size } })
}

// ===== 群文件 =====

export function uploadGroupFileApi(groupId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return api.post<GroupFile>(`/groups/${groupId}/files`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function listGroupFilesApi(groupId: number) {
  return api.get<GroupFile[]>(`/groups/${groupId}/files`)
}

export function getGroupFileDownloadUrl(fileId: number) {
  return `/v1/groups/${fileId}/files/${fileId}/download`
}

// ===== 用户私聊 =====

export function sendPrivateMessageApi(receiverId: number, content: string) {
  return api.post<ChatPrivateMessage>('/messages', { receiverId, content })
}

export function getConversationApi(otherUserId: number, page = 0, size = 50) {
  return api.get<ChatPrivateMessage[]>(`/messages/${otherUserId}`, { params: { page, size } })
}

export function getContactsApi() {
  return api.get<Array<{ userId: number; username: string }>>('/messages/contacts')
}

// ===== 表情包 =====

export function listEmojiPacksApi() {
  return api.get<EmojiPack[]>('/emoji/packs')
}
