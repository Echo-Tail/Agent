import { api } from './request'
import { STORAGE_KEY_TOKEN, STORAGE_KEY_USER, STREAM_TIMEOUT } from '@/constants'
import type { Session, SessionSummary, SessionFolder, SseEvent } from '@/types/session'
import i18n from '@/locales'
import { logger } from '@/utils/logger'

export function listSessionsApi(params?: { folderId?: number; agentId?: number }) {
  return api.get<SessionSummary[]>('/sessions', { params })
}

export function getSessionApi(id: number) {
  return api.get<Session>(`/sessions/${id}`)
}

export function createSessionApi(data: { agentId: number; title?: string; folderId?: number }) {
  return api.post<Session>('/sessions', data)
}

export function updateSessionApi(id: number, data: { title?: string; folderId?: number | null }) {
  return api.put<Session>(`/sessions/${id}`, data)
}

export function deleteSessionApi(id: number) {
  return api.delete(`/sessions/${id}`)
}

export function addMessageApi(sessionId: number, role: string, content: string) {
  return api.post<unknown>(`/sessions/${sessionId}/messages`, { role, content })
}

export function listFoldersApi() {
  return api.get<SessionFolder[]>('/session-folders')
}

export function createFolderApi(data: { name: string }) {
  return api.post<SessionFolder>('/session-folders', data)
}

export function updateFolderApi(id: number, data: { name: string }) {
  return api.put<SessionFolder>(`/session-folders/${id}`, data)
}

export function deleteFolderApi(id: number) {
  return api.delete(`/session-folders/${id}`)
}

export interface StreamChatCallbacks {
  onToken: (text: string) => void
  onDone: (fullText: string) => void
  onError: (message: string) => void
  onReasoning?: (content: string) => void
  onToolCall?: (tool: string) => void
  onToolResult?: (tool: string, summary?: string) => void
  onFile?: (file: { id: number; name: string; url: string; size: number }) => void
}

export async function streamChat(
  agentId: number,
  sessionId: number,
  content: string,
  callbacks: StreamChatCallbacks,
  abortSignal?: AbortSignal,
  timeoutMs: number = STREAM_TIMEOUT,
): Promise<void> {
  const { onToken, onDone, onError, onReasoning, onToolCall, onToolResult, onFile } = callbacks
  const token = localStorage.getItem(STORAGE_KEY_TOKEN)

  const agentInfo = `agent=${agentId} session=${sessionId}`
  logger.info('SSE', `→ stream start ${agentInfo}`, { contentLength: content.length })

  const timeoutController = new AbortController()
  let timedOut = false
  const timeoutId = setTimeout(() => {
    timedOut = true
    timeoutController.abort()
    logger.warn('SSE', `timeout ${agentInfo} (${timeoutMs}ms)`)
    onError(i18n.global.t('error.requestTimeout'))
  }, timeoutMs)

  const combinedSignal = abortSignal
    ? AbortSignal.any([abortSignal, timeoutController.signal])
    : timeoutController.signal

  try {
    const response = await fetch(`/chat/${agentId}/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ sessionId, content }),
      signal: combinedSignal,
    })

    if (!response.ok) {
      clearTimeout(timeoutId)
      if (response.status === 401) {
        logger.warn('SSE', `401 unauthorized ${agentInfo}`)
        localStorage.removeItem(STORAGE_KEY_TOKEN)
        localStorage.removeItem(STORAGE_KEY_USER)
        if (!window.location.pathname.startsWith('/login')) {
          window.location.href = '/login'
        }
        return
      }
      logger.error('SSE', `HTTP error ${response.status} ${agentInfo}`)
      onError('HTTP ' + response.status)
      return
    }

    const reader = response.body?.getReader()
    if (!reader) {
      clearTimeout(timeoutId)
      logger.error('SSE', `no reader ${agentInfo}`)
      onError(i18n.global.t('error.operationFailed'))
      return
    }

    const decoder = new TextDecoder()
    let buffer = ''
    let fullText = ''
    let completed = false

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const parts = buffer.split('\n')
      buffer = parts.pop() || ''

      for (const line of parts) {
        const trimmed = line.trim()
        if (!trimmed || !trimmed.startsWith('data:')) continue
        try {
          const event: SseEvent = JSON.parse(trimmed.slice(5).trim())
          if (event.type === 'token') {
            fullText += event.content
            onToken(event.content)
          } else if (event.type === 'done') {
            clearTimeout(timeoutId)
            completed = true
            logger.info('SSE', `stream done ${agentInfo}`, { totalTokens: fullText.length })
            onDone(event.content || fullText)
          } else if (event.type === 'error') {
            clearTimeout(timeoutId)
            completed = true
            logger.error('SSE', `stream error ${agentInfo}`, { message: event.message })
            onError(event.message)
          } else if (event.type === 'reasoning') {
            onReasoning?.(event.content)
          } else if (event.type === 'tool_call') {
            onToolCall?.(event.tool)
          } else if (event.type === 'tool_result') {
            onToolResult?.(event.tool, event.summary)
          } else if (event.type === 'file') {
            onFile?.({ id: event.id, name: event.name, url: event.url, size: event.size })
          }
        } catch {
          console.warn('streamChat: malformed SSE JSON, skipping line:', trimmed)
        }
      }
    }
    if (!completed) {
      clearTimeout(timeoutId)
      if (fullText) {
        onDone(fullText)
      }
    }
  } catch (e) {
    if (timedOut) return
    if (e instanceof DOMException && e.name === 'AbortError') return
    logger.error('SSE', `stream exception ${agentInfo}`, { error: String(e) })
    throw e
  } finally {
    clearTimeout(timeoutId)
  }
}
