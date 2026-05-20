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
} from '../api/session'
import type { Session, SessionSummary, SessionFolder, SessionMessage } from '../types/session'

export type ChatMode = 'direct' | 'agent'

export const useChatStore = defineStore('chat', () => {
  // Chat mode
  const chatMode = ref<ChatMode>('direct')

  // Session state
  const sessions = ref<SessionSummary[]>([])
  const activeSession = ref<Session | null>(null)
  const messages = ref<SessionMessage[]>([])
  const sessionLoading = ref(false)

  // Folder state
  const folders = ref<SessionFolder[]>([])

  // Streaming state
  const isStreaming = ref(false)
  const streamingText = ref('')
  const abortController = ref<AbortController | null>(null)

  // Tool call tracking (for UI display during streaming)
  const currentToolCalls = ref<string[]>([])

  // Input state
  const inputText = ref('')

  // Current agent ID
  const activeAgentId = ref<number | null>(null)

  /* ====== Getters ====== */

  const folderTree = computed(() => {
    return [...folders.value].sort((a, b) => a.orderNum - b.orderNum)
  })

  /* ====== Session Actions ====== */

  async function fetchSessions(params?: { folderId?: number; agentId?: number }) {
    sessionLoading.value = true
    try {
      const res = await listSessionsApi(params)
      if (res.data.code === 200) {
        sessions.value = res.data.data ?? []
      }
    } finally {
      sessionLoading.value = false
    }
  }

  async function loadSession(id: number) {
    sessionLoading.value = true
    try {
      const res = await getSessionApi(id)
      if (res.data.code === 200 && res.data.data) {
        activeSession.value = res.data.data
        messages.value = res.data.data.messages ?? []
      }
    } finally {
      sessionLoading.value = false
    }
  }

  async function createSession(agentId: number, title?: string) {
    const res = await createSessionApi({ agentId, title: title || '新对话' })
    if (res.data.code === 200 && res.data.data) {
      activeSession.value = res.data.data
      messages.value = []
      return res.data.data
    }
    throw new Error(res.data.message || '创建会话失败')
  }

  async function updateSession(id: number, data: { title?: string; folderId?: number | null }) {
    const res = await updateSessionApi(id, data)
    if (res.data.code === 200) {
      await fetchSessions()
      return true
    }
    return false
  }

  async function removeSession(id: number) {
    const res = await deleteSessionApi(id)
    if (res.data.code === 200) {
      if (activeSession.value?.id === id) {
        activeSession.value = null
        messages.value = []
      }
      await fetchSessions()
      return true
    }
    return false
  }

  /* ====== Folder Actions ====== */

  async function fetchFolders() {
    try {
      const res = await listFoldersApi()
      if (res.data.code === 200) {
        folders.value = res.data.data ?? []
      }
    } catch {
      // ignore
    }
  }

  async function addFolder(name: string) {
    const res = await createFolderApi({ name })
    if (res.data.code === 200) {
      await fetchFolders()
      return true
    }
    return false
  }

  async function renameFolder(id: number, name: string) {
    const res = await updateFolderApi(id, { name })
    if (res.data.code === 200) {
      await fetchFolders()
      return true
    }
    return false
  }

  async function removeFolder(id: number) {
    const res = await deleteFolderApi(id)
    if (res.data.code === 200) {
      await fetchFolders()
      return true
    }
    return false
  }

  /* ====== Mode Switching ====== */

  function switchToDirect() {
    chatMode.value = 'direct'
    activeAgentId.value = null
    clearActiveSession()
  }

  function switchToAgent(_agentId: number) {
    chatMode.value = 'agent'
    activeAgentId.value = _agentId
    clearActiveSession()
  }

  /* ====== Chat / Streaming ====== */

  async function sendMessage(agentId: number, content: string, skipUserPush = false) {
    if (!activeSession.value) {
      console.warn('sendMessage skipped: no active session (agentId=%s)', agentId)
      throw new Error('没有活跃会话，请重新选择 Agent')
    }

    if (!skipUserPush) {
      const userMsg: SessionMessage = {
        role: 'user',
        content,
        timestamp: new Date().toISOString(),
      }
      messages.value.push(userMsg)
    }

    isStreaming.value = true
    streamingText.value = ''
    currentToolCalls.value = []
    abortController.value = new AbortController()

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
            messages.value.push({
              role: 'assistant',
              content: fullText,
              timestamp: new Date().toISOString(),
            })
            streamingText.value = ''
            isStreaming.value = false
            currentToolCalls.value = []
            abortController.value = null
            fetchSessions()
          },
          onError: (errorMsg) => {
            const pc = streamingText.value
            streamingText.value = ''
            isStreaming.value = false
            currentToolCalls.value = []
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
          },
          onToolResult: (tool) => {
            currentToolCalls.value = currentToolCalls.value.filter(t => t !== tool)
          },
        },
        abortController.value.signal,
      )
    } catch (e) {
      console.error('sendMessage failed:', e)
      if (isStreaming.value) {
        isStreaming.value = false
        abortController.value = null
        messages.value.push({
          role: 'assistant',
          content: e instanceof Error ? e.message : '发送消息时发生未知错误',
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
  }

  /** 重试发送：找到触发 error 的上一条用户消息，重新发送 */
  async function retryMessage(errorMsg: SessionMessage) {
    if (isStreaming.value || !activeAgentId.value) return
    const errIdx = messages.value.indexOf(errorMsg)
    if (errIdx < 0 || !messages.value[errIdx]?.isError) return

    // 找到该 error 之前的最后一条用户消息
    let lastUserContent = ''
    for (let i = errIdx - 1; i >= 0; i--) {
      if (messages.value[i].role === 'user') {
        lastUserContent = messages.value[i].content
        break
      }
    }
    if (!lastUserContent) return

    // 移除 error 消息
    messages.value.splice(errIdx, 1)

    // 重新发送（不重复 push 用户消息）
    await sendMessage(activeAgentId.value, lastUserContent, true)
  }

  function clearActiveSession() {
    activeSession.value = null
    messages.value = []
    inputText.value = ''
  }

  return {
    // state
    chatMode,
    sessions,
    activeSession,
    messages,
    sessionLoading,
    folders,
    isStreaming,
    streamingText,
    currentToolCalls,
    inputText,
    activeAgentId,
    // getters
    folderTree,
    // mode
    switchToDirect,
    switchToAgent,
    // session actions
    fetchSessions,
    loadSession,
    createSession,
    updateSession,
    removeSession,
    // folder actions
    fetchFolders,
    addFolder,
    renameFolder,
    removeFolder,
    // chat
    sendMessage,
    retryMessage,
    stopStreaming,
    clearActiveSession,
  }
})
