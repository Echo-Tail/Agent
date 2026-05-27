import http from './request'

import type { ChatGroup, GroupMember, GroupAgent, GroupMessage, GroupFile, UnifiedMember, ChatPrivateMessage, EmojiPack } from '@/types/group'

// ===== 群 CRUD =====

export function createGroupApi(name: string, avatar?: string) {
  return http.post<any, ChatGroup>('/groups', { name, avatar })
}

export function listMyGroupsApi() {
  return http.get<any, ChatGroup[]>('/groups')
}

export function getGroupApi(groupId: number) {
  return http.get<any, ChatGroup>(`/groups/${groupId}`)
}

export function updateGroupApi(groupId: number, data: { name?: string; avatar?: string }) {
  return http.put<any, ChatGroup>(`/groups/${groupId}`, data)
}

export function disbandGroupApi(groupId: number) {
  return http.delete<any, void>(`/groups/${groupId}`)
}

export function uploadGroupAvatarApi(groupId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<any, string>(`/groups/${groupId}/avatar`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// ===== 群成员 =====

export function inviteMemberApi(groupId: number, userId: number) {
  return http.post<any, void>(`/groups/${groupId}/members`, { userId })
}

export function kickMemberApi(groupId: number, targetUserId: number) {
  return http.delete<any, void>(`/groups/${groupId}/members/${targetUserId}`)
}

export function listMembersApi(groupId: number) {
  return http.get<any, GroupMember[]>(`/groups/${groupId}/members`)
}

// ===== 群 Agent =====

export function addGroupAgentApi(groupId: number, agentId: number) {
  return http.post<any, void>(`/groups/${groupId}/agents`, { agentId })
}

export function removeGroupAgentApi(groupId: number, agentId: number) {
  return http.delete<any, void>(`/groups/${groupId}/agents/${agentId}`)
}

export function listGroupAgentsApi(groupId: number) {
  return http.get<any, GroupAgent[]>(`/groups/${groupId}/agents`)
}

export function getUnifiedMembersApi(groupId: number) {
  return http.get<any, UnifiedMember[]>(`/groups/${groupId}/unified-members`)
}

export function getInvitableAgentsApi(groupId: number) {
  return http.get<any, Array<{ id: number; name: string; icon: string; avatar?: string }>>(`/groups/${groupId}/invitable-agents`)
}

// ===== 群消息 =====

export function sendGroupMessageApi(groupId: number, content: string) {
  return http.post<any, GroupMessage>(`/groups/${groupId}/messages`, { content })
}

export function listGroupMessagesApi(groupId: number, page = 0, size = 50) {
  return http.get<any, GroupMessage[]>(`/groups/${groupId}/messages`, { params: { page, size } })
}

// ===== 群文件 =====

export function uploadGroupFileApi(groupId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<any, GroupFile>(`/groups/${groupId}/files`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function listGroupFilesApi(groupId: number) {
  return http.get<any, GroupFile[]>(`/groups/${groupId}/files`)
}

export function getGroupFileDownloadUrl(fileId: number) {
  return `/v1/groups/${fileId}/files/${fileId}/download`
}

// ===== 用户私聊 =====

export function sendPrivateMessageApi(receiverId: number, content: string) {
  return http.post<any, ChatPrivateMessage>('/messages', { receiverId, content })
}

export function getConversationApi(otherUserId: number, page = 0, size = 50) {
  return http.get<any, ChatPrivateMessage[]>(`/messages/${otherUserId}`, { params: { page, size } })
}

export function getContactsApi() {
  return http.get<any, Array<{ userId: number; username: string }>>('/messages/contacts')
}

// ===== 表情包 =====

export function listEmojiPacksApi() {
  return http.get<any, EmojiPack[]>('/emoji/packs')
}
