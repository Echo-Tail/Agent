<script setup lang="ts">
import { ref, nextTick, watch, onMounted, onUnmounted } from 'vue'
import { getUnifiedMembersApi } from '@/api/group'
import AgentIcon from '@/components/AgentIcon.vue'

const props = defineProps<{
  groupId: number
  modelValue: string
  currentUserId?: number
  /** 递增此值触发成员列表刷新 */
  refreshKey?: number
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'keydown', event: KeyboardEvent): void
}>()

const textareaRef = ref<HTMLTextAreaElement | null>(null)
const showMentions = ref(false)
const mentionSearch = ref('')
const mentionIndex = ref(0)

interface MentionItem {
  memberType: 'USER' | 'AGENT'
  refId: number
  name: string
  avatar?: string
  icon?: string
}

const mentionItems = ref<MentionItem[]>([])
const filteredItems = ref<MentionItem[]>([])

async function loadItems() {
  try {
    const list = await getUnifiedMembersApi(props.groupId)
    mentionItems.value = list.filter(m => !(m.memberType === 'USER' && m.refId === props.currentUserId))
  } catch {
    mentionItems.value = []
  }
}

// 加载群内统一成员列表（组件挂载时预加载），排除自己
onMounted(async () => {
  await loadItems()
  nextTick(autoResize)
})

// 成员刷新标记变化时重新加载
watch(() => props.refreshKey, () => {
  loadItems()
})

// 输入内容变化时自动调整高度
watch(() => props.modelValue, () => nextTick(autoResize))

function autoResize() {
  const ta = textareaRef.value
  if (!ta) return
  ta.style.height = 'auto'
  ta.style.height = ta.scrollHeight + 'px'
}

// 检测光标前的 @ 文本
function detectMention(text: string, cursorPos: number): { active: boolean; searchText: string } {
  const beforeCursor = text.slice(0, cursorPos)
  const atIndex = beforeCursor.lastIndexOf('@')
  if (atIndex === -1) return { active: false, searchText: '' }

  // 检查 @ 后是否有空格（有空格表示不是正在输入）
  const afterAt = beforeCursor.slice(atIndex + 1)
  if (afterAt.includes(' ') || afterAt.includes('\n')) return { active: false, searchText: '' }

  return { active: true, searchText: afterAt }
}

function handleInput() {
  const textarea = textareaRef.value
  if (!textarea) return
  const text = textarea.value
  const cursorPos = textarea.selectionStart

  const mention = detectMention(text, cursorPos)
  if (mention.active) {
    mentionSearch.value = mention.searchText
    filteredItems.value = mentionItems.value.filter(m =>
      m.name.toLowerCase().includes(mention.searchText.toLowerCase())
    )
    mentionIndex.value = 0
    showMentions.value = filteredItems.value.length > 0
  } else {
    showMentions.value = false
  }

  emit('update:modelValue', text)
}

function handleKeydown(e: KeyboardEvent) {
  if (showMentions.value) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      mentionIndex.value = (mentionIndex.value + 1) % filteredItems.value.length
      return
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      mentionIndex.value = (mentionIndex.value - 1 + filteredItems.value.length) % filteredItems.value.length
      return
    }
    if (e.key === 'Enter' || e.key === 'Tab') {
      if (filteredItems.value.length > 0) {
        e.preventDefault()
        selectMention(filteredItems.value[mentionIndex.value])
        return
      }
    }
    if (e.key === 'Escape') {
      showMentions.value = false
      return
    }
  }
  // 阻止冒泡：避免原生 keydown 事件冒泡到根元素再次触发父组件的 @keydown 监听
  e.stopPropagation()
  emit('keydown', e)
}

function selectMention(item: MentionItem) {
  const textarea = textareaRef.value
  if (!textarea) return

  const text = textarea.value
  const cursorPos = textarea.selectionStart
  const beforeCursor = text.slice(0, cursorPos)
  const atIndex = beforeCursor.lastIndexOf('@')
  const afterCursor = text.slice(cursorPos)

  // 替换 @text 为 @[名称](type:refId)
  const type = item.memberType === 'AGENT' ? 'agent' : 'user'
  const mentionText = `@[${item.name}](${type}:${item.refId}) `
  const newText = beforeCursor.slice(0, atIndex) + mentionText + afterCursor
  emit('update:modelValue', newText)

  showMentions.value = false

  // 移动光标到插入文本后
  nextTick(() => {
    if (textareaRef.value) {
      const pos = atIndex + mentionText.length
      textareaRef.value.setSelectionRange(pos, pos)
      textareaRef.value.focus()
    }
  })
}

function handleBlur() {
  // 延迟关闭，让点击选项先触发
  setTimeout(() => { showMentions.value = false }, 200)
}

function handleClickOutside(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (!target.closest('.mention-container')) {
    showMentions.value = false
  }
}

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <div class="relative flex-1 mention-container">
    <textarea
      id="group-chat-input"
      name="group-chat-input"
      ref="textareaRef"
      :value="modelValue"
      rows="1"
      class="flex min-h-11 w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 resize-none overflow-hidden"
      :placeholder="'发送消息...'"
      @input="handleInput"
      @keydown="handleKeydown"
      @blur="handleBlur"
    />

    <!-- @提及 下拉 -->
    <div
      v-if="showMentions && filteredItems.length > 0"
      class="absolute bottom-full left-0 mb-1 w-72 max-h-48 overflow-y-auto rounded-md border bg-popover text-popover-foreground shadow-md z-50"
      @mousedown.prevent
    >
      <div class="p-1">
        <div class="px-2 py-1.5 text-xs text-muted-foreground">选择成员或 Agent</div>
        <button
          v-for="(item, idx) in filteredItems"
          :key="item.memberType + '-' + item.refId"
          :class="['flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-sm cursor-pointer', idx === mentionIndex ? 'bg-accent text-accent-foreground' : 'hover:bg-accent/50']"
          @click="selectMention(item)"
          @mouseenter="mentionIndex = idx"
        >
          <div v-if="item.memberType === 'AGENT'" class="h-6 w-6 rounded-full overflow-hidden bg-primary/10 flex items-center justify-center shrink-0">
            <AgentIcon :icon="item.icon" :avatar="item.avatar" class="h-4 w-4" />
          </div>
          <div v-else class="h-6 w-6 rounded-full overflow-hidden bg-muted flex items-center justify-center shrink-0">
            <span class="text-xs font-medium">{{ item.name.charAt(0) }}</span>
          </div>
          <div class="flex-1 min-w-0">
            <span class="truncate">{{ item.name }}</span>
          </div>
          <span class="text-xs text-muted-foreground shrink-0">{{ item.memberType === 'AGENT' ? 'Agent' : '成员' }}</span>
        </button>
      </div>
    </div>
  </div>
</template>
