import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  listSessionsApi,
  getSessionApi,
  createSessionApi,
  updateSessionApi,
  deleteSessionApi,
  listFoldersApi,
  createFolderApi,
  updateFolderApi,
  deleteFolderApi,
  streamChat,
} from '@/api/session'
import type { Session, SessionSummary, SessionFolder, SessionMessage } from '@/types/session'
import i18n from '@/locales'

const { t } = i18n.global

export type ChatMode = 'direct' | 'agent'
export type ToolStatus = 'running' | 'done' | 'degraded' | 'timeout' | 'empty'

export interface CurrentToolStatus {
  tool: string
  status: ToolStatus
  message: string
}

export const useChatStore = defineStore('chat', () => {
  const chatMode = ref<ChatMode>('direct')
  const sessions = ref<SessionSummary[]>([])
  const activeSession = ref<Session | null>(null)
  const messages = ref<SessionMessage[]>([])
  const sessionLoading = ref(false)
  const folders = ref<SessionFolder[]>([])
  const isStreaming = ref(false)
  const streamingText = ref('')
  const abortController = ref<AbortController | null>(null)
  const currentToolCalls = ref<string[]>([])
  const currentToolStatuses = ref<CurrentToolStatus[]>([])
  const inputText = ref('')
  const activeAgentId = ref<number | null>(null)
  const pendingFile = ref<{ id: number; name: string; url: string; size: number } | null>(null)

  const folderTree = computed(() => {
    return [...folders.value].sort((a, b) => a.orderNum - b.orderNum)
  })

  async function fetchSessions(params?: { folderId?: number; agentId?: number }) {
    sessionLoading.value = true
    try {
      sessions.value = (await listSessionsApi(params)) ?? []
    } finally {
      sessionLoading.value = false
    }
  }

  async function loadSession(id: number) {
    sessionLoading.value = true
    try {
      const data = await getSessionApi(id)
      if (data) {
        activeSession.value = data
        // Map backend flat fileId/fileName → frontend structured file object
        messages.value = (data.messages ?? []).map((msg: any) => {
          if (msg.fileId) {
            return {
              ...msg,
              file: {
                id: msg.fileId,
                name: msg.fileName || 'file',
                url: `/v1/files/${msg.fileId}/download`,
                size: 0,
              },
            }
          }
          return msg
        })
      }
    } finally {
      sessionLoading.value = false
    }
  }

  async function createSession(agentId: number, title?: string) {
    const data = await createSessionApi({ agentId, title: title || t('common.newChat') })
    if (data) {
      activeSession.value = data
      messages.value = []
      return data
    }
    throw new Error(t('error.createSessionFailed'))
  }

  async function updateSession(id: number, data: { title?: string; folderId?: number | null }) {
    await updateSessionApi(id, data)
    await fetchSessions()
    return true
  }

  async function removeSession(id: number) {
    await deleteSessionApi(id)
    if (activeSession.value?.id === id) {
      activeSession.value = null
      messages.value = []
    }
    await fetchSessions()
    return true
  }

  async function fetchFolders() {
    try {
      folders.value = (await listFoldersApi()) ?? []
    } catch { /* ignore */ }
  }

  async function addFolder(name: string) {
    await createFolderApi({ name })
    await fetchFolders()
    return true
  }

  async function renameFolder(id: number, name: string) {
    await updateFolderApi(id, { name })
    await fetchFolders()
    return true
  }

  async function removeFolder(id: number) {
    await deleteFolderApi(id)
    await fetchFolders()
    return true
  }

  function switchToDirect() {
    chatMode.value = 'direct'
    activeAgentId.value = null
    clearActiveSession()
  }

  function switchToAgent(_agentId: number, options?: { preserveSession?: boolean }) {
    chatMode.value = 'agent'
    activeAgentId.value = _agentId
    if (!options?.preserveSession) {
      clearActiveSession()
    }
  }

  async function sendMessage(agentId: number, content: string, skipUserPush = false) {
    if (!activeSession.value) {
      throw new Error(t('error.noActiveSession'))
    }

    if (!skipUserPush) {
      messages.value.push({
        role: 'user',
        content,
        timestamp: new Date().toISOString(),
      })
    }

    isStreaming.value = true
    streamingText.value = ''
    currentToolCalls.value = []
    currentToolStatuses.value = []
    abortController.value = new AbortController()
    pendingFile.value = null

    try {
      await streamChat(
        agentId,
        activeSession.value.id,
        content,
        {
          onToken: (token) => {
            streamingText.value += token
          },
          onDone: (fullText) => {
            const msg: SessionMessage = {
              role: 'assistant',
              content: fullText,
              timestamp: new Date().toISOString(),
            }
            if (pendingFile.value) {
              msg.file = { ...pendingFile.value }
              pendingFile.value = null
            }
            messages.value.push(msg)
            streamingText.value = ''
            isStreaming.value = false
            currentToolCalls.value = []
            currentToolStatuses.value = []
            abortController.value = null
            fetchSessions()
          },
          onError: (errorMsg) => {
            const pc = streamingText.value
            streamingText.value = ''
            isStreaming.value = false
            currentToolCalls.value = []
            currentToolStatuses.value = []
            abortController.value = null
            messages.value.push({
              role: 'assistant',
              content: errorMsg,
              timestamp: new Date().toISOString(),
              isError: true,
              partialContent: pc || undefined,
            })
          },
          onToolCall: (tool) => {
            currentToolCalls.value = [...currentToolCalls.value, tool]
            upsertToolStatus(tool, 'running', describeToolCall(tool))
          },
          onToolResult: (tool, summary) => {
            currentToolCalls.value = currentToolCalls.value.filter(t => t !== tool)
            const result = describeToolResult(tool, summary)
            upsertToolStatus(tool, result.status, result.message)
          },
          onFile: (file) => {
            pendingFile.value = file
          },
        },
        abortController.value.signal,
      )
    } catch (e) {
      console.error('sendMessage failed:', e)
      if (isStreaming.value) {
        isStreaming.value = false
        abortController.value = null
        currentToolCalls.value = []
        currentToolStatuses.value = []
        messages.value.push({
          role: 'assistant',
          content: e instanceof Error ? e.message : t('error.sendMessageFailed'),
          timestamp: new Date().toISOString(),
          isError: true,
        })
      }
    }
  }

  function stopStreaming() {
    abortController.value?.abort()
    if (streamingText.value) {
      messages.value.push({
        role: 'assistant',
        content: streamingText.value,
        timestamp: new Date().toISOString(),
      })
    }
    streamingText.value = ''
    isStreaming.value = false
    abortController.value = null
    currentToolCalls.value = []
    currentToolStatuses.value = []
  }

  async function retryMessage(errorMsg: SessionMessage) {
    if (isStreaming.value || !activeAgentId.value) return
    const errIdx = messages.value.indexOf(errorMsg)
    if (errIdx < 0 || !messages.value[errIdx]?.isError) return
    let lastUserContent = ''
    for (let i = errIdx - 1; i >= 0; i--) {
      if (messages.value[i].role === 'user') {
        lastUserContent = messages.value[i].content
        break
      }
    }
    if (!lastUserContent) return
    messages.value.splice(errIdx, 1)
    await sendMessage(activeAgentId.value, lastUserContent, true)
  }

  function clearActiveSession() {
    activeSession.value = null
    messages.value = []
    inputText.value = ''
  }

  function upsertToolStatus(tool: string, status: ToolStatus, message: string) {
    const next = currentToolStatuses.value.filter(item => item.tool !== tool)
    next.push({ tool, status, message })
    currentToolStatuses.value = next
  }

  function describeToolCall(tool: string) {
    if (tool === 'retrieve_knowledge') return '知识库检索中'
    return `${tool} 运行中`
  }

  function describeToolResult(tool: string, summary?: string): { status: ToolStatus; message: string } {
    if (tool !== 'retrieve_knowledge') {
      return { status: 'done', message: `${tool} 已完成` }
    }
    const normalized = summary ?? ''
    if (normalized.includes('text_search_fallback_after_vector_timeout')) {
      return { status: 'degraded', message: '知识库向量检索超时，已使用文本检索' }
    }
    if (normalized.includes('degraded_vector_search')) {
      return { status: 'degraded', message: '知识库向量检索部分降级' }
    }
    if (normalized.includes('text_search_fallback')) {
      return { status: 'degraded', message: '知识库已使用文本检索' }
    }
    if (normalized.includes('knowledge_empty')) {
      return { status: 'empty', message: '知识库未检索到相关内容' }
    }
    if (normalized.includes('vector_timeout_no_fallback')) {
      return { status: 'timeout', message: '知识库检索超时，未找到可用降级结果' }
    }
    if (normalized.toLowerCase().includes('timeout')) {
      return { status: 'timeout', message: '知识库检索超时' }
    }
    return { status: 'done', message: '知识库检索完成' }
  }

  return {
    chatMode, sessions, activeSession, messages, sessionLoading,
    folders, isStreaming, streamingText, currentToolCalls, currentToolStatuses, inputText, activeAgentId,
    folderTree,
    switchToDirect, switchToAgent,
    fetchSessions, loadSession, createSession, updateSession, removeSession,
    fetchFolders, addFolder, renameFolder, removeFolder,
    sendMessage, retryMessage, stopStreaming, clearActiveSession,
  }
})
