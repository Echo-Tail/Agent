<script setup lang="ts">
/**
 * AgentMessageItem.vue — 单条消息气泡
 *
 * 使用 shadcn-vue Message + Bubble 渲染用户/AI/错误消息。
 * 支持对齐、头像、Markdown 渲染、复制、重试和内嵌文件下载。
 */
import type { HTMLAttributes } from 'vue'
import type { SessionMessage } from '@/types/session'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import {
  Message,
  MessageAvatar,
  MessageContent,
  MessageFooter,
} from '@/components/ui/message'
import {
  Bubble,
  BubbleContent,
} from '@/components/ui/bubble'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import {
  Copy,
  Check,
  RefreshCw,
  File as FileIcon,
  Download,
} from 'lucide-vue-next'

const { t } = useI18n()

interface FileInfo {
  id: number
  name: string
  url: string
  size: number
}

interface Props {
  message: SessionMessage
  align: 'start' | 'end'
  agentName?: string
  agentInitial?: string
  senderName?: string
  timestamp?: string
  showCopy?: boolean
  showRetry?: boolean
  class?: HTMLAttributes['class']
}

const props = withDefaults(defineProps<Props>(), {
  align: 'start',
  agentName: '',
  agentInitial: 'A',
  senderName: '',
  timestamp: '',
  showCopy: true,
  showRetry: true,
})

const emit = defineEmits<{
  retry: [message: SessionMessage]
}>()

const copied = ref(false)

function handleCopy() {
  navigator.clipboard.writeText(props.message.content)
  copied.value = true
  setTimeout(() => { copied.value = false }, 2000)
}

function handleRetry() {
  emit('retry', props.message)
}

function downloadFile(file: FileInfo) {
  const a = document.createElement('a')
  a.href = file.url
  a.download = file.name
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}
</script>

<template>
  <Message :align="align">
    <MessageAvatar class="self-start mt-6">
      <Avatar class="h-8 w-8">
        <AvatarFallback
          :class="align === 'end'
            ? 'bg-muted text-xs'
            : 'bg-primary text-primary-foreground text-xs'"
        >
          {{ align === 'end' ? 'U' : agentInitial }}
        </AvatarFallback>
      </Avatar>
    </MessageAvatar>
    <MessageContent>
      <!-- Sender name + timestamp -->
      <div
        v-if="senderName"
        class="mb-0.5 flex items-center gap-2"
        :class="align === 'end' ? 'justify-end' : ''"
      >
        <span class="text-xs text-muted-foreground">{{ senderName }}</span>
        <span v-if="timestamp" class="text-xs text-muted-foreground/60">{{ timestamp }}</span>
      </div>
      <!-- AI assistant message (Markdown) -->
      <Bubble
        v-if="message.role === 'assistant' && !message.isError"
        variant="ghost"
      >
        <BubbleContent>
          <MarkdownRenderer :content="message.content" />
          <div
            v-if="message.file"
            class="mt-2 flex items-center gap-2 border-t pt-2"
          >
            <FileIcon class="h-4 w-4 text-muted-foreground" />
            <span class="max-w-[200px] truncate text-xs text-muted-foreground">
              {{ message.file.name }}
            </span>
            <Button
              variant="link"
              size="sm"
              class="h-auto p-0 text-xs"
              @click="downloadFile(message.file!)"
            >
              <Download class="mr-0.5 h-3 w-3" />
              {{ t('chat.downloadFile') }}
            </Button>
          </div>
        </BubbleContent>
      </Bubble>
      <!-- User message (plain text) -->
      <Bubble v-else-if="!message.isError" variant="default">
        <BubbleContent class="whitespace-pre-wrap">
          {{ message.content }}
        </BubbleContent>
      </Bubble>
      <!-- Error message -->
      <Bubble v-if="message.isError" variant="destructive">
        <BubbleContent>
          <div class="whitespace-pre-wrap">{{ message.content }}</div>
          <div
            v-if="message.partialContent"
            class="mt-2 border-t pt-1 text-xs opacity-70"
          >
            {{ t('chat.errorPartial') }}: {{ message.partialContent }}
          </div>
        </BubbleContent>
      </Bubble>
      <!-- Footer actions -->
      <MessageFooter v-if="(showCopy && message.role === 'assistant' && !message.isError) || (showRetry && message.isError)">
        <Button
          v-if="showCopy && message.role === 'assistant' && !message.isError"
          variant="ghost"
          size="icon-sm"
          :title="copied ? '已复制' : '复制消息'"
          :aria-label="copied ? '已复制' : '复制消息'"
          @click="handleCopy"
        >
          <Check v-if="copied" class="h-3.5 w-3.5 text-green-500" />
          <Copy v-else class="h-3.5 w-3.5" />
        </Button>
        <Button
          v-if="showRetry && message.isError"
          variant="ghost"
          size="icon-sm"
          :title="t('chat.errorRetry')"
          :aria-label="t('chat.errorRetry')"
          @click="handleRetry"
        >
          <RefreshCw class="h-3.5 w-3.5" />
        </Button>
      </MessageFooter>
    </MessageContent>
  </Message>
</template>
