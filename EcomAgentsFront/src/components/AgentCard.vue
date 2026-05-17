<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import type { Agent } from '../types/agent'

const props = defineProps<{
  agent: Agent
  editable?: boolean
  disableNav?: boolean
}>()

const emit = defineEmits<{
  delete: [agent: Agent]
}>()

const router = useRouter()

const iconMap: Record<string, string> = {
  'bi-robot': 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z',
  'bi-chat-dots': 'M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H5.17L4 17.17V4h16v12zM7 9h2v2H7zm4 0h2v2h-2zm4 0h2v2h-2z',
  'bi-gear': 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 00.12-.61l-1.92-3.32a.488.488 0 00-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 00-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.07.62-.07.94s.02.64.07.94l-2.03 1.58a.49.49 0 00-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z',
  'bi-search': 'M15.5 14h-.79l-.28-.27A6.471 6.471 0 0016 9.5 6.5 6.5 0 109.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z',
  'bi-cart': 'M7 18c-1.1 0-1.99.9-1.99 2S5.9 22 7 22s2-.9 2-2-.9-2-2-2zm10 0c-1.1 0-1.99.9-1.99 2S15.9 22 17 22s2-.9 2-2-.9-2-2-2zM7.17 14.75l.03-.12.9-1.63h7.45c.75 0 1.41-.41 1.75-1.03l3.86-7.01L19.42 4h-.01l-1.1 2-2.76 5H8.53l-.13-.27L6.16 6l-.95-2-.94-2H1v2h2l3.6 7.59-1.35 2.45c-.16.28-.25.61-.25.96 0 1.1.9 2 2 2h12v-2H7.42c-.14 0-.25-.11-.25-.25z',
  'bi-tools': 'M22.7 19l-9.1-9.1c.9-2.3.4-5-1.5-6.9-2-2-5-2.4-7.4-1.3L9 6 6 9 1.6 4.7C.4 7.1.9 10.1 2.9 12.1c1.9 1.9 4.6 2.4 6.9 1.5l9.1 9.1c.4.4 1 .4 1.4 0l2.3-2.3c.5-.4.5-1.1.1-1.4z',
  'bi-brain': 'M12 2C7.58 2 4 3.12 4 6.5c0 1.46.63 2.77 1.68 3.74-.26.4-.46.78-.56 1.13-.49 1.7.88 3.13 2.88 3.13.2 0 .4-.02.6-.05.9.86 2.07 1.35 3.4 1.35s2.5-.49 3.4-1.35c.2.03.4.05.6.05 2 0 3.37-1.43 2.88-3.13-.1-.35-.3-.73-.56-1.13C19.37 9.27 20 7.96 20 6.5 20 3.12 16.42 2 12 2zm0 10c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm-6 4c-.55 0-1 .45-1 1v1H4c-.55 0-1 .45-1 1s.45 1 1 1h1v1c0 .55.45 1 1 1s1-.45 1-1v-1h1c.55 0 1-.45 1-1s-.45-1-1-1H7v-1c0-.55-.45-1-1-1zm12 0c-.55 0-1 .45-1 1v1h-1c-.55 0-1 .45-1 1s.45 1 1 1h1v1c0 .55.45 1 1 1s1-.45 1-1v-1h1c.55 0 1-.45 1-1s-.45-1-1-1h-1v-1c0-.55-.45-1-1-1z',
}

const iconPath = computed(() => iconMap[props.agent.icon] || iconMap['bi-robot'])

function handleClick() {
  if (props.disableNav) return
  if (props.editable) {
    router.push({ name: 'AgentEdit', params: { id: props.agent.id } })
  } else {
    router.push({ name: 'Chat', query: { agentId: props.agent.id.toString() } })
  }
}

function handleEdit(e: MouseEvent) {
  e.stopPropagation()
  router.push({ name: 'AgentEdit', params: { id: props.agent.id } })
}

function handleDelete(e: MouseEvent) {
  e.stopPropagation()
  emit('delete', props.agent)
}
</script>

<template>
  <n-card
    class="agent-card"
    hoverable
    @click="handleClick"
    :title="undefined"
  >
    <div class="agent-card-inner">
      <n-avatar :size="48" round :color="agent.status === 'active' ? '#C8815F' : '#B5ADA5'">
        <n-icon size="24" color="#fff">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
            <path :d="iconPath" />
          </svg>
        </n-icon>
      </n-avatar>
      <div class="agent-info">
        <div class="agent-name-row">
          <span class="agent-name">{{ agent.name }}</span>
          <n-tag
            :type="agent.status === 'active' ? 'success' : 'default'"
            size="tiny"
            :bordered="false"
          >
            {{ agent.status === 'active' ? '启用' : '停用' }}
          </n-tag>
        </div>
        <n-ellipsis v-if="agent.description" :line-clamp="2" class="agent-desc">
          {{ agent.description }}
        </n-ellipsis>
        <div v-if="agent.tags && agent.tags.length" class="agent-tags">
          <n-tag
            v-for="tag in agent.tags"
            :key="tag"
            size="tiny"
            :bordered="false"
            round
            style="margin-right: 4px;"
          >
            {{ tag }}
          </n-tag>
        </div>
      </div>
      <div v-if="editable" class="card-actions" @click.stop>
        <n-button circle size="small" type="primary" quaternary @click="handleEdit">
          <template #icon>
            <n-icon>
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/>
              </svg>
            </n-icon>
          </template>
        </n-button>
        <n-button circle size="small" type="error" quaternary @click="handleDelete">
          <template #icon>
            <n-icon color="#ef4444">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
              </svg>
            </n-icon>
          </template>
        </n-button>
      </div>
    </div>
  </n-card>
</template>

<style scoped>
.agent-card {
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}
.agent-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.agent-card-inner {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.agent-info {
  flex: 1;
  min-width: 0;
}

.agent-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.agent-name {
  font-weight: 600;
  font-size: 15px;
}

.agent-desc {
  font-size: 13px;
  color: #888;
  margin-bottom: 6px;
}

.agent-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
}

.card-actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s;
}

.agent-card:hover .card-actions {
  opacity: 1;
}
</style>
