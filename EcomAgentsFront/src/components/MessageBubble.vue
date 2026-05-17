<script setup lang="ts">
import { ref } from 'vue'
import type { SessionMessage } from '../types/session'
import MarkdownRenderer from './MarkdownRenderer.vue'

const props = defineProps<{
  msg: SessionMessage
}>()

const msgCopied = ref(false)

async function copyMessage() {
  try {
    await navigator.clipboard.writeText(props.msg.content)
    msgCopied.value = true
    setTimeout(() => { msgCopied.value = false }, 1500)
  } catch {
    // fallback silently
  }
}
</script>

<template>
  <div class="message-row" :class="[msg.role, msg.isError ? 'error' : '']">
    <div class="message-avatar">
      <n-avatar
        :size="36"
        round
        :color="msg.isError ? '#E8805A' : (msg.role === 'user' ? '#8B8178' : '#C8815F')"
      >
        <n-icon size="18" color="#fff">
          <svg v-if="msg.isError" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
          </svg>
          <svg v-else-if="msg.role === 'user'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/>
          </svg>
          <svg v-else xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/>
          </svg>
        </n-icon>
      </n-avatar>
    </div>
    <div class="message-bubble">
      <div class="message-content">
        <MarkdownRenderer
          v-if="msg.role === 'assistant' && !msg.isError"
          :content="msg.content"
          class="message-text"
        />
        <pre v-else class="message-text">{{ msg.content }}</pre>
        <button
          v-if="msg.role === 'assistant' && !msg.isError"
          class="msg-copy-btn"
          :class="{ copied: msgCopied }"
          :title="msgCopied ? '已复制' : '复制消息'"
          @click="copyMessage"
        >
          <svg v-if="!msgCopied" xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
          </svg>
          <svg v-else xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="20 6 9 17 4 12"/>
          </svg>
        </button>
      </div>
      <div class="message-time">
        {{ new Date(msg.timestamp).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.message-row {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  max-width: 80%;
}

.message-row.assistant {
  align-self: flex-start;
}

.message-row.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message-bubble {
  min-width: 0;
}

.message-content {
  padding: 10px 14px;
  border-radius: 12px;
  background: var(--message-bg, #F0EBE5);
  position: relative;
}

.assistant .message-content {
  background: var(--message-assistant-bg, #FAF6F1);
  border-bottom-left-radius: 4px;
}

.user .message-content {
  background: var(--message-user-bg, #F0EBE5);
  border-bottom-right-radius: 4px;
}

.error .message-content {
  background: var(--message-error-bg, #FDF0EC);
  border: 1px solid var(--message-error-border, #F5C6B8);
}

.error .message-text {
  color: var(--message-error-color, #B84A2A);
}

.message-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
}

.message-time {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
  padding: 0 4px;
}

.user .message-time {
  text-align: right;
}

.msg-copy-btn {
  position: absolute;
  bottom: 6px;
  right: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: rgba(0, 0, 0, 0.3);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s, background 0.15s, color 0.15s;
}

.assistant .message-content:hover .msg-copy-btn {
  opacity: 1;
}

.msg-copy-btn:hover {
  background: rgba(0, 0, 0, 0.06);
  color: rgba(0, 0, 0, 0.6);
}

.msg-copy-btn.copied {
  opacity: 1;
  color: #4CAF50;
}
</style>
