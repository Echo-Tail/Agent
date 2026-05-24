<script setup lang="ts">
import { ref, onMounted, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useChatStore } from '@/stores/chat'
import { useAgentStore } from '@/stores/agent'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Skeleton } from '@/components/ui/skeleton'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import {
  Send,
  Square,
  Bot,
  RefreshCw,
  Wrench,
} from 'lucide-vue-next'
import { toast } from 'sonner'

const { t } = useI18n()

const route = useRoute()
const chatStore = useChatStore()
const agentStore = useAgentStore()

const inputText = ref('')
const messagesEnd = ref<HTMLDivElement | null>(null)
const initializing = ref(true)

const selectedAgent = ref(agentStore.myAgents.find(a => a.id === Number(route.query.agentId)) ?? null)

onMounted(async () => {
  const agentId = Number(route.query.agentId)

  // Load agents if not loaded
  if (agentStore.myAgents.length === 0) {
    await agentStore.fetchMyAgents()
  }

  if (agentId) {
    selectedAgent.value = agentStore.myAgents.find(a => a.id === agentId) || null
    if (selectedAgent.value) {
      chatStore.switchToAgent(agentId)
      // Create a session if none exists
      try {
        await chatStore.createSession(agentId)
      } catch {
        toast.error(t('error.createSessionFailed'))
      }
    }
  } else {
    chatStore.switchToDirect()
  }
  initializing.value = false
})

watch(() => chatStore.messages.length, () => {
  nextTick(() => messagesEnd.value?.scrollIntoView({ behavior: 'smooth' }))
})

function renderMarkdown(text: string): string {
  const raw = marked.parse(text, { async: false }) as string
  return DOMPurify.sanitize(raw)
}

async function handleSend() {
  const content = inputText.value.trim()
  if (!content || chatStore.isStreaming) return

  if (!selectedAgent.value) {
    toast.error(t('agent.modelRequired'))
    return
  }

  inputText.value = ''

  if (!chatStore.activeSession) {
    try {
      await chatStore.createSession(selectedAgent.value.id)
    } catch {
      toast.error(t('error.createSessionFailed'))
      return
    }
  }

  await chatStore.sendMessage(selectedAgent.value.id, content)
}

function handleStop() {
  chatStore.stopStreaming()
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

function selectAgent(agentId: number) {
  const agent = agentStore.myAgents.find(a => a.id === agentId)
  if (agent) {
    selectedAgent.value = agent
    chatStore.switchToAgent(agentId)
    chatStore.createSession(agentId).catch(() => toast.error(t('error.createSessionFailed')))
  }
}

function newSession() {
  if (selectedAgent.value) {
    chatStore.createSession(selectedAgent.value.id).catch(() => toast.error(t('error.createSessionFailed')))
  }
}
</script>

<template>
  <div class="flex flex-col h-[calc(100vh-8rem)] max-w-4xl mx-auto">
    <!-- Toolbar -->
    <div class="flex items-center justify-between mb-4">
      <div class="flex items-center gap-2">
        <span v-if="selectedAgent" class="font-medium">{{ selectedAgent.name }}</span>
        <span v-else class="text-muted-foreground">{{ $t('chat.directChat') }}</span>
      </div>
      <div class="flex gap-2">
        <Button v-if="selectedAgent" variant="outline" size="sm" @click="newSession">
          <RefreshCw class="mr-1 h-3 w-3" /> {{ $t('chat.newChat') }}
        </Button>
      </div>
    </div>

    <!-- Messages -->
    <Card class="flex-1 overflow-hidden flex flex-col">
      <CardContent class="flex-1 overflow-y-auto p-4 space-y-4">
        <!-- Loading state -->
        <div v-if="initializing" class="space-y-4">
          <div class="flex gap-3"><Skeleton class="h-8 w-8 rounded-full" /><Skeleton class="h-16 flex-1 rounded-lg" /></div>
        </div>

        <!-- Empty state -->
        <div v-else-if="chatStore.messages.length === 0 && !chatStore.isStreaming" class="flex-1 flex items-center justify-center">
          <div class="text-center">
            <Bot class="mx-auto h-12 w-12 text-muted-foreground/50" />
            <h3 class="mt-4 text-lg font-semibold">{{ selectedAgent ? selectedAgent.name : $t('chat.startChat') }}</h3>
            <p class="text-sm text-muted-foreground mt-1">{{ selectedAgent?.greeting || $t('chat.startChat') }}</p>
          </div>
        </div>

        <!-- Messages -->
        <template v-else>
          <div v-for="(msg, idx) in chatStore.messages" :key="idx" :class="['flex gap-3', msg.role === 'user' ? 'justify-end' : '']">
            <Avatar v-if="msg.role === 'assistant'" class="h-8 w-8 mt-1">
              <AvatarFallback class="bg-primary text-primary-foreground text-xs">
                {{ selectedAgent?.name?.charAt(0) || 'A' }}
              </AvatarFallback>
            </Avatar>
            <div :class="['max-w-[80%] rounded-lg p-3 text-sm', msg.role === 'user' ? 'bg-primary text-primary-foreground' : 'bg-muted', msg.isError ? 'bg-destructive/10 text-destructive' : '']">
              <div v-if="msg.role === 'assistant' && !msg.isError" class="prose prose-sm max-w-none dark:prose-invert" v-html="renderMarkdown(msg.content)" />
              <div v-else class="whitespace-pre-wrap">{{ msg.content }}</div>
              <div v-if="msg.isError && msg.partialContent" class="mt-2 text-xs opacity-70 border-t pt-1">
                {{ $t('chat.errorPartial') }}: {{ msg.partialContent }}
              </div>
              <div v-if="msg.isError && !chatStore.isStreaming" class="mt-2">
                <Button variant="ghost" size="sm" class="h-6 text-xs" @click="chatStore.retryMessage(msg)">
                  <RefreshCw class="mr-1 h-3 w-3" /> {{ $t('chat.errorRetry') }}
                </Button>
              </div>
            </div>
            <Avatar v-if="msg.role === 'user'" class="h-8 w-8 mt-1">
              <AvatarFallback class="bg-muted text-xs">U</AvatarFallback>
            </Avatar>
          </div>

          <!-- Error message (not yet pushed to messages) -->
          <div v-if="chatStore.isStreaming" class="flex gap-3">
            <Avatar class="h-8 w-8 mt-1">
              <AvatarFallback class="bg-primary text-primary-foreground text-xs">
                {{ selectedAgent?.name?.charAt(0) || 'A' }}
              </AvatarFallback>
            </Avatar>
            <div class="bg-muted rounded-lg p-3 text-sm max-w-[80%]">
              <!-- Tool calls -->
              <div v-if="chatStore.currentToolCalls.length" class="flex flex-wrap gap-2 mb-2">
                <Badge v-for="tool in chatStore.currentToolCalls" :key="tool" variant="secondary" class="text-xs">
                  <Wrench class="mr-1 h-3 w-3" /> {{ tool }}
                </Badge>
              </div>
              <div class="prose prose-sm max-w-none dark:prose-invert" v-html="renderMarkdown(chatStore.streamingText)" />
              <span class="inline-block w-2 h-4 bg-primary animate-pulse ml-0.5" />
            </div>
          </div>

          <div ref="messagesEnd" />
        </template>
      </CardContent>
    </Card>

    <!-- Input -->
    <div class="mt-4 flex gap-2">
      <Textarea
        ref="textareaRef"
        v-model="inputText"
        placeholder="{{ $t('chat.inputPlaceholder') }}"
        :disabled="chatStore.isStreaming || !selectedAgent"
        class="min-h-[44px] max-h-[120px] resize-none"
        @keydown="handleKeydown"
      />
      <div class="flex flex-col gap-1">
        <Button
          v-if="!chatStore.isStreaming"
          size="icon"
          class="h-[44px] w-[44px]"
          :disabled="!inputText.trim() || !selectedAgent"
          @click="handleSend"
        >
          <Send class="h-4 w-4" />
        </Button>
        <Button
          v-else
          variant="destructive"
          size="icon"
          class="h-[44px] w-[44px]"
          @click="handleStop"
        >
          <Square class="h-4 w-4" />
        </Button>
      </div>
    </div>

    <!-- Agent selector when no agent selected -->
    <div v-if="!selectedAgent && !initializing" class="mt-2">
      <p class="text-sm text-muted-foreground mb-2">{{ $t('chat.selectAgent') }}</p>
      <div class="flex flex-wrap gap-2">
        <Button
          v-for="agent in agentStore.myAgents"
          :key="agent.id"
          variant="outline"
          size="sm"
          @click="selectAgent(agent.id)"
        >
          <Bot class="mr-1 h-3 w-3" /> {{ agent.name }}
        </Button>
      </div>
    </div>
  </div>
</template>
