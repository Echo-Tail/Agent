<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useChatStore } from '../../stores/chat'
import { useAgentStore } from '../../stores/agent'
import { getSystemAgentApi, getAgentApi, updateAgentApi } from '../../api/agent'
import { listToolsApi } from '../../api/tool'
import { uploadFileApi } from '../../api/file'
import { listModelsApi } from '../../api/model'
import MessageBubble from '../../components/MessageBubble.vue'
import AgentCard from '../../components/AgentCard.vue'
import type { Agent } from '../../types/agent'
import type { FileRecord, AiModel } from '../../types/api'
import type { ToolDefinition } from '../../api/tool'
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

/* ====== Tools (always all available in direct mode) ====== */
const availableTools = ref<ToolDefinition[]>([])
const selectedToolIds = ref<string[]>([])
const showToolPanel = ref(false)
const toolPanelRef = ref<HTMLElement | null>(null)
const activeToolCount = computed(() => selectedToolIds.value.length)

async function fetchTools() {
  try {
    const res = await listToolsApi()
    if (res.data.code === 200) {
      availableTools.value = res.data.data ?? []
    }
  } catch { /* ignore */ }
}

function toggleTool(toolId: string) {
  const idx = selectedToolIds.value.indexOf(toolId)
  if (idx >= 0) selectedToolIds.value.splice(idx, 1)
  else selectedToolIds.value.push(toolId)
}

function isToolSelected(toolId: string): boolean {
  return selectedToolIds.value.includes(toolId)
}

function onClickOutside(e: MouseEvent) {
  if (toolPanelRef.value && !toolPanelRef.value.contains(e.target as Node)) {
    showToolPanel.value = false
  }
}

onMounted(() => document.addEventListener('click', onClickOutside))
onUnmounted(() => document.removeEventListener('click', onClickOutside))

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
  selectedToolIds.value = []
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
  fetchTools()
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

          <div ref="toolPanelRef" class="tool-wrapper">
            <n-button
              quaternary size="small"
              :disabled="chat.isStreaming || !isDirectMode"
              @click="showToolPanel = !showToolPanel"
              class="action-btn"
            >
              <template #icon>
                <n-icon size="20">
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M21.58 16.09l-1.09-1.09a2.03 2.03 0 00-2.83 0l-5.94 5.94a2.03 2.03 0 000 2.83l1.09 1.09c.78.78 2.05.78 2.83 0l5.94-5.94c.78-.78.78-2.05 0-2.83zm-4.24 2.83l-4.24 4.24-1.41-1.41 4.24-4.24 1.41 1.41zM2 17.27V21h3.73l9.9-9.9-3.73-3.73L2 17.27zM18.36 8.64l1.41-1.41c.78-.78.78-2.05 0-2.83l-1.17-1.17a2.03 2.03 0 00-2.83 0l-1.41 1.41 4 4z"/>
                  </svg>
                </n-icon>
              </template>
            </n-button>
            <span v-if="activeToolCount > 0 && isDirectMode" class="tool-badge">{{ activeToolCount }}</span>
            <div v-if="showToolPanel && isDirectMode" class="tool-panel">
              <div class="tool-panel-header">选择工具（直接对话）</div>
              <div
                v-for="tool in availableTools"
                :key="tool.id"
                class="tool-item"
                @click="toggleTool(tool.id)"
              >
                <n-checkbox :checked="isToolSelected(tool.id)" />
                <div class="tool-item-info">
                  <span class="tool-item-name">{{ tool.name }}</span>
                  <span class="tool-item-desc">{{ tool.description }}</span>
                </div>
              </div>
            </div>
          </div>

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

.tool-wrapper {
  position: relative;
  margin-bottom: 2px;
}

.tool-badge {
  position: absolute;
  top: -2px;
  right: -2px;
  min-width: 16px;
  height: 16px;
  line-height: 16px;
  text-align: center;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  background: #C8815F;
  border-radius: 8px;
  padding: 0 5px;
  pointer-events: none;
}

.tool-panel {
  position: absolute;
  bottom: 100%;
  left: 0;
  margin-bottom: 8px;
  min-width: 260px;
  background: var(--card-bg, #fff);
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  padding: 8px 0;
  z-index: 100;
}

.tool-panel-header {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-color, #333);
  padding: 6px 14px 10px;
  border-bottom: 1px solid var(--border-color, #eee);
  margin-bottom: 4px;
}

.tool-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 14px;
  cursor: pointer;
  transition: background 0.15s;
}

.tool-item:hover {
  background: var(--hover-bg, #f5f5f5);
}

.tool-item-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.tool-item-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-color, #333);
}

.tool-item-desc {
  font-size: 12px;
  color: #999;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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
