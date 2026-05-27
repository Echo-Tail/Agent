<script setup lang="ts">
import { ref, watch, nextTick, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getConversationApi, sendPrivateMessageApi } from '@/api/group'
import { getUserApi } from '@/api/user'
import type { ChatPrivateMessage } from '@/types/group'
import { Button } from '@/components/ui/button'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Textarea } from '@/components/ui/textarea'
import { toast } from 'sonner'
import { Send, ArrowLeft, Loader2 } from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const otherUserId = computed(() => Number(route.params.userId))
const messages = ref<ChatPrivateMessage[]>([])
const loading = ref(true)
const inputText = ref('')
const sending = ref(false)

const otherUsername = ref('')
const myUsername = computed(() => auth.currentUser?.username || '我')
const messagesEnd = ref<HTMLDivElement | null>(null)

let pollTimer: ReturnType<typeof setInterval> | null = null

watch(() => messages.value.length, () => {
  nextTick(() => messagesEnd.value?.scrollIntoView({ behavior: 'smooth' }))
})

onMounted(async () => {
  try {
    const [userInfo, msgs] = await Promise.all([
      getUserApi(otherUserId.value),
      getConversationApi(otherUserId.value),
    ])
    otherUsername.value = userInfo.username
    messages.value = msgs.reverse()
  } catch {
    toast.error('加载失败')
  } finally {
    loading.value = false
  }

  // 轮询新消息
  pollTimer = setInterval(async () => {
    if (document.hidden) return
    try {
      const msgs = await getConversationApi(otherUserId.value)
      const reversed = msgs.reverse()
      if (reversed.length > messages.value.length) {
        messages.value = reversed
      }
    } catch { /* ignore */ }
  }, 3000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})

async function sendMessage() {
  const content = inputText.value.trim()
  if (!content || sending.value) return
  sending.value = true
  inputText.value = ''
  try {
    const msg = await sendPrivateMessageApi(otherUserId.value, content)
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
  router.push({ name: 'Messages' })
}
</script>

<template>
  <div class="flex flex-col h-[calc(100vh-8rem)] -m-6">
    <!-- Header -->
    <div class="flex items-center gap-3 px-4 h-14 border-b shrink-0">
      <Button variant="ghost" size="icon" class="h-8 w-8" @click="goBack">
        <ArrowLeft class="h-4 w-4" />
      </Button>
      <Avatar class="h-8 w-8">
        <AvatarFallback>{{ (otherUsername || 'U').charAt(0).toUpperCase() }}</AvatarFallback>
      </Avatar>
      <span class="font-semibold">{{ otherUsername || `用户 #${otherUserId}` }}</span>
    </div>

    <!-- Messages -->
    <div class="flex-1 overflow-y-auto p-4 space-y-4 bg-white dark:bg-[#121212]">
      <div v-if="loading" class="flex justify-center py-10">
        <Loader2 class="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
      <div v-else-if="messages.length === 0" class="flex flex-col items-center justify-center py-20 text-muted-foreground">
        <p>发送第一条消息开始聊天</p>
      </div>
      <template v-else>
        <div
          v-for="msg in messages"
          :key="msg.id"
          :class="['flex gap-3', msg.senderId === auth.currentUser?.id ? 'flex-row-reverse' : '']"
        >
          <div class="min-w-0">
            <div :class="['flex items-center gap-2 mb-1', msg.senderId === auth.currentUser?.id ? 'flex-row-reverse' : '']">
              <span class="text-xs text-[#888888]">{{ msg.senderId === auth.currentUser?.id ? myUsername : otherUsername }}</span>
              <span class="text-xs text-[#B2B2B2]">{{ new Date(msg.createdAt).toLocaleTimeString() }}</span>
            </div>
            <div :class="[
              'rounded-lg p-3 text-sm w-fit max-w-full',
              msg.senderId === auth.currentUser?.id
                ? 'bg-[#E9ECEF] text-[#191919] dark:bg-[#2d2d44] dark:text-gray-100'
                : 'bg-[#F0F2F5] text-[#191919] dark:bg-[#2d2d44] dark:text-gray-100'
            ]">
              <div class="whitespace-pre-wrap">{{ msg.content }}</div>
            </div>
          </div>
        </div>
        <div ref="messagesEnd" />
      </template>
    </div>

    <!-- Input -->
    <div class="px-4 py-3 border-t">
      <div class="flex gap-2">
        <Textarea
          v-model="inputText"
          :placeholder="'发送消息...'"
          class="min-h-[44px] max-h-[120px] resize-none"
          @keydown="handleKeydown"
        />
        <Button size="icon" class="h-[44px] w-[44px] shrink-0" :disabled="!inputText.trim() || sending" @click="sendMessage">
          <Send class="h-4 w-4" />
        </Button>
      </div>
    </div>
  </div>
</template>
