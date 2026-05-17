<script setup lang="ts">
import { ref, computed, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useChatStore } from '../../stores/chat'
import { useAgentStore } from '../../stores/agent'
import { getSystemAgentApi, getAgentApi, updateAgentApi } from '../../api/agent'
import { uploadFileApi } from '../../api/file'
import { listModelsApi } from '../../api/model'
import MessageBubble from '../../components/MessageBubble.vue'
import AgentCard from '../../components/AgentCard.vue'
import type { Agent } from '../../types/agent'
import type { FileRecord, AiModel } from '../../types/api'
import type { DropdownOption } from 'naive-ui'

const route = useRoute()
const message = useMessage()
const chat = useChatStore()
const agentStore = useAgentStore()

/* ====== System agent loading ====== */
const systemAgent = ref<Agent | null>(null)
const systemAgentLoading = ref(true)
const systemAgentError = ref(false)

async function loadSystemAgent() {
  systemAgentLoading.value = true
  systemAgentError.value = false
  try {
    const res = await getSystemAgentApi()
    if (res.data.code === 200 && res.data.data) {
      systemAgent.value = res.data.data
    } else {
      systemAgentError.value = true
    }
  } catch {
    systemAgentError.value = true
  } finally {
    systemAgentLoading.value = false
  }
}

/* ====== Model display and switching ====== */
const models = ref<AiModel[]>([])

async function loadModels() {
  if (models.value.length > 0) return
  try {
    const res = await listModelsApi()
    if (res.data.code === 200) {
      models.value = res.data.data ?? []
    }
  } catch { /* ignore */ }
}

const currentModelLabel = computed(() => {
  if (!systemAgent.value?.modelId) return ''
  const m = models.value.find((x) => x.id === systemAgent.value!.modelId)
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
  if (!systemAgent.value || modelId === systemAgent.value.modelId) return
  try {
    const res = await updateAgentApi(systemAgent.value.id, { modelId })
    if (res.data.code === 200) {
      systemAgent.value = res.data.data
      message.success('模型已切换')
    } else {
      message.error(res.data.message || '模型切换失败')
    }
  } catch {
    message.error('模型切换失败')
  }
}

/* ====== File upload ====== */
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

/* ====== Chat state ====== */
const messagesContainer = ref<HTMLElement | null>(null)

const isDirectMode = computed(() => chat.chatMode === 'direct')
const hasSession = computed(() => !!chat.activeSession)

const allMessages = computed(() => {
  const msgs = [...chat.messages]
  if (chat.isStreaming && chat.streamingText) {
    msgs.push({ role: 'assistant' as const, content: chat.streamingText, timestamp: new Date().toISOString() })
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

/* ====== Session management ====== */
async function startDirectSession() {
  if (!systemAgent.value) return
  try {
    await chat.createSession(systemAgent.value.id, '新对话')
  } catch (e) {
    console.error('Failed to create direct session:', e)
    message?.error('创建会话失败')
  }
}

async function startAgentSession(agent: Agent) {
  chat.switchToAgent(agent.id)
  try {
    const session = await chat.createSession(agent.id, '新对话')
    if (session && agent.greeting) {
      chat.messages.push({
        role: 'assistant',
        content: agent.greeting,
        timestamp: new Date().toISOString(),
      })
    }
  } catch (e) {
    console.error('Failed to create agent session:', e)
    message?.error('创建会话失败')
  }
}

function handleNewSession() {
  chat.clearActiveSession()
  if (isDirectMode.value) {
    startDirectSession()
  }
}

function handleSwitchToDirect() {
  chat.switchToDirect()
  if (!chat.activeSession && systemAgent.value) {
    startDirectSession()
  }
}

/* ====== Agent click ====== */
function handleAgentClick(agent: Agent) {
  chat.clearActiveSession()
  startAgentSession(agent)
}

/* ====== Send message ====== */
async function handleSend() {
  const textContent = chat.inputText.trim()
  if ((!textContent && !attachedFile.value) || chat.isStreaming) return
  if (!chat.activeSession) {
    if (isDirectMode.value && systemAgent.value) {
      await startDirectSession()
    } else {
      return
    }
  }

  const fileRef = attachedFile.value
    ? `\n[attached file:${attachedFile.value.id}](${attachedFile.value.originalName})`
    : ''
  const content = textContent + fileRef
  attachedFile.value = null
  chat.inputText = ''

  try {
    if (isDirectMode.value && systemAgent.value) {
      await chat.sendMessage(systemAgent.value.id, content)
    } else if (chat.activeSession) {
      const agentId = chat.activeSession.agentId
      await chat.sendMessage(agentId, content)
    }
  } catch (e) {
    message?.error(e instanceof Error ? e.message : '发送消息失败')
  }
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

/* ====== Init ====== */
async function init() {
  loadModels()
  agentStore.fetchAgents()
  await loadSystemAgent()

  // Handle query param: ?sessionId=xxx (restore existing session)
  const sessionIdParam = route.query.sessionId as string | undefined
  if (sessionIdParam) {
    try {
      const { getSessionApi } = await import('../../api/session')
      const res = await getSessionApi(Number(sessionIdParam))
      if (res.data.code === 200 && res.data.data) {
        chat.activeSession = res.data.data
        chat.messages = res.data.data.messages ?? []
        // Determine mode from session's agent
        chat.chatMode = systemAgent.value && res.data.data.agentId === systemAgent.value.id
          ? 'direct' : 'agent'
        return
      }
    } catch (e) {
      console.error('Failed to restore session:', e)
    }
  }

  // Handle query param: ?agentId=xxx (navigated from AgentCard)
  const agentIdParam = route.query.agentId as string | undefined
  if (agentIdParam) {
    try {
      const res = await getAgentApi(Number(agentIdParam))
      if (res.data.code === 200 && res.data.data) {
        handleAgentClick(res.data.data)
        return
      }
    } catch (e) {
      console.error('Failed to load agent from query param:', e)
    }
  }

  // Default: auto-start a direct session
  if (systemAgent.value) {
    await startDirectSession()
  }
}

init()
</script>

<template>
  <div class="direct-chat-view">
    <!-- Breadcrumb bar -->
    <div class="breadcrumb-bar">
      <div class="breadcrumb-left">
        <span v-if="isDirectMode" class="breadcrumb-item active">
          💬 默认对话
        </span>
        <n-dropdown
          v-if="currentModelLabel && isDirectMode"
          trigger="click"
          :options="modelMenuOptions"
          @select="handleModelSelect"
        >
          <span class="model-pill">
            {{ currentModelLabel }}
            <n-icon size="12">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                <path d="M7 10l5 5 5-5z"/>
              </svg>
            </n-icon>
          </span>
        </n-dropdown>
        <template v-else>
          <span class="breadcrumb-item link" @click="handleSwitchToDirect">
            💬 默认对话
          </span>
          <span class="breadcrumb-sep">|</span>
          <span class="breadcrumb-item active">
            🤖 {{ chat.activeSession ? '对话中' : '选择智能体' }}
          </span>
          <span class="breadcrumb-close" @click="handleSwitchToDirect">&times;</span>
        </template>
      </div>
      <div class="breadcrumb-right">
        <n-button size="tiny" quaternary @click="handleNewSession">
          <template #icon>
            <n-icon size="16">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                <path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>
              </svg>
            </n-icon>
          </template>
          新对话
        </n-button>
      </div>
    </div>

    <!-- Chat area -->
    <div class="chat-section">
      <!-- No system agent error -->
      <div v-if="systemAgentError" class="empty-state">
        <n-empty description="请先在模型管理中设置默认模型">
          <template #extra>
            <n-button type="primary" @click="$router.push({ name: 'ModelManage' })">
              前往设置
            </n-button>
          </template>
        </n-empty>
      </div>

      <!-- Loading -->
      <div v-else-if="systemAgentLoading" class="empty-state">
        <n-spin />
      </div>

      <!-- Welcome (direct mode, no session) -->
      <div v-else-if="isDirectMode && !hasSession" class="welcome-area">
        <div class="welcome-title">你好！</div>
        <div class="welcome-subtitle">需要我为你做些什么？</div>
      </div>

      <!-- Messages -->
      <div v-if="hasSession" ref="messagesContainer" class="messages-area">
        <div class="messages-inner">
          <MessageBubble v-for="(msg, i) in allMessages" :key="i" :msg="msg" />
          <div v-if="chat.isStreaming" class="streaming-cursor">
            <span class="cursor-dot">▍</span>
          </div>
          <!-- Tool call visualization -->
          <div v-if="chat.currentToolCalls.length > 0" class="tool-calls-indicator">
            <div v-for="tool in chat.currentToolCalls" :key="tool" class="tool-call-chip">
              <span class="tool-call-spinner"></span>
              <span class="tool-call-name">{{ tool }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Input bar -->
      <div class="input-section">
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
            quaternary size="small"
            :loading="uploading"
            :disabled="chat.isStreaming"
            @click="fileInput?.click()"
            class="action-btn"
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
                <n-icon><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M6 6h12v12H6z"/></svg></n-icon>
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
                <n-icon><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/></svg></n-icon>
              </template>
              发送
            </n-button>
          </div>
        </div>
      </div>
    </div>

    <!-- Agent card grid -->
    <div class="agents-section">
      <div class="agents-section-title">智能体推荐</div>
      <div v-if="agentStore.loading" class="agents-loading">
        <n-spin size="small" />
      </div>
      <div v-else-if="agentStore.agents.length === 0" class="agents-empty">
        <n-empty description="暂无智能体" />
      </div>
      <div v-else class="agent-grid">
        <div
          v-for="agent in agentStore.agents.filter(a => a.status === 'active')"
          :key="agent.id"
          class="agent-card-wrapper"
          @click="handleAgentClick(agent)"
        >
          <AgentCard :agent="agent" disable-nav />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.direct-chat-view {
  max-width: 960px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 80px - 48px);
  gap: 24px;
}

/* ── Breadcrumb ── */
.breadcrumb-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-color, #eee);
}

.breadcrumb-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.breadcrumb-item.active {
  font-weight: 600;
  color: var(--text-color, #333);
}

.breadcrumb-item.link {
  cursor: pointer;
  color: #999;
  transition: color 0.15s;
}

.breadcrumb-item.link:hover {
  color: var(--text-color, #333);
}

.breadcrumb-sep {
  color: #ccc;
  font-size: 12px;
}

.breadcrumb-close {
  cursor: pointer;
  color: #999;
  font-size: 18px;
  line-height: 1;
  padding: 0 4px;
  transition: color 0.15s;
}

.breadcrumb-close:hover {
  color: #e74c3c;
}

/* ── Chat section ── */
.chat-section {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
}

.welcome-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 40px 0 20px;
}

.welcome-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-color, #333);
  margin-bottom: 8px;
}

.welcome-subtitle {
  font-size: 16px;
  color: #999;
}

.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
  min-height: 0;
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

/* ── Tool call indicator ── */
.tool-calls-indicator {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 46px 4px;
  align-items: center;
}

.tool-call-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--tag-bg, #f0f0f0);
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 6px;
  padding: 4px 10px 4px 8px;
  font-size: 12px;
  color: var(--text-color, #666);
  animation: toolFadeIn 0.25s ease-out;
}

.tool-call-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid var(--border-color, #ddd);
  border-top-color: #C8815F;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  flex-shrink: 0;
}

.tool-call-name {
  font-weight: 500;
  color: var(--text-color, #333);
  white-space: nowrap;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@keyframes toolFadeIn {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ── Input ── */
.input-section {
  flex-shrink: 0;
  border-top: 1px solid var(--border-color, #eee);
  padding-top: 12px;
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

.action-btn {
  margin-bottom: 2px;
}


/* ── Agent section ── */
.agents-section {
  padding-top: 8px;
}

.agents-section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-color, #333);
  margin-bottom: 16px;
}

.agents-loading,
.agents-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
}

.agent-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.agent-card-wrapper {
  cursor: pointer;
}

.model-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 12px;
  background: var(--tag-bg, #f0f0f0);
  color: var(--text-color, #666);
  cursor: pointer;
  transition: background 0.15s;
  margin-left: 8px;
  white-space: nowrap;
}
.model-pill:hover {
  background: var(--hover-bg, #e0e0e0);
}
</style>
