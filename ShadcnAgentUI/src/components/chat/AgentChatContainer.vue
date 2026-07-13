<script setup lang="ts">
/**
 * AgentChatContainer.vue — MessageScroller 封装
 *
 * 统一管理聊天消息的自动滚动、锚定、流式跟随和滚动按钮。
 * Props 向下透传给 MessageScrollerProvider。
 */
import type { HTMLAttributes } from 'vue'
import {
  MessageScrollerProvider,
  MessageScroller,
  MessageScrollerViewport,
  MessageScrollerContent,
  MessageScrollerButton,
} from '@/components/ui/message-scroller'
import type { MessageScrollerDefaultScrollPosition } from '@/components/ui/message-scroller'

interface Props {
  autoScroll?: boolean
  defaultScrollPosition?: MessageScrollerDefaultScrollPosition
  class?: HTMLAttributes['class']
  viewportClass?: HTMLAttributes['class']
}

const props = withDefaults(defineProps<Props>(), {
  autoScroll: true,
  defaultScrollPosition: 'end',
})
</script>

<template>
  <MessageScrollerProvider :auto-scroll="autoScroll" :default-scroll-position="defaultScrollPosition">
    <MessageScroller :class="props.class">
      <MessageScrollerViewport :class="viewportClass">
        <MessageScrollerContent>
          <slot />
        </MessageScrollerContent>
      </MessageScrollerViewport>
      <MessageScrollerButton direction="end" />
    </MessageScroller>
  </MessageScrollerProvider>
</template>
