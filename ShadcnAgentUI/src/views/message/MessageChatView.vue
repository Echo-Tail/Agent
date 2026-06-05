<script setup lang="ts">
import { ref, watch, nextTick, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { STORAGE_KEY_TOKEN } from '@/constants'
import { getConversationApi, sendPrivateMessageApi, uploadGroupFileApi, markPrivateChatReadApi } from '@/api/group'
import { getUserApi } from '@/api/user'
import { useUnreadStore } from '@/stores/unread'
import type { ChatPrivateMessage } from '@/types/group'
import { Button } from '@/components/ui/button'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { toast } from 'sonner'
import { Send, ArrowLeft, Loader2, Paperclip, FileIcon } from 'lucide-vue-next'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import EmojiPicker from '@/components/EmojiPicker.vue'
import PrivateChatFileDialog from '@/components/PrivateChatFileDialog.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const unread = useUnreadStore()

const otherUserId = computed(() => Number(route.params.userId))
const messages = ref<ChatPrivateMessage[]>([])
const loading = ref(true)
const inputText = ref('')
const sending = ref(false)

const otherUsername = ref('')
const myUsername = computed(() => auth.currentUser?.username || '我')
const messagesEnd = ref<HTMLDivElement | null>(null)

const fileUploading = ref(false)
const showFileDialog = ref(false)

let pollTimer: ReturnType<typeof setInterval> | null = null
let eventSource: EventSource | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | undefined

watch(() => messages.value.length, () => {
  nextTick(() => {
    requestAnimationFrame(() => messagesEnd.value?.scrollIntoView({ behavior: 'smooth' }))
  })
})

onMounted(async () => {
  try {
    const [userInfo, msgs] = await Promise.all([
      getUserApi(otherUserId.value),
      getConversationApi(otherUserId.value),
    ])
    otherUsername.value = userInfo.username
    messages.value = msgs.reverse()
    // 标记私聊为已读
    await markPrivateChatReadApi(otherUserId.value)
    unread.clearPrivate(otherUserId.value)
  } catch {
    toast.error('加载失败')
  } finally {
    loading.value = false
    nextTick(() => {
      requestAnimationFrame(() => messagesEnd.value?.scrollIntoView())
    })
  }

  // SSE 连接
  const token = localStorage.getItem(STORAGE_KEY_TOKEN)
  const baseUrl = `${window.location.protocol}//${window.location.hostname}:8888`
  const sseUrl = token ? `${baseUrl}/v1/messages/sse?token=${encodeURIComponent(token)}` : `${baseUrl}/v1/messages/sse`
  eventSource = new EventSource(sseUrl)

  eventSource.addEventListener('message', (e) => {
    try {
      const msg: ChatPrivateMessage = JSON.parse(e.data)
      if ((msg.senderId === otherUserId.value || msg.receiverId === otherUserId.value) &&
          msg.senderId !== auth.currentUser?.id) {
        if (!messages.value.find(m => m.id === msg.id)) {
          messages.value.push(msg)
        }
      }
    } catch { /* ignore */ }
  })

  eventSource.addEventListener('unread_private', (e) => {
    try {
      const data = JSON.parse(e.data)
      if (data.userId !== auth.currentUser?.id) {
        unread.incrementPrivate(data.userId)
      }
    } catch { /* ignore */ }
  })

  eventSource.onerror = () => {
    reconnectTimer = window.setTimeout(() => {
      if (eventSource) eventSource.close()
      const newToken = localStorage.getItem(STORAGE_KEY_TOKEN)
      const newUrl = newToken ? `${baseUrl}/v1/messages/sse?token=${encodeURIComponent(newToken)}` : `${baseUrl}/v1/messages/sse`
      eventSource = new EventSource(newUrl)
    }, 3000)
  }
})

onUnmounted(() => {
  if (eventSource) eventSource.close()
  if (reconnectTimer) clearTimeout(reconnectTimer)
  if (pollTimer) clearInterval(pollTimer)
})

async function handleFileUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files
  if (!files || files.length === 0) return
  fileUploading.value = true
  try {
    for (const file of Array.from(files)) {
      const gf = await uploadGroupFileApi(otherUserId.value, file)
      const link = `[${file.name}](/v1/groups/${otherUserId.value}/files/${gf.id}/download)`
      const msg = await sendPrivateMessageApi(otherUserId.value, `上传了文件: ${link}`)
      messages.value.push(msg)
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

const textareaRef = ref<HTMLTextAreaElement | null>(null)

function autoResize() {
  const ta = textareaRef.value
  if (!ta) return
  ta.style.height = 'auto'
  ta.style.height = ta.scrollHeight + 'px'
}

watch(() => inputText.value, () => nextTick(autoResize))

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
      <Button variant="outline" size="sm" @click="showFileDialog = true">
        <FileIcon class="h-4 w-4 mr-1" /> 文件
      </Button>
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
          <div class="min-w-0 max-w-[75%]">
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
              <MarkdownRenderer v-if="msg.content.includes('[')" :content="msg.content" />
              <div v-else class="whitespace-pre-wrap">{{ msg.content }}</div>
            </div>
          </div>
        </div>
        <div ref="messagesEnd" />
      </template>
    </div>

    <!-- Input -->
    <div class="px-4 py-3 border-t">
      <div class="flex gap-2">
        <textarea
          id="private-chat-input"
          name="private-chat-input"
          ref="textareaRef"
          :value="inputText"
          @input="(e: Event) => { const ta = e.target as HTMLTextAreaElement; inputText = ta.value; autoResize() }"
          :placeholder="'发送消息...'"
          rows="1"
          class="flex min-h-[44px] w-full rounded-md border border-input bg-transparent px-2.5 py-2.5 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 resize-none overflow-hidden"
          @keydown="handleKeydown"
        />
        <EmojiPicker @select="(val: string) => { inputText += val.length <= 2 ? val : '![emoji](' + val + ') ' }" />
        <Button size="icon" variant="outline" class="h-[44px] w-[44px] shrink-0 relative" :disabled="fileUploading">
          <input id="private-chat-file-upload" name="private-chat-file-upload" type="file" multiple class="absolute inset-0 opacity-0 cursor-pointer" @change="handleFileUpload" />
          <Paperclip class="h-4 w-4" />
        </Button>
        <Button size="icon" class="h-[44px] w-[44px] shrink-0" :disabled="!inputText.trim() || sending" @click="sendMessage">
          <Send class="h-4 w-4" />
        </Button>
      </div>
    </div>
    <PrivateChatFileDialog
      context-type="PRIVATE"
      :context-id="otherUserId"
      :open="showFileDialog"
      @update:open="showFileDialog = $event"
    />
  </div>
</template>
