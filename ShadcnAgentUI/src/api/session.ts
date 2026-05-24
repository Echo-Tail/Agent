import http from './request'
import { STORAGE_KEY_TOKEN, STREAM_TIMEOUT } from '@/constants'
import type { Session, SessionSummary, SessionFolder, SseEvent } from '@/types/session'
import i18n from '@/locales'

export function listSessionsApi(params?: { folderId?: number; agentId?: number }) {
  return http.get<any, SessionSummary[]>('/sessions', { params })
}

export function getSessionApi(id: number) {
  return http.get<any, Session>(`/sessions/${id}`)
}

export function createSessionApi(data: { agentId: number; title?: string; folderId?: number }) {
  return http.post<any, Session>('/sessions', data)
}

export function updateSessionApi(id: number, data: { title?: string; folderId?: number | null }) {
  return http.put<any, Session>(`/sessions/${id}`, data)
}

export function deleteSessionApi(id: number) {
  return http.delete<any, void>(`/sessions/${id}`)
}

export function addMessageApi(sessionId: number, role: string, content: string) {
  return http.post<any, unknown>(`/sessions/${sessionId}/messages`, { role, content })
}

export function listFoldersApi() {
  return http.get<any, SessionFolder[]>('/session-folders')
}

export function createFolderApi(data: { name: string }) {
  return http.post<any, SessionFolder>('/session-folders', data)
}

export function updateFolderApi(id: number, data: { name: string }) {
  return http.put<any, SessionFolder>(`/session-folders/${id}`, data)
}

export function deleteFolderApi(id: number) {
  return http.delete<any, void>(`/session-folders/${id}`)
}

export interface StreamChatCallbacks {
  onToken: (text: string) => void
  onDone: (fullText: string) => void
  onError: (message: string) => void
  onReasoning?: (content: string) => void
  onToolCall?: (tool: string) => void
  onToolResult?: (tool: string, summary?: string) => void
}

export async function streamChat(
  agentId: number,
  sessionId: number,
  content: string,
  callbacks: StreamChatCallbacks,
  abortSignal?: AbortSignal,
  timeoutMs: number = STREAM_TIMEOUT,
): Promise<void> {
  const { onToken, onDone, onError, onReasoning, onToolCall, onToolResult } = callbacks
  const token = localStorage.getItem(STORAGE_KEY_TOKEN)

  const timeoutController = new AbortController()
  let timedOut = false
  const timeoutId = setTimeout(() => {
    timedOut = true
    timeoutController.abort()
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
      onError('HTTP ' + response.status)
      return
    }

    const reader = response.body?.getReader()
    if (!reader) {
      clearTimeout(timeoutId)
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
            onDone(event.content || fullText)
          } else if (event.type === 'error') {
            clearTimeout(timeoutId)
            completed = true
            onError(event.message)
          } else if (event.type === 'reasoning') {
            onReasoning?.(event.content)
          } else if (event.type === 'tool_call') {
            onToolCall?.(event.tool)
          } else if (event.type === 'tool_result') {
            onToolResult?.(event.tool, event.summary)
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
    throw e
  } finally {
    clearTimeout(timeoutId)
  }
}
