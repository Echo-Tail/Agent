<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useDialog, useMessage } from 'naive-ui'
import { useAgentStore } from '../../stores/agent'
import { deleteAgentApi } from '../../api/agent'
import AgentCard from '../../components/AgentCard.vue'
import type { Agent } from '../../types/agent'

const router = useRouter()
const dialog = useDialog()
const message = useMessage()
const agentStore = useAgentStore()

onMounted(() => {
  agentStore.fetchAgents()
})

function handleDelete(agent: Agent) {
  dialog.warning({
    title: '确认删除',
    content: `确定要删除 Agent「${agent.name}」吗？此操作不可撤销。`,
    positiveText: '删除',
    negativeText: '取消',
    positiveButtonProps: { type: 'error' },
    onPositiveClick: async () => {
      try {
        const res = await deleteAgentApi(agent.id)
        if (res.data.code === 200) {
          message.success('删除成功')
          agentStore.fetchAgents()
        } else {
          message.error(res.data.message || '删除失败')
        }
      } catch {
        message.error('网络异常')
      }
    },
  })
}
</script>

<template>
  <n-space vertical size="large">
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
      description="还没有创建任何 Agent"
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
        <AgentCard :agent="agent" editable @delete="handleDelete" />
      </n-gi>
    </n-grid>
  </n-space>
</template>
