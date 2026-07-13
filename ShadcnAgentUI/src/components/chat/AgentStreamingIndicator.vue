<script setup lang="ts">
/**
 * AgentStreamingIndicator.vue — 流式/思考指示器
 *
 * 使用 shadcn-vue Marker 展示 AI 正在思考/工具调用的状态。
 * 包含 tool status badges、Markdown 流式文本和闪烁光标。
 */
import type { HTMLAttributes } from 'vue'
import type { CurrentToolStatus } from '@/stores/chat'
import { Badge } from '@/components/ui/badge'
import { Marker, MarkerContent, MarkerIcon } from '@/components/ui/marker'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { Loader2 } from 'lucide-vue-next'

interface Props {
  streamingText: string
  currentToolStatuses: CurrentToolStatus[]
  class?: HTMLAttributes['class']
}

const props = withDefaults(defineProps<Props>(), {
  streamingText: '',
  currentToolStatuses: () => [],
})

function toolBadgeVariant(status: CurrentToolStatus['status']) {
  switch (status) {
    case 'running': return 'secondary' as const
    case 'done':    return 'default' as const
    case 'degraded': return 'outline' as const
    case 'timeout':  return 'destructive' as const
    case 'empty':    return 'ghost' as const
  }
}
</script>

<template>
  <Marker role="status" class="w-full">
    <MarkerIcon>
      <Loader2 class="h-4 w-4 animate-spin" />
    </MarkerIcon>
    <MarkerContent class="flex flex-col gap-2">
      <!-- Tool status badges -->
      <div
        v-if="currentToolStatuses.length"
        class="flex flex-wrap gap-2"
      >
        <Badge
          v-for="tool in currentToolStatuses"
          :key="tool.tool"
          :variant="toolBadgeVariant(tool.status)"
          class="text-xs"
          :title="tool.message"
        >
          {{ tool.message }}
        </Badge>
      </div>
      <!-- Streaming markdown text -->
      <MarkdownRenderer
        v-if="streamingText"
        :content="streamingText"
      />
      <!-- Blinking cursor -->
      <span
        class="inline-block h-4 w-2 animate-pulse bg-primary ml-0.5"
      />
    </MarkerContent>
  </Marker>
</template>
