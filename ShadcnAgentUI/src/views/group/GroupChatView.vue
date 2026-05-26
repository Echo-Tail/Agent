<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

import { getGroupApi, listMembersApi, listGroupAgentsApi, listGroupMessagesApi, sendGroupMessageApi, listGroupFilesApi } from '@/api/group'

import type { ChatGroup, GroupMember, GroupMessage, GroupFile } from '@/types/group'
import GroupFileDialog from '@/components/GroupFileDialog.vue'
import InviteMemberDialog from '@/components/InviteMemberDialog.vue'
import { Button } from '@/components/ui/button'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import MentionInput from '@/components/MentionInput.vue'
import EmojiPicker from '@/components/EmojiPicker.vue'
import { toast } from 'sonner'
import { Send, ArrowLeft, Users, Bot, Loader2, FileIcon, UserPlus } from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const groupId = computed(() => Number(route.params.id))
const group = ref<ChatGroup | null>(null)
const members = ref<GroupMember[]>([])
const agents = ref<Array<{ id: number; agentId: number }>>([])
const messages = ref<GroupMessage[]>([])
const files = ref<GroupFile[]>([])
const loading = ref(true)
const inputText = ref('')
const showMembers = ref(false)
const showFileDialog = ref(false)
const showInviteDialog = ref(false)
const sending = ref(false)

const existingMemberIds = computed(() => members.value.map(m => m.userId))

const currentUserId = computed(() => auth.currentUser?.id)

// userId → username 映射表（从成员列表构建）
const userNames = computed(() => {
  const map = new Map<number, string>()
  for (const m of members.value) {
    map.set(m.userId, m.username)
  }
  return map
})

function senderName(msg: GroupMessage): string {
  if (msg.senderType === 'USER') {
    return userNames.value.get(msg.senderId) || `用户#${msg.senderId}`
  }
  return `Agent #${msg.senderId}`
}

function isMyMessage(msg: GroupMessage) {
  return msg.senderType === 'USER' && msg.senderId === currentUserId.value
}

onMounted(async () => {
  try {
    const [g, m, ag, msgs, fs] = await Promise.all([
      getGroupApi(groupId.value),
      listMembersApi(groupId.value),
      listGroupAgentsApi(groupId.value),
      listGroupMessagesApi(groupId.value),
      listGroupFilesApi(groupId.value),
    ])
    group.value = g
    members.value = m
    agents.value = ag
    messages.value = msgs.reverse() // 倒序变正序
    files.value = fs
  } catch {
    toast.error('加载失败')
  } finally {
    loading.value = false
  }
})

async function sendMessage() {
  const content = inputText.value.trim()
  if (!content || sending.value) return
  sending.value = true
  inputText.value = ''
  try {
    const msg = await sendGroupMessageApi(groupId.value, content)
    messages.value.push(msg)
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
    isCreator: group.value?.createdBy === m.userId,
  }))
)

// 邀请成功后刷新
async function onInvited() {
  members.value = await listMembersApi(groupId.value)
}
</script>

<template>
  <div class="flex h-[calc(100vh-8rem)] -m-6">
    <!-- Main chat area -->
    <div class="flex-1 flex flex-col">
      <!-- Header -->
      <div class="flex items-center gap-3 px-4 h-14 border-b shrink-0">
        <Button variant="ghost" size="icon" class="h-8 w-8" @click="goBack">
          <ArrowLeft class="h-4 w-4" />
        </Button>
        <div class="flex-1">
          <h2 class="font-semibold">{{ group?.name || '群聊' }}</h2>
          <p class="text-xs text-muted-foreground">{{ members.length }} 位成员 · {{ agents.length }} 个 Agent</p>
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
      <div class="flex-1 overflow-y-auto p-4 space-y-4">
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
            <Avatar class="h-8 w-8 mt-1 shrink-0">
              <AvatarFallback :class="msg.senderType === 'AGENT' ? 'bg-primary text-primary-foreground text-xs' : 'bg-muted text-xs'">
                {{ msg.senderType === 'AGENT' ? 'A' : 'U' }}
              </AvatarFallback>
            </Avatar>
            <div class="min-w-0">
              <div :class="['flex items-center gap-2 mb-1', isMyMessage(msg) ? 'flex-row-reverse' : '']">
                <span class="text-xs font-medium">
                  {{ senderName(msg) }}
                </span>
                <span class="text-xs text-muted-foreground">{{ new Date(msg.createdAt).toLocaleTimeString() }}</span>
              </div>
              <div :class="['rounded-lg p-3 text-sm w-fit max-w-full', isMyMessage(msg) ? 'bg-primary text-primary-foreground' : 'bg-muted']">
                <MarkdownRenderer v-if="msg.senderType === 'AGENT'" :content="msg.content" />
                <div v-else class="whitespace-pre-wrap">{{ msg.content }}</div>
              </div>
            </div>
          </div>
        </template>
      </div>

      <!-- Input -->
      <div class="px-4 py-3 border-t">
        <div class="flex gap-2">
          <MentionInput
            :groupId="groupId"
            :model-value="inputText"
            @update:model-value="inputText = $event"
            @keydown="handleKeydown"
          />
          <EmojiPicker @select="(url: string) => { inputText += '![emoji](' + url + ') ' }" />
          <Button size="icon" class="h-[44px] w-[44px] shrink-0" :disabled="!inputText.trim() || sending" @click="sendMessage">
            <Send class="h-4 w-4" />
          </Button>
        </div>
      </div>
    </div>

    <!-- Members sidebar -->
    <div v-if="showMembers" class="w-72 border-l shrink-0 overflow-y-auto p-4 space-y-4">
      <!-- 成员列表 -->
      <div>
        <h3 class="font-semibold text-sm mb-2">成员 ({{ members.length }})</h3>
        <div v-for="m in memberList" :key="m.id" class="flex items-center gap-3 py-1.5">
          <Avatar class="h-8 w-8">
            <AvatarFallback class="text-xs">{{ m.username.charAt(0).toUpperCase() }}</AvatarFallback>
          </Avatar>
          <div class="flex-1 min-w-0">
            <p class="text-sm truncate">{{ m.username }}</p>
            <Badge v-if="m.isCreator" variant="secondary" class="text-xs">创建者</Badge>
            <Badge v-else variant="outline" class="text-xs">成员</Badge>
          </div>
        </div>
      </div>

      <hr />

      <!-- Agent 列表 -->
      <div>
        <h3 class="font-semibold text-sm mb-2">Agent ({{ agents.length }})</h3>
        <div v-for="ag in agents" :key="ag.id" class="flex items-center gap-3 py-1.5">
          <Avatar class="h-8 w-8">
            <AvatarFallback class="bg-primary text-primary-foreground text-xs">A</AvatarFallback>
          </Avatar>
          <div>
            <p class="text-sm">Agent #{{ ag.agentId }}</p>
          </div>
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
