import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { listAgentsApi } from '../api/agent'
import type { Agent, AgentSummary } from '../types/agent'

export const useAgentStore = defineStore('agent', () => {
  const agents = ref<Agent[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  const summary = computed<AgentSummary>(() => ({
    total: agents.value.length,
    active: agents.value.filter((a) => a.status === 'active').length,
    disabled: agents.value.filter((a) => a.status === 'disabled').length,
  }))

  async function fetchAgents() {
    loading.value = true
    error.value = null
    try {
      const res = await listAgentsApi()
      const body = res.data
      if (body.code === 200) {
        agents.value = (body.data ?? []).filter(a => !a.isSystem)
      } else {
        error.value = body.message || '获取 Agent 列表失败'
      }
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : '网络异常'
    } finally {
      loading.value = false
    }
  }

  return { agents, loading, error, summary, fetchAgents }
})
