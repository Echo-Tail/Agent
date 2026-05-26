<script setup lang="ts">
import { ref, nextTick, onUnmounted } from 'vue'
import { listGroupAgentsApi } from '@/api/group'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'

const props = defineProps<{
  groupId: number
  modelValue: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'keydown', event: KeyboardEvent): void
}>()

const textareaRef = ref<HTMLTextAreaElement | null>(null)
const showMentions = ref(false)
const mentionSearch = ref('')
const mentionIndex = ref(0)
const agents = ref<Array<{ agentId: number; agentName: string }>>([])
const filteredAgents = ref<Array<{ agentId: number; agentName: string }>>([])

// 加载群内 Agent 列表
let agentsLoaded = false
async function ensureAgentsLoaded() {
  if (agentsLoaded) return
  try {
    // 由于 API 只返回 GroupAgent（有 agentId），这里先简单显示
    const list = await listGroupAgentsApi(props.groupId)
    agents.value = list.map(a => ({ agentId: a.agentId, agentName: `Agent #${a.agentId}` }))
    agentsLoaded = true
  } catch {
    agents.value = []
  }
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
    ensureAgentsLoaded()
    mentionSearch.value = mention.searchText
    filteredAgents.value = agents.value.filter(a =>
      a.agentName.toLowerCase().includes(mention.searchText.toLowerCase())
    )
    mentionIndex.value = 0
    showMentions.value = filteredAgents.value.length > 0
  } else {
    showMentions.value = false
  }

  emit('update:modelValue', text)
}

function handleKeydown(e: KeyboardEvent) {
  if (showMentions.value) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      mentionIndex.value = (mentionIndex.value + 1) % filteredAgents.value.length
      return
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      mentionIndex.value = (mentionIndex.value - 1 + filteredAgents.value.length) % filteredAgents.value.length
      return
    }
    if (e.key === 'Enter' || e.key === 'Tab') {
      if (filteredAgents.value.length > 0) {
        e.preventDefault()
        selectAgent(filteredAgents.value[mentionIndex.value])
        return
      }
    }
    if (e.key === 'Escape') {
      showMentions.value = false
      return
    }
  }
  emit('keydown', e)
}

function selectAgent(agent: { agentId: number; agentName: string }) {
  const textarea = textareaRef.value
  if (!textarea) return

  const text = textarea.value
  const cursorPos = textarea.selectionStart
  const beforeCursor = text.slice(0, cursorPos)
  const atIndex = beforeCursor.lastIndexOf('@')
  const afterCursor = text.slice(cursorPos)

  // 替换 @text 为 @[Agent名称](agent:id)
  const mentionText = `@[${agent.agentName}](agent:${agent.agentId}) `
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
      ref="textareaRef"
      :value="modelValue"
      class="flex min-h-[44px] w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 resize-none max-h-[120px]"
      :placeholder="'发送消息...'"
      @input="handleInput"
      @keydown="handleKeydown"
      @blur="handleBlur"
    />

    <!-- @Agent 下拉 -->
    <div
      v-if="showMentions && filteredAgents.length > 0"
      class="absolute bottom-full left-0 mb-1 w-64 max-h-48 overflow-y-auto rounded-md border bg-popover text-popover-foreground shadow-md z-50"
      @mousedown.prevent
    >
      <div class="p-1">
        <div class="px-2 py-1.5 text-xs text-muted-foreground">选择 Agent</div>
        <button
          v-for="(agent, idx) in filteredAgents"
          :key="agent.agentId"
          :class="['flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-sm cursor-pointer', idx === mentionIndex ? 'bg-accent text-accent-foreground' : 'hover:bg-accent/50']"
          @click="selectAgent(agent)"
          @mouseenter="mentionIndex = idx"
        >
          <Avatar class="h-6 w-6">
            <AvatarFallback class="bg-primary text-primary-foreground text-xs">
              {{ agent.agentName.charAt(0) }}
            </AvatarFallback>
          </Avatar>
          <span>{{ agent.agentName }}</span>
        </button>
      </div>
    </div>
  </div>
</template>
