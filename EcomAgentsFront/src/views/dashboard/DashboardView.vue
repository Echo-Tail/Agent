<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAgentStore } from '../../stores/agent'
import AgentCard from '../../components/AgentCard.vue'

const router = useRouter()
const agentStore = useAgentStore()

onMounted(() => {
  agentStore.fetchAgents()
})
</script>

<template>
  <n-space vertical size="large">
    <!-- Welcome -->
    <n-card :bordered="false" style="background: linear-gradient(135deg, #C8815F 0%, #B0755A 100%);">
      <n-h2 style="color: #fff; margin: 0 0 4px;">欢迎使用 EcomAgents</n-h2>
      <n-p style="color: rgba(255,255,255,0.85); margin: 0;">
        企业电商智能体管理平台
      </n-p>
    </n-card>

    <!-- Stats -->
    <n-grid :cols="3" :x-gap="16">
      <n-gi>
        <n-card :bordered="true">
          <n-statistic label="智能体总数" :value="agentStore.summary.total">
            <template #prefix>
              <n-icon color="#C8815F">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/>
                </svg>
              </n-icon>
            </template>
          </n-statistic>
        </n-card>
      </n-gi>
      <n-gi>
        <n-card :bordered="true">
          <n-statistic label="已启用" :value="agentStore.summary.active">
            <template #prefix>
              <n-icon color="#8BA888">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/>
                </svg>
              </n-icon>
            </template>
          </n-statistic>
        </n-card>
      </n-gi>
      <n-gi>
        <n-card :bordered="true">
          <n-statistic label="已停用" :value="agentStore.summary.disabled">
            <template #prefix>
              <n-icon color="#f59e0b">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
                </svg>
              </n-icon>
            </template>
          </n-statistic>
        </n-card>
      </n-gi>
    </n-grid>

    <!-- Toolbar -->
    <div style="display: flex; justify-content: space-between; align-items: center;">
      <n-h3 style="margin: 0;">我的 Agent</n-h3>
      <n-button type="primary" @click="router.push({ name: 'AgentCreate' })">
        <template #icon>
          <n-icon>
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
              <path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>
            </svg>
          </n-icon>
        </template>
        创建 Agent
      </n-button>
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
        <n-button @click="agentStore.fetchAgents()">重试</n-button>
      </template>
    </n-result>

    <!-- Empty -->
    <n-result
      v-else-if="agentStore.agents.length === 0"
      status="info"
      title="暂无 Agent"
      description="还没有创建任何 Agent，点击上方按钮开始创建"
    >
      <template #footer>
        <n-button type="primary" @click="router.push({ name: 'AgentCreate' })">
          创建第一个 Agent
        </n-button>
      </template>
    </n-result>

    <!-- Agent Grid -->
    <n-grid v-else :cols="2" :x-gap="16" :y-gap="16">
      <n-gi v-for="agent in agentStore.agents" :key="agent.id">
        <AgentCard :agent="agent" />
      </n-gi>
    </n-grid>
  </n-space>
</template>
