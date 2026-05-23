<script setup lang="ts">
import { onMounted } from 'vue'
import { useAgentStore } from '../../stores/agent'
import AgentCard from '../../components/AgentCard.vue'

const agentStore = useAgentStore()

onMounted(() => {
  agentStore.fetchPlazaAgents()
})
</script>

<template>
  <n-space vertical size="large">
    <!-- Header -->
    <div>
      <n-h3 style="margin: 0;">Agent 广场</n-h3>
      <n-p style="margin: 4px 0 0 0; font-size: 13px; color: #888;">
        发现并使用其他用户创建的 Agent
      </n-p>
    </div>

    <!-- Loading -->
    <n-spin v-if="agentStore.loading" />

    <!-- Error -->
    <n-result
      v-else-if="agentStore.error"
      status="error"
      title="加载失败"
      :description="agentStore.error"
    >
      <template #footer>
        <n-button @click="agentStore.fetchPlazaAgents()">重试</n-button>
      </template>
    </n-result>

    <!-- Empty -->
    <n-result
      v-else-if="agentStore.agents.length === 0"
      status="info"
      title="Agent 广场空空如也"
      description="目前还没有其他用户创建 Agent，稍后再来看看吧"
    />

    <!-- Agent Grid (not editable, click navigates to chat) -->
    <n-grid v-else :cols="2" :x-gap="16" :y-gap="16">
      <n-gi v-for="agent in agentStore.agents" :key="agent.id">
        <AgentCard :agent="agent" :editable="false" />
      </n-gi>
    </n-grid>
  </n-space>
</template>
