<script setup lang="ts">
/**
 * AgentFilePreview.vue — 附件预览
 *
 * 使用 shadcn-vue Attachment 展示待上传文件的预览列表。
 * 支持多种状态：idle（就绪）、uploading（上传中）、error（失败）。
 */
import type { HTMLAttributes } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Attachment,
  AttachmentContent,
  AttachmentDescription,
  AttachmentGroup,
  AttachmentMedia,
  AttachmentTitle,
  AttachmentAction,
  AttachmentActions,
} from '@/components/ui/attachment'
import { File as FileIcon, X, RefreshCw } from 'lucide-vue-next'

const { t } = useI18n()

export interface FileAttachment {
  file: File
  record?: { id: number; originalName: string; fileSize: number; mimeType: string }
  error?: string
}

interface Props {
  attachments: FileAttachment[]
  class?: HTMLAttributes['class']
}

const props = defineProps<Props>()

const emit = defineEmits<{
  remove: [index: number]
  retry: [index: number]
}>()

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(0) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function attachmentState(att: FileAttachment) {
  if (att.error) return 'error' as const
  if (!att.record) return 'uploading' as const
  return 'done' as const
}
</script>

<template>
  <AttachmentGroup v-if="attachments.length > 0" :class="props.class">
    <Attachment
      v-for="(att, idx) in attachments"
      :key="att.file.name + idx"
      :state="attachmentState(att)"
      size="sm"
    >
      <AttachmentMedia>
        <FileIcon />
      </AttachmentMedia>
      <AttachmentContent>
        <AttachmentTitle>{{ att.file.name }}</AttachmentTitle>
        <AttachmentDescription>
          {{ formatFileSize(att.file.size) }}
          <template v-if="att.error"> · {{ t('common.error') }}</template>
          <template v-else-if="!att.record"> · {{ t('chat.uploading') }}</template>
        </AttachmentDescription>
      </AttachmentContent>
      <AttachmentActions>
        <AttachmentAction
          v-if="att.error"
          :aria-label="t('chat.retryUpload')"
          @click="emit('retry', idx)"
        >
          <RefreshCw />
        </AttachmentAction>
        <AttachmentAction
          :aria-label="t('common.delete')"
          @click="emit('remove', idx)"
        >
          <X />
        </AttachmentAction>
      </AttachmentActions>
    </Attachment>
  </AttachmentGroup>
</template>
