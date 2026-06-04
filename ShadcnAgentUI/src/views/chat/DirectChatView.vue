<script setup lang="ts">
/**
 * DirectChatView.vue — AI Agent 私聊界面
 *
 * 与 AI Agent 进行对话的主界面，支持文件上传、消息发送、
 * 会话管理、Agent 切换等功能。使用 streamChat 流式响应。
 */
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { STORAGE_KEY_TOKEN } from '@/constants'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useChatStore } from '@/stores/chat'
import { useAgentStore } from '@/stores/agent'
import { getAgentApi, getWebSearchAvailabilityApi } from '@/api/agent'
import { getModelApi } from '@/api/model'
import { listToolsApi } from '@/api/tool'
import type { Agent, ToolAvailability } from '@/types/agent'
import type { AiModel } from '@/types/api'
import type { ToolDefinition } from '@/api/tool'
import { Button } from '@/components/ui/button'

import { Badge } from '@/components/ui/badge'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Skeleton } from '@/components/ui/skeleton'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import EmojiPicker from '@/components/EmojiPicker.vue'
import {
  Send,
  Square,
  Bot,
  RefreshCw,
  Wrench,
  Paperclip,
  X,
  File as FileIcon,
  Download,
  Copy,
  Check,
  AlertCircle,
} from 'lucide-vue-next'
import { toast } from 'sonner'
import { uploadFileApi } from '@/api/file'

const { t } = useI18n()

const route = useRoute()
const chatStore = useChatStore()
const agentStore = useAgentStore()

const inputText = ref('')
const messagesEnd = ref<HTMLDivElement | null>(null)
const initializing = ref(true)
const msgCopiedIdx = ref<number | null>(null)

interface FileAttachment {
  file: File
  record?: { id: number; originalName: string; fileSize: number; mimeType: string }
  error?: string
}

const attachments = ref<FileAttachment[]>([])
const fileInput = ref<HTMLInputElement | null>(null)
const uploadError = ref<string | null>(null)

const MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function getUniqueFileName(originalName: string, existingNames: string[]): string {
  const dotIndex = originalName.lastIndexOf('.')
  const baseName = dotIndex > 0 ? originalName.substring(0, dotIndex) : originalName
  const ext = dotIndex > 0 ? originalName.substring(dotIndex) : ''
  let counter = 1
  let newName = originalName
  while (existingNames.includes(newName)) {
    newName = `${baseName}(${counter})${ext}`
    counter++
  }
  return newName
}

function handleFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  if (files.length === 0) return

  uploadError.value = null
  const existingNames = attachments.value.map(a => a.file.name)

  for (const file of files) {
    if (file.size > MAX_FILE_SIZE) {
      uploadError.value = t('chat.fileTooLarge', { size: '10MB' })
      continue
    }
    const uniqueName = getUniqueFileName(file.name, existingNames)
    const renamedFile = new File([file], uniqueName, { type: file.type })
    existingNames.push(uniqueName)
    const attachment: FileAttachment = { file: renamedFile }
    attachments.value.push(attachment)
    uploadFile(attachment)
  }

  input.value = ''
}

async function uploadFile(attachment: FileAttachment) {
  try {
    const record = await uploadFileApi(attachment.file)
    attachment.record = record
  } catch {
    attachment.error = t('error.uploadFailed')
    toast.error(t('error.uploadFailed') + ': ' + attachment.file.name)
  }
}

function removeFile(index: number) {
  attachments.value.splice(index, 1)
  if (attachments.value.length === 0) {
    uploadError.value = null
  }
}

const selectedAgent = ref<Agent | null>(null)
const agentModel = ref<AiModel | null>(null)
const agentToolDefinitions = ref<ToolDefinition[]>([])
const webSearchAvailability = ref<ToolAvailability | null>(null)

function parseQueryNumber(value: unknown): number | null {
  const raw = Array.isArray(value) ? value[0] : value
  const parsed = Number(raw)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

async function resolveAgent(agentId: number): Promise<Agent | null> {
  const localAgent = agentStore.myAgents.find(a => a.id === agentId)
  if (localAgent) return localAgent
  try {
    return await getAgentApi(agentId)
  } catch {
    return null
  }
}

async function loadWebSearchAvailability(agentId: number) {
  try {
    webSearchAvailability.value = await getWebSearchAvailabilityApi(agentId)
  } catch {
    webSearchAvailability.value = null
  }
}

async function resolveModel(modelId: number): Promise<AiModel | null> {
  try {
    return await getModelApi(modelId)
  } catch {
    return null
  }
}

async function resolveToolNames(toolIds: string[]): Promise<ToolDefinition[]> {
  if (!toolIds.length) return []
  try {
    const allTools = await listToolsApi()
    return allTools.filter(t => toolIds.includes(t.id))
  } catch {
    return []
  }
}

async function setSelectedAgent(agent: Agent | null) {
  selectedAgent.value = agent
  agentModel.value = null
  agentToolDefinitions.value = []
  webSearchAvailability.value = null
  if (agent) {
    const [model, tools, _] = await Promise.all([
      resolveModel(agent.modelId),
      resolveToolNames(agent.tools),
      loadWebSearchAvailability(agent.id),
    ])
    agentModel.value = model
    agentToolDefinitions.value = tools
  }
}

onMounted(async () => {
  const routeAgentId = parseQueryNumber(route.query.agentId)
  const sessionId = parseQueryNumber(route.query.sessionId)

  try {
    // Load agents if not loaded
    if (agentStore.myAgents.length === 0) {
      await agentStore.fetchMyAgents()
    }
    if (agentStore.plazaAgents.length === 0) {
      await agentStore.fetchPlazaAgents()
    }

    if (sessionId) {
      try {
        await chatStore.loadSession(sessionId)
        const agentId = chatStore.activeSession?.agentId ?? routeAgentId
        if (agentId) {
          await setSelectedAgent(await resolveAgent(agentId))
          chatStore.switchToAgent(agentId, { preserveSession: true })
        }
      } catch {
        toast.error(t('error.loadSessionFailed'))
        chatStore.clearActiveSession()
      }
      return
    }

    if (routeAgentId) {
      await setSelectedAgent(await resolveAgent(routeAgentId))
      if (selectedAgent.value) {
        chatStore.switchToAgent(routeAgentId)
        // Create a session if none exists
        try {
          await chatStore.createSession(routeAgentId)
        } catch {
          toast.error(t('error.createSessionFailed'))
        }
      }
    } else {
      // Default to system agent when no agentId specified
      try {
        const sysAgent = await agentStore.fetchSystemAgent()
        if (sysAgent) {
          await setSelectedAgent(sysAgent)
          chatStore.switchToAgent(sysAgent.id)
          await chatStore.createSession(sysAgent.id)
        } else {
          chatStore.switchToDirect()
        }
      } catch {
        chatStore.switchToDirect()
      }
    }
  } finally {
    initializing.value = false
    nextTick(() => {
      requestAnimationFrame(() => messagesEnd.value?.scrollIntoView())
    })
  }
})

watch(() => chatStore.messages, () => {
  nextTick(() => {
    requestAnimationFrame(() => messagesEnd.value?.scrollIntoView({ behavior: 'smooth' }))
  })
}, { flush: 'post' })

async function copyMessage(idx: number, content: string) {
  try {
    await navigator.clipboard.writeText(content)
    msgCopiedIdx.value = idx
    setTimeout(() => { msgCopiedIdx.value = null }, 1500)
  } catch { /* ignore */ }
}

async function downloadFile(file: { id: number; name: string; url: string; size: number }) {
  try {
    const token = localStorage.getItem(STORAGE_KEY_TOKEN)
    const response = await fetch(file.url, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!response.ok) {
      toast.error(t('error.downloadFailed'))
      return
    }
    const blob = await response.blob()
    const blobUrl = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = blobUrl
    a.download = file.name
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(blobUrl)
  } catch {
    toast.error(t('error.downloadFailed'))
  }
}

async function handleSend() {
  const content = inputText.value.trim()
  if (!content || chatStore.isStreaming) return

  if (!selectedAgent.value) {
    toast.error(t('agent.modelRequired'))
    return
  }

  inputText.value = ''

  if (!chatStore.activeSession) {
    try {
      await chatStore.createSession(selectedAgent.value.id)
    } catch {
      toast.error(t('error.createSessionFailed'))
      return
    }
  }

  await chatStore.sendMessage(selectedAgent.value.id, content)
}

function handleStop() {
  chatStore.stopStreaming()
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
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

async function selectAgent(agentId: number) {
  const agent = agentStore.myAgents.find(a => a.id === agentId)
    ?? agentStore.plazaAgents.find(a => a.id === agentId)
    ?? await resolveAgent(agentId)
  if (agent) {
    await setSelectedAgent(agent)
    chatStore.switchToAgent(agentId)
    chatStore.createSession(agentId).catch(() => toast.error(t('error.createSessionFailed')))
  }
}

function newSession() {
  if (selectedAgent.value) {
    chatStore.createSession(selectedAgent.value.id).catch(() => toast.error(t('error.createSessionFailed')))
  }
}

function toolBadgeVariant(status: string) {
  if (status === 'timeout') return 'destructive'
  if (status === 'degraded' || status === 'empty') return 'outline'
  return 'secondary'
}

const availableAgents = computed(() => {
  const currentId = selectedAgent.value?.id
  const all = [...agentStore.myAgents, ...agentStore.plazaAgents]
  const seen = new Set<number>()
  return all.filter(a => {
    if (a.id === currentId || seen.has(a.id)) return false
    seen.add(a.id)
    return true
  })
})
</script>

<template>
  <div class="flex flex-col h-[calc(100vh-8rem)] max-w-6xl mx-auto">
    <!-- Toolbar -->
    <div class="flex items-center justify-between mb-4">
      <div class="flex items-center gap-2">
        <span v-if="selectedAgent" class="font-medium">{{ selectedAgent.name }}</span>
        <span v-else class="text-muted-foreground">{{ $t('chat.directChat') }}</span>
        <!-- Model name -->
        <Badge
          v-if="selectedAgent && agentModel"
          variant="outline"
          class="text-xs font-normal"
        >
          {{ agentModel.name }}
        </Badge>
        <!-- Agent tools -->
        <template v-if="selectedAgent && agentToolDefinitions.length">
          <template v-for="tool in agentToolDefinitions" :key="tool.id">
            <span class="text-xs text-muted-foreground">·</span>
            <span class="text-xs text-muted-foreground">{{ tool.name }}</span>
          </template>
        </template>
        <!-- Web search availability -->
        <Badge
          v-if="selectedAgent && webSearchAvailability"
          :variant="webSearchAvailability.available ? 'secondary' : 'outline'"
          class="text-xs"
          :title="webSearchAvailability.message"
        >
          <Wrench class="mr-1 h-3 w-3" />
          {{ webSearchAvailability.available ? $t('chat.webSearchReady') : $t('chat.webSearchUnavailable') }}
        </Badge>
      </div>
      <div class="flex gap-2">
        <Button v-if="selectedAgent" variant="outline" size="sm" @click="newSession">
          <RefreshCw class="mr-1 h-3 w-3" /> {{ $t('chat.newChat') }}
        </Button>
      </div>
    </div>

    <div
      v-if="selectedAgent && webSearchAvailability && !webSearchAvailability.available"
      class="mb-3 flex items-start gap-2 rounded-md border border-border bg-muted/40 px-3 py-2 text-xs text-muted-foreground"
    >
      <AlertCircle class="mt-0.5 h-3.5 w-3.5 shrink-0" />
      <span>{{ webSearchAvailability.message }}</span>
    </div>

    <!-- Messages -->
    <div class="flex-1 overflow-y-auto rounded-lg bg-muted/30 p-4 space-y-4">
        <!-- Loading state -->
        <div v-if="initializing" class="space-y-4">
          <div class="flex gap-3"><Skeleton class="h-8 w-8 rounded-full" /><Skeleton class="h-16 flex-1 rounded-lg" /></div>
        </div>

        <!-- Empty state -->
        <div v-else-if="chatStore.messages.length === 0 && !chatStore.isStreaming" class="flex-1 flex items-center justify-center">
          <div class="text-center">
            <Bot class="mx-auto h-12 w-12 text-muted-foreground/50" />
            <h3 class="mt-4 text-lg font-semibold">{{ selectedAgent ? selectedAgent.name : $t('chat.startChat') }}</h3>
            <p class="text-sm text-muted-foreground mt-1">{{ selectedAgent?.greeting || $t('chat.startChat') }}</p>
          </div>
        </div>

        <!-- Messages -->
        <template v-else>
          <div v-for="(msg, idx) in chatStore.messages" :key="idx" :class="['flex gap-3', msg.role === 'user' ? 'justify-end' : '']">
            <Avatar v-if="msg.role === 'assistant'" class="h-8 w-8 mt-1">
              <AvatarFallback class="bg-primary text-primary-foreground text-xs">
                {{ selectedAgent?.name?.charAt(0) || 'A' }}
              </AvatarFallback>
            </Avatar>
            <div :class="['group relative rounded-lg p-3 text-sm', msg.role === 'user' ? 'bg-primary text-primary-foreground' : 'bg-muted', msg.isError ? 'bg-destructive/10 text-destructive' : '']">
              <MarkdownRenderer v-if="msg.role === 'assistant' && !msg.isError" :content="msg.content" />
              <div v-if="msg.file" class="mt-2 flex items-center gap-2 border-t pt-2">
                <FileIcon class="h-4 w-4 text-muted-foreground" />
                <span class="text-xs text-muted-foreground truncate max-w-[200px]">{{ msg.file.name }}</span>
                <button
                  class="inline-flex items-center gap-1 text-xs text-primary hover:underline"
                  @click="downloadFile(msg.file)"
                >
                  <Download class="h-3.5 w-3.5" />
                  {{ $t('chat.downloadFile') }}
                </button>
              </div>
              <div v-if="msg.role !== 'assistant' || msg.isError" class="whitespace-pre-wrap">{{ msg.content }}</div>
              <button
                v-if="msg.role === 'assistant' && !msg.isError"
                class="absolute bottom-2 right-2 flex h-6 w-6 items-center justify-center rounded opacity-0 transition-opacity hover:bg-black/10 group-hover:opacity-100"
                :class="msgCopiedIdx === idx ? 'text-green-500 opacity-100' : 'text-muted-foreground'"
                :title="msgCopiedIdx === idx ? '已复制' : '复制消息'"
                @click="copyMessage(idx, msg.content)"
              >
                <Check v-if="msgCopiedIdx === idx" class="h-3.5 w-3.5" />
                <Copy v-else class="h-3.5 w-3.5" />
              </button>
              <div v-if="msg.isError && msg.partialContent" class="mt-2 text-xs opacity-70 border-t pt-1">
                {{ $t('chat.errorPartial') }}: {{ msg.partialContent }}
              </div>
              <div v-if="msg.isError && !chatStore.isStreaming" class="mt-2">
                <Button variant="ghost" size="sm" class="h-6 text-xs" @click="chatStore.retryMessage(msg)">
                  <RefreshCw class="mr-1 h-3 w-3" /> {{ $t('chat.errorRetry') }}
                </Button>
              </div>
            </div>
            <Avatar v-if="msg.role === 'user'" class="h-8 w-8 mt-1">
              <AvatarFallback class="bg-muted text-xs">U</AvatarFallback>
            </Avatar>
          </div>

          <!-- Error message (not yet pushed to messages) -->
          <div v-if="chatStore.isStreaming" class="flex gap-3">
            <Avatar class="h-8 w-8 mt-1">
              <AvatarFallback class="bg-primary text-primary-foreground text-xs">
                {{ selectedAgent?.name?.charAt(0) || 'A' }}
              </AvatarFallback>
            </Avatar>
            <div class="bg-muted rounded-lg p-3 text-sm">
              <!-- Tool calls -->
              <div v-if="chatStore.currentToolStatuses.length" class="flex flex-wrap gap-2 mb-2">
                <Badge
                  v-for="tool in chatStore.currentToolStatuses"
                  :key="tool.tool"
                  :variant="toolBadgeVariant(tool.status)"
                  class="text-xs"
                  :title="tool.message"
                >
                  <Wrench class="mr-1 h-3 w-3" /> {{ tool.message }}
                </Badge>
              </div>
              <MarkdownRenderer :content="chatStore.streamingText" />
              <span class="inline-block w-2 h-4 bg-primary animate-pulse ml-0.5" />
            </div>
          </div>

          <div ref="messagesEnd" />
        </template>
    </div>

    <!-- Input -->
    <div class="mt-4">
      <!-- File preview chips -->
      <div v-if="attachments.length > 0" class="flex gap-2 mb-2 overflow-x-auto pb-1">
        <div
          v-for="(att, idx) in attachments"
          :key="att.file.name + idx"
          class="flex items-center gap-1.5 px-2.5 py-1.5 bg-muted rounded-md text-xs whitespace-nowrap shrink-0"
        >
          <FileIcon class="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
          <span class="max-w-[120px] truncate">{{ att.file.name }}</span>
          <span class="text-muted-foreground shrink-0">{{ formatFileSize(att.file.size) }}</span>
          <span v-if="!att.record && !att.error" class="text-muted-foreground shrink-0">{{ $t('chat.uploading') }}</span>
          <Button
            variant="ghost"
            size="icon"
            class="h-4 w-4 shrink-0"
            @click="removeFile(idx)"
          >
            <X class="h-3 w-3" />
          </Button>
        </div>
      </div>
      <div v-if="uploadError && attachments.length === 0" class="mb-2 text-xs text-destructive">{{ uploadError }}</div>
      <div class="flex gap-2">
        <Button
          variant="outline"
          size="icon"
          class="h-[44px] w-[44px] shrink-0"
          :disabled="chatStore.isStreaming || !selectedAgent"
          :title="$t('chat.attachFile')"
          @click="fileInput?.click()"
        >
          <Paperclip class="h-4 w-4" />
        </Button>
          <input
            id="chat-file-upload"
            name="chat-file-upload"
            ref="fileInput"
          type="file"
          multiple
          accept=".txt,.md,.pdf,.doc,.docx,.xls,.xlsx,.csv,.json,.xml,.png,.jpg,.jpeg,.gif,.bmp,.webp,.svg,.zip"
          class="hidden"
          @change="handleFileSelect"
        />
        <EmojiPicker @select="(val: string) => { inputText += val.length <= 2 ? val : '![emoji](' + val + ') ' }" />
        <textarea
          id="chat-input"
          name="chat-input"
          ref="textareaRef"
          :value="inputText"
          @input="(e: Event) => { const ta = e.target as HTMLTextAreaElement; inputText = ta.value; autoResize() }"
          :placeholder="$t('chat.inputPlaceholder')"
          :disabled="chatStore.isStreaming || !selectedAgent"
          rows="1"
          class="flex min-h-[44px] w-full rounded-md border border-input bg-transparent px-2.5 py-2.5 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 resize-none overflow-hidden"
          @keydown="handleKeydown"
        />
      <div class="flex flex-col gap-1">
        <Button
          v-if="!chatStore.isStreaming"
          size="icon"
          class="h-[44px] w-[44px]"
          :disabled="!inputText.trim() || !selectedAgent"
          @click="handleSend"
        >
          <Send class="h-4 w-4" />
        </Button>
        <Button
          v-else
          variant="destructive"
          size="icon"
          class="h-[44px] w-[44px]"
          @click="handleStop"
        >
          <Square class="h-4 w-4" />
        </Button>
      </div>
    </div>
    </div>

    <!-- Agent switching bar -->
    <div v-if="!initializing && availableAgents.length > 0" class="mt-3 pt-3 border-t">
      <div class="flex flex-wrap gap-1.5">
        <Badge
          v-for="agent in availableAgents"
          :key="agent.id"
          variant="outline"
          class="cursor-pointer hover:bg-accent text-xs py-1 px-2.5 select-none"
          @click="selectAgent(agent.id)"
        >
          {{ agent.name }}
        </Badge>
      </div>
    </div>
  </div>
</template>
