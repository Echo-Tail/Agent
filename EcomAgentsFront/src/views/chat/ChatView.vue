<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import type { DropdownOption } from 'naive-ui'
import { useChatStore } from '../../stores/chat'
import { getAgentApi, updateAgentApi } from '../../api/agent'
import { listModelsApi } from '../../api/model'
import { uploadFileApi } from '../../api/file'
import MessageBubble from '../../components/MessageBubble.vue'
import AgentSelector from '../../components/AgentSelector.vue'
import type { Agent } from '../../types/agent'
import type { AiModel, FileRecord } from '../../types/api'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const chat = useChatStore()

const showSelector = ref(false)
const messagesContainer = ref<HTMLElement | null>(null)
const currentAgentId = ref<number | null>(null)
const currentAgent = ref<Agent | null>(null)
let ignoreNextRouteChange = false
const currentModelName = ref('')
const models = ref<AiModel[]>([])
const attachedFile = ref<FileRecord | null>(null)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

async function handleFileSelect(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  uploading.value = true
  try {
    const res = await uploadFileApi(file)
    if (res.data.code === 200 && res.data.data) {
      attachedFile.value = res.data.data
    } else {
      message?.error(res.data.message || '文件上传失败')
    }
  } catch {
    message?.error('文件上传失败')
  } finally {
    uploading.value = false
    target.value = ''
  }
}

function removeAttachedFile() {
  attachedFile.value = null
}

function initAgentTools(_agent: Agent) {
  // tools are managed globally in ToolManage; no per-agent tool selection needed
}

async function resolveModelName(modelId: number): Promise<string> {
  if (models.value.length === 0) {
    const res = await listModelsApi()
    if (res.data.code === 200) {
      models.value = res.data.data ?? []
    }
  }
  return models.value.find((m) => m.id === modelId)?.name ?? ''
}

async function loadModels() {
  if (models.value.length === 0) {
    const res = await listModelsApi()
    if (res.data.code === 200) {
      models.value = res.data.data ?? []
    }
  }
}

const currentModelLabel = computed(() => {
  if (!currentAgent.value?.modelId) return ''
  const m = models.value.find((x) => x.id === currentAgent.value!.modelId)
  return m ? `${m.name} (${m.provider})` : ''
})

const modelMenuOptions = computed(() => {
  const grouped = new Map<string, DropdownOption[]>()
  for (const m of models.value) {
    if (!grouped.has(m.provider)) {
      grouped.set(m.provider, [])
    }
    grouped.get(m.provider)!.push({
      label: m.name,
      key: `model_${m.id}`,
    })
  }
  return Array.from(grouped.entries()).map(([provider, children]) => ({
    label: provider,
    key: `provider_${provider}`,
    type: 'submenu' as const,
    children,
  }))
})

async function handleModelSelect(key: string) {
  if (!key.startsWith('model_')) return
  const modelId = Number(key.slice(6))
  if (!currentAgentId.value || modelId === currentAgent.value?.modelId) return

  try {
    const res = await updateAgentApi(currentAgentId.value, { modelId })
    if (res.data.code === 200) {
      currentAgent.value = res.data.data
      currentModelName.value = modelId ? await resolveModelName(modelId) : ''
      message.success('模型已切换')
    } else {
      message.error(res.data.message || '模型切换失败')
    }
  } catch {
    message.error('模型切换失败')
  }
}

async function fetchAgent(agentId: number): Promise<Agent | null> {
  try {
    const res = await getAgentApi(agentId)
    if (res.data.code === 200 && res.data.data) {
      currentAgent.value = res.data.data
      currentModelName.value = res.data.data.modelId
        ? await resolveModelName(res.data.data.modelId)
        : ''
      initAgentTools(res.data.data)
      return res.data.data
    }
  } catch (e) {
    console.error('Failed to fetch agent:', agentId, e)
    message?.error('加载 Agent 失败')
  }
  return null
}

// Load model definitions once
loadModels()

const hasSession = computed(() => !!chat.activeSession)
const allMessages = computed(() => {
  const msgs = [...chat.messages]
  if (chat.isStreaming && chat.streamingText) {
    msgs.push({
      role: 'assistant' as const,
      content: chat.streamingText,
      timestamp: new Date().toISOString(),
    })
  }
  return msgs
})

async function scrollToBottom() {
  await nextTick()
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

watch(allMessages, scrollToBottom, { deep: true })
watch(() => chat.streamingText, scrollToBottom)

async function initChat() {
  currentAgent.value = null
  currentModelName.value = ''

  const agentIdParam = route.params.agentId as string | undefined
  const sessionIdParam = route.query.sessionId as string | undefined

  if (sessionIdParam) {
    await chat.loadSession(Number(sessionIdParam))
    if (chat.activeSession) {
      currentAgentId.value = chat.activeSession.agentId ?? null
      if (currentAgentId.value === null) return
      const agentData = await fetchAgent(currentAgentId.value)
      if (agentData?.greeting && chat.messages.length === 0) {
        chat.messages.push({
          role: 'assistant',
          content: agentData.greeting,
          timestamp: agentData.createdAt || new Date().toISOString(),
        })
        await nextTick()
        scrollToBottom()
      }
      return
    }
  }

  if (agentIdParam) {
    currentAgentId.value = Number(agentIdParam)
    await chat.fetchSessions({ agentId: currentAgentId.value })
    const existing = chat.sessions[0]
    if (existing) {
      await chat.loadSession(existing.id)
      const agentData = await fetchAgent(currentAgentId.value)
      if (agentData?.greeting && chat.messages.length === 0) {
        chat.messages.push({
          role: 'assistant',
          content: agentData.greeting,
          timestamp: agentData.createdAt || new Date().toISOString(),
        })
        await nextTick()
        scrollToBottom()
      }
    } else {
      try {
        const session = await chat.createSession(currentAgentId.value)
        router.replace({
          query: { sessionId: session.id.toString() },
        })
        const agentData = await fetchAgent(currentAgentId.value)
        if (agentData?.greeting) {
          chat.messages.push({
            role: 'assistant',
            content: agentData.greeting,
            timestamp: agentData.createdAt || new Date().toISOString(),
          })
          await nextTick()
          scrollToBottom()
        }
      } catch (e) {
        console.error('Failed to create session in initChat:', e)
        message?.error('创建会话失败')
      }
    }
    return
  }

  showSelector.value = true
}

onMounted(initChat)

watch(
  () => [route.params.agentId, route.query.sessionId] as const,
  () => {
    if (ignoreNextRouteChange) {
      ignoreNextRouteChange = false
      return
    }
    chat.clearActiveSession()
    initChat()
  },
)

async function handleAgentSelect(agent: Agent) {
  showSelector.value = false
  currentAgentId.value = agent.id
  currentAgent.value = agent
  currentModelName.value = agent.modelId
    ? await resolveModelName(agent.modelId)
    : ''
  initAgentTools(agent)
  try {
    const session = await chat.createSession(agent.id)
    router.replace({
      params: { agentId: agent.id.toString() },
      query: { sessionId: session.id.toString() },
    })
  } catch (e) {
    console.error('Failed to create session in handleAgentSelect:', e)
    message?.error('创建会话失败')
  }
}

async function handleSend() {
  const textContent = chat.inputText.trim()
  if ((!textContent && !attachedFile.value) || chat.isStreaming || !currentAgentId.value) return

  const fileRef = attachedFile.value
    ? `\n[attached file:${attachedFile.value.id}](${attachedFile.value.originalName})`
    : ''
  const content = textContent + fileRef
  attachedFile.value = null
  chat.inputText = ''

  if (!chat.activeSession) {
    try {
      const session = await chat.createSession(currentAgentId.value)
      ignoreNextRouteChange = true
      router.replace({
        query: { sessionId: session.id.toString() },
      })
      if (currentAgent.value?.greeting) {
        chat.messages.push({
          role: 'assistant',
          content: currentAgent.value.greeting,
          timestamp: currentAgent.value.createdAt || new Date().toISOString(),
        })
        await nextTick()
        scrollToBottom()
      }
    } catch (e) {
      console.error('Failed to create session in handleSend:', e)
      message?.error('创建会话失败，请重试')
      return
    }
  }

  try {
    await chat.sendMessage(currentAgentId.value, content)
  } catch (e) {
    message?.error(e instanceof Error ? e.message : '发送消息失败')
  }
}

function handleRetry(msg: any) {
  chat.retryMessage(msg)
  scrollToBottom()
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

function handleStop() {
  chat.stopStreaming()
}
</script>

<template>
  <div class="chat-view">
    <AgentSelector
      v-if="showSelector"
      @select="handleAgentSelect"
    />

    <div v-if="!hasSession && !showSelector && !chat.sessionLoading" class="chat-empty">
      <n-empty description="请选择一个 Agent 开始对话">
        <template #extra>
          <n-button type="primary" @click="showSelector = true">
            选择 Agent
          </n-button>
        </template>
      </n-empty>
    </div>

    <div v-if="chat.sessionLoading" class="chat-loading">
      <n-spin />
    </div>

    <template v-if="hasSession">
      <div v-if="currentAgent" class="chat-header">
        <div class="chat-header-info">
          <span class="chat-agent-name">{{ currentAgent.name }}</span>
          <n-dropdown
            v-if="currentModelLabel"
            trigger="click"
            :options="modelMenuOptions"
            @select="handleModelSelect"
          >
            <span class="chat-model-trigger">
              {{ currentModelLabel }}
              <n-icon size="12">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M7 10l5 5 5-5z"/>
                </svg>
              </n-icon>
            </span>
          </n-dropdown>
        </div>
      </div>

      <div ref="messagesContainer" class="messages-area">
        <div class="messages-inner">
          <MessageBubble
            v-for="(msg, i) in allMessages"
            :key="i"
            :msg="msg"
            @retry="handleRetry(msg)"
          />
          <div v-if="chat.isStreaming" class="streaming-cursor">
            <span class="cursor-dot">▍</span>
          </div>
        </div>
      </div>

      <div class="input-area">
        <div v-if="attachedFile" class="file-attachment">
          <span class="file-tag">
            <n-icon size="14">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                <path d="M16.5 6v11.5c0 2.21-1.79 4-4 4s-4-1.79-4-4V5c0-1.38 1.12-2.5 2.5-2.5s2.5 1.12 2.5 2.5v10.5c0 .55-.45 1-1 1s-1-.45-1-1V6H10v9.5c0 1.38 1.12 2.5 2.5 2.5s2.5-1.12 2.5-2.5V5c0-2.21-1.79-4-4-4S7 2.79 7 5v12.5c0 3.04 2.46 5.5 5.5 5.5s5.5-2.46 5.5-5.5V6h-1.5z"/>
              </svg>
            </n-icon>
            <span class="file-tag-name">{{ attachedFile.originalName }}</span>
            <span class="file-tag-size">({{ formatFileSize(attachedFile.fileSize) }})</span>
            <button class="file-tag-remove" @click="removeAttachedFile">&times;</button>
          </span>
        </div>

        <div class="input-bar">
          <input
            ref="fileInput"
            type="file"
            accept=".txt,.md,.pdf,.png,.jpg,.jpeg,.gif,.json,.csv,.xml"
            style="display: none"
            @change="handleFileSelect"
          />
          <n-button
            quaternary
            size="small"
            :loading="uploading"
            :disabled="chat.isStreaming"
            @click="fileInput?.click()"
            class="file-btn"
          >
            <template #icon>
              <n-icon size="20">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M16.5 6v11.5c0 2.21-1.79 4-4 4s-4-1.79-4-4V5c0-1.38 1.12-2.5 2.5-2.5s2.5 1.12 2.5 2.5v10.5c0 .55-.45 1-1 1s-1-.45-1-1V6H10v9.5c0 1.38 1.12 2.5 2.5 2.5s2.5-1.12 2.5-2.5V5c0-2.21-1.79-4-4-4S7 2.79 7 5v12.5c0 3.04 2.46 5.5 5.5 5.5s5.5-2.46 5.5-5.5V6h-1.5z"/>
                </svg>
              </n-icon>
            </template>
          </n-button>
          <n-input
            v-model:value="chat.inputText"
            type="textarea"
            :disabled="chat.isStreaming"
            :autosize="{ minRows: 1, maxRows: 4 }"
            placeholder="输入消息..."
            @keydown="handleKeydown"
            style="flex: 1;"
          />
          <div class="input-actions">
            <n-button
              v-if="chat.isStreaming"
              type="warning"
              @click="handleStop"
            >
              <template #icon>
                <n-icon>
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M6 6h12v12H6z"/>
                  </svg>
                </n-icon>
              </template>
              停止
            </n-button>
            <n-button
              v-else
              type="primary"
              @click="handleSend"
              :disabled="!chat.inputText.trim() && !attachedFile"
            >
              <template #icon>
                <n-icon>
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
                  </svg>
                </n-icon>
              </template>
              发送
            </n-button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.chat-view {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 140px);
  max-width: 900px;
  margin: 0 auto;
  width: 100%;
}

.chat-empty,
.chat-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 16px 0;
}

.messages-inner {
  display: flex;
  flex-direction: column;
}

.streaming-cursor {
  align-self: flex-start;
  margin-left: 46px;
  padding: 10px 14px;
}

.cursor-dot {
  animation: blink 1s step-end infinite;
  font-size: 18px;
  color: #C8815F;
}

@keyframes blink {
  50% { opacity: 0; }
}

.input-bar {
  display: flex;
  gap: 10px;
  padding: 4px 0 12px 0;
  align-items: flex-end;
}

.input-actions {
  display: flex;
  gap: 8px;
}

.input-area {
  border-top: 1px solid var(--border-color, #eee);
  padding-top: 8px;
}

.file-attachment {
  padding: 0 0 8px 0;
}

.file-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--tag-bg, #f0f0f0);
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 13px;
}

.file-tag-name {
  color: var(--text-color, #333);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-tag-size {
  color: #999;
}

.file-tag-remove {
  border: none;
  background: none;
  cursor: pointer;
  color: #999;
  font-size: 16px;
  line-height: 1;
  padding: 0 2px;
}

.file-tag-remove:hover {
  color: #e74c3c;
}

.file-btn {
  margin-bottom: 2px;
}


.chat-header {
  padding: 12px 0;
  border-bottom: 1px solid var(--border-color, #eee);
  margin-bottom: 8px;
}

.chat-header-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chat-agent-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-color, #333);
}

.chat-model-trigger {
  font-size: 12px;
  color: #999;
  background: var(--tag-bg, #f0f0f0);
  padding: 2px 10px;
  border-radius: 10px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  transition: background 0.15s;
}

.chat-model-trigger:hover {
  background: var(--hover-bg, #e8e8e8);
}
</style>
