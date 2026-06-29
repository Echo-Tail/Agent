<script setup lang="ts">
/**
 * GroupChatView.vue — 群聊界面
 *
 * 群聊会话的主界面，支持多成员消息、@提及、文件共享、
 * SSE 实时消息推送、未读标记清除等功能。
 */
import { ref, watch, nextTick, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useUnreadStore } from '@/stores/unread'
import { STORAGE_KEY_TOKEN } from '@/constants'

import { getGroupApi, getUnifiedMembersApi, listGroupMessagesApi, sendGroupMessageApi, listGroupFilesApi, uploadGroupFileApi, kickMemberApi, markGroupReadApi } from '@/api/group'

import type { ChatGroup, UnifiedMember, GroupMessage, GroupFile } from '@/types/group'
import GroupFileDialog from '@/components/GroupFileDialog.vue'
import InviteMemberDialog from '@/components/InviteMemberDialog.vue'
import { Button } from '@/components/ui/button'

import { Badge } from '@/components/ui/badge'
import AgentIcon from '@/components/AgentIcon.vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import MentionInput from '@/components/MentionInput.vue'
import EmojiPicker from '@/components/EmojiPicker.vue'
import { toast } from 'sonner'
import { Send, ArrowLeft, Users, Bot, Loader2, FileIcon, UserPlus, Paperclip, Mail, UserX } from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const unread = useUnreadStore()

const groupId = computed(() => Number(route.params.id))
const group = ref<ChatGroup | null>(null)
const members = ref<UnifiedMember[]>([])
const messages = ref<GroupMessage[]>([])
const files = ref<GroupFile[]>([])
const loading = ref(true)
const inputText = ref('')
const showMembers = ref(false)
const showFileDialog = ref(false)
const showInviteDialog = ref(false)
const mentionRefreshKey = ref(0)
const sending = ref(false)
const fileUploading = ref(false)

const existingMemberIds = computed(() => members.value.filter(m => m.memberType === 'USER').map(m => m.refId))

const currentUserId = computed(() => auth.currentUser?.id)

// 成员名称映射表（按 memberType + refId 复合键区分）
const memberNames = computed(() => {
  const map = new Map<string, string>()
  for (const m of members.value) {
    map.set(`${m.memberType}-${m.refId}`, m.name)
  }
  return map
})

// 成员头像/图标映射表
const memberAvatars = computed(() => {
  const map = new Map<string, { avatar?: string; icon?: string; name: string }>()
  for (const m of members.value) {
    map.set(`${m.memberType}-${m.refId}`, { avatar: m.avatar, icon: m.icon, name: m.name })
  }
  return map
})

// 将 @[名称](type:id) 渲染为高亮标签，[text](url) 渲染为下载链接
function renderContent(text: string): string {
  // ☕ 极简商务风格：@提及高亮
  const mentionClass = ''
  const linkClass = 'text-[#495057] underline hover:no-underline'
  // 先处理下载链接 [text](url)
  let result = text.replace(
    /\[([^\]]+)\]\((https?:\/\/[^\s)]+|\/[^\s)]+)\)/g,
    `<a href="$2" target="_blank" class="${linkClass}" download>$1</a>`
  )
  // 再处理 @提及
  result = result.replace(
    /@\[([^\]]+)\]\((user|agent):(\d+)\)/g,
    `<span class="${mentionClass}">@$1</span>`
  )
  return result
}

function senderName(msg: GroupMessage): string {
  const key = `${msg.senderType}-${msg.senderId}`
  return memberNames.value.get(key) || (msg.senderType === 'USER' ? `用户#${msg.senderId}` : `Agent #${msg.senderId}`)
}

const isCreator = computed(() => group.value?.createdBy === auth.currentUser?.id)

function startPrivateChat(userId: number) {
  router.push({ name: 'MessagesDetail', params: { userId } })
}

async function kickUser(targetUserId: number) {
  try {
    await kickMemberApi(groupId.value, targetUserId)
    toast.success('已移除')
    members.value = await getUnifiedMembersApi(groupId.value)
  } catch {
    toast.error('移除失败')
  }
}

function isMyMessage(msg: GroupMessage) {
  return msg.senderType === 'USER' && msg.senderId === currentUserId.value
}

// 自动滚动到最新消息
const messagesEnd = ref<HTMLDivElement | null>(null)
watch(() => messages.value.length, () => {
  nextTick(() => {
    requestAnimationFrame(() => messagesEnd.value?.scrollIntoView({ behavior: 'smooth' }))
  })
})

// SSE 连接
let eventSource: EventSource | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | undefined

function connectSse() {
  if (eventSource) eventSource.close()
  const token = localStorage.getItem(STORAGE_KEY_TOKEN)
  // SSE 直连后端 8888 端口（Vite 代理不支持 SSE 长连接）
  const baseUrl = `${window.location.protocol}//${window.location.hostname}:8888`
  const url = token
    ? `${baseUrl}/v1/groups/${groupId.value}/sse?token=${encodeURIComponent(token)}`
    : `${baseUrl}/v1/groups/${groupId.value}/sse`
  eventSource = new EventSource(url)

  eventSource.addEventListener('message', (e) => {
    try {
      const msg: GroupMessage = JSON.parse(e.data)
      // 去重：避免自己发的消息重复添加（sendMessage 已本地追加）
      if (!messages.value.find(m => m.id === msg.id)) {
        messages.value.push(msg)
      }
    } catch { /* ignore parse errors */ }
  })

  eventSource.addEventListener('unread_group', (e) => {
    try {
      const data = JSON.parse(e.data)
      if (data.senderId !== auth.currentUser?.id) {
        unread.incrementGroup(data.groupId)
      }
    } catch { /* ignore */ }
  })

  eventSource.onerror = () => {
    // 断线后 3 秒重连
    reconnectTimer = window.setTimeout(() => connectSse(), 3000)
  }
}

onMounted(async () => {
  try {
    const [g, unified, msgs, fs] = await Promise.all([
      getGroupApi(groupId.value),
      getUnifiedMembersApi(groupId.value),
      listGroupMessagesApi(groupId.value),
      listGroupFilesApi(groupId.value),
    ])
    group.value = g
    members.value = unified
    messages.value = msgs.reverse() // 倒序变正序
    files.value = fs
    // 标记群聊为已读
    await markGroupReadApi(groupId.value)
    unread.clearGroup(groupId.value)
  } catch {
    toast.error('加载失败')
  } finally {
    loading.value = false
    nextTick(() => {
      requestAnimationFrame(() => messagesEnd.value?.scrollIntoView())
    })
  }
  connectSse()
})

onUnmounted(() => {
  eventSource?.close()
  if (reconnectTimer) clearTimeout(reconnectTimer)
})

async function handleFileUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files
  if (!files || files.length === 0) return
  fileUploading.value = true
  try {
    for (const file of Array.from(files)) {
      const gf = await uploadGroupFileApi(groupId.value, file)
      const link = `[${file.name}](/v1/groups/${groupId.value}/files/${gf.id}/download)`
      const msg = await sendGroupMessageApi(groupId.value, `上传了文件: ${link}`)
      // 本地追加消息，避免等 SSE（可能连不上）
      if (!messages.value.find(m => m.id === msg.id)) {
        messages.value.push(msg)
      }
    }
    toast.success(`已上传 ${files.length} 个文件`)
  } catch {
    toast.error('文件上传失败')
  } finally {
    fileUploading.value = false
    input.value = ''
  }
}

async function sendMessage() {
  const content = inputText.value.trim()
  if (!content || sending.value) return
  sending.value = true
  inputText.value = ''
  try {
    const msg = await sendGroupMessageApi(groupId.value, content)
    // 去重：SSE 可能已先于 REST 响应到达，避免重复添加
    if (!messages.value.find(m => m.id === msg.id)) {
      messages.value.push(msg)
    }
  } catch {
    toast.error('发送失败')
  } finally {
    sending.value = false
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

function goBack() {
  router.push({ name: 'GroupChat' })
}

const memberList = computed(() =>
  members.value.map(m => ({
    ...m,
    isCreator: group.value?.createdBy === m.refId && m.memberType === 'USER',
  }))
)

// 邀请成功后刷新
async function onInvited() {
  members.value = await getUnifiedMembersApi(groupId.value)
  mentionRefreshKey.value++
}
</script>

<template>
  <div class="flex h-[calc(100vh-7rem)] -m-6">
    <!-- Main chat area -->
    <div class="flex-1 flex flex-col">
      <!-- Header -->
      <div class="flex items-center gap-3 px-4 h-14 border-b shrink-0">
        <Button variant="ghost" size="icon" class="h-8 w-8" @click="goBack">
          <ArrowLeft class="h-4 w-4" />
        </Button>
        <div class="flex-1">
          <h2 class="font-semibold">{{ group?.name || '群聊' }}</h2>
          <p class="text-xs text-muted-foreground">{{ members.filter(m => m.memberType === 'USER').length }} 位成员 · {{ members.filter(m => m.memberType === 'AGENT').length }} 个 Agent</p>
        </div>
        <Button variant="outline" size="sm" @click="showInviteDialog = true">
          <UserPlus class="h-4 w-4 mr-1" /> 邀请
        </Button>
        <Button variant="outline" size="sm" @click="showFileDialog = true">
          <FileIcon class="h-4 w-4 mr-1" /> 文件
        </Button>
        <Button variant="outline" size="sm" @click="showMembers = !showMembers">
          <Users class="h-4 w-4 mr-1" /> 成员
        </Button>
      </div>

      <!-- Messages -->
      <div class="flex-1 overflow-y-auto p-4 space-y-4 bg-white dark:bg-[#121212]">
        <div v-if="loading" class="flex justify-center py-10">
          <Loader2 class="h-6 w-6 animate-spin text-muted-foreground" />
        </div>
        <div v-else-if="messages.length === 0" class="flex flex-col items-center justify-center py-20 text-muted-foreground">
          <Bot class="h-12 w-12 mb-3 opacity-30" />
          <p>群聊已创建，开始聊天吧</p>
          <p class="text-xs mt-1">使用 @Agent名称 可以艾特群里的 Agent</p>
        </div>
        <template v-else>
          <div v-for="msg in messages" :key="msg.id" :class="['flex gap-3', isMyMessage(msg) ? 'flex-row-reverse' : '']">
            <div class="h-8 w-8 mt-1 shrink-0 rounded-full overflow-hidden flex items-center justify-center"
                 :class="msg.senderType === 'AGENT' ? 'bg-primary/10' : 'bg-muted'">
              <img v-if="memberAvatars.get(`${msg.senderType}-${msg.senderId}`)?.avatar"
                   :src="memberAvatars.get(`${msg.senderType}-${msg.senderId}`)!.avatar!"
                   :alt="memberAvatars.get(`${msg.senderType}-${msg.senderId}`)?.name || 'avatar'"
                   class="h-full w-full object-cover" />
              <AgentIcon v-else-if="msg.senderType === 'AGENT'"
                         :icon="memberAvatars.get(`AGENT-${msg.senderId}`)?.icon"
                         class="h-5 w-5" />
              <span v-else class="text-xs font-medium text-muted-foreground">
                {{ memberAvatars.get(`USER-${msg.senderId}`)?.name?.charAt(0)?.toUpperCase() || '?' }}
              </span>
            </div>
            <div class="min-w-0">
              <div :class="['flex items-center gap-2 mb-1', isMyMessage(msg) ? 'flex-row-reverse' : '']">
                <span :class="['text-xs', isMyMessage(msg) ? 'font-medium' : 'text-[#888888]']">
                  {{ senderName(msg) }}
                </span>
                <span class="text-xs text-[#B2B2B2]">{{ new Date(msg.createdAt).toLocaleTimeString() }}</span>
              </div>
              <div :class="['rounded-lg p-3 text-sm w-fit max-w-full', isMyMessage(msg) ? 'bg-[#E9ECEF] text-[#191919] dark:bg-[#2d2d44] dark:text-gray-100' : 'bg-[#F0F2F5] text-[#191919] dark:bg-[#2d2d44] dark:text-gray-100']">
                <MarkdownRenderer v-if="msg.senderType === 'AGENT'" :content="msg.content" />
                <div v-else class="whitespace-pre-wrap" v-html="renderContent(msg.content)"></div>
              </div>
            </div>
          </div>
          <div ref="messagesEnd" />
        </template>
      </div>

      <!-- Input -->
      <div class="px-4 pt-3 pb-0 border-t">
        <div class="flex gap-2">
          <MentionInput
            :groupId="groupId"
            :model-value="inputText"
            :current-user-id="currentUserId"
            :refresh-key="mentionRefreshKey"
            @update:model-value="inputText = $event"
            @keydown="handleKeydown"
          />
          <EmojiPicker @select="(val: string) => { inputText += val.length <= 2 ? val : '![emoji](' + val + ') ' }" />
          <Button size="icon" variant="outline" class="h-[44px] w-[44px] shrink-0 relative" :disabled="fileUploading" :title="$t('common.upload')">
            <input id="group-file-upload" name="group-file-upload" ref="fileInputRef" type="file" multiple class="absolute inset-0 opacity-0 cursor-pointer" @change="handleFileUpload" />
            <Paperclip class="h-4 w-4" />
          </Button>
          <Button size="icon" class="h-[44px] w-[44px] shrink-0" :disabled="!inputText.trim() || sending" @click="sendMessage">
            <Send class="h-4 w-4" />
          </Button>
        </div>
      </div>
    </div>

    <!-- Members sidebar -->
    <div v-if="showMembers" class="w-72 border-l shrink-0 overflow-y-auto p-4 space-y-4">
      <div>
        <h3 class="font-semibold text-sm mb-2">成员 ({{ members.length }})</h3>
        <div v-for="m in memberList" :key="m.id" class="flex items-center gap-3 py-1.5">
          <div v-if="m.memberType === 'USER'" class="h-8 w-8 rounded-full overflow-hidden bg-muted flex items-center justify-center shrink-0">
            <img v-if="m.avatar" :src="m.avatar" :alt="m.name || 'avatar'" class="h-full w-full object-cover" />
            <span v-else class="text-xs font-medium">{{ m.name.charAt(0).toUpperCase() }}</span>
          </div>
          <div v-else class="h-8 w-8 rounded-full overflow-hidden bg-primary/10 flex items-center justify-center shrink-0">
            <AgentIcon :icon="m.icon" :avatar="m.avatar" class="h-5 w-5" />
          </div>
          <div class="flex items-center flex-1 min-w-0">
            <p class="text-sm truncate mr-2">{{ m.name }}</p>
          </div>
          <div class="flex gap-1 shrink-0">
            <Button v-if="m.memberType === 'USER' && m.refId !== currentUserId" variant="ghost" size="icon" class="h-7 w-7" title="私聊" @click.stop="startPrivateChat(m.refId)">
              <Mail class="h-3.5 w-3.5" />
            </Button>
            <Button v-if="m.memberType === 'USER' && isCreator && m.refId !== currentUserId" variant="ghost" size="icon" class="h-7 w-7 text-destructive hover:text-destructive" title="移出群" @click.stop="kickUser(m.refId)">
              <UserX class="h-3.5 w-3.5" />
            </Button>
          </div>
          <Badge v-if="m.role === 'CREATOR'" variant="secondary" class="text-xs shrink-0">创建者</Badge>
          <Badge v-else-if="m.memberType === 'AGENT'" variant="outline" class="text-xs bg-primary/5 shrink-0">Agent</Badge>
        </div>
      </div>
    </div>
  </div>

  <!-- 群文件 Dialog -->
  <GroupFileDialog
    :group-id="groupId"
    :open="showFileDialog"
    @update:open="showFileDialog = $event"
  />

  <!-- 邀请成员 Dialog -->
  <InviteMemberDialog
    :group-id="groupId"
    :open="showInviteDialog"
    :existing-member-ids="existingMemberIds"
    @update:open="showInviteDialog = $event"
    @invited="onInvited"
  />
</template>
