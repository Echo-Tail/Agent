import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { listAgentsApi, listAgentsByScopeApi, getSystemAgentApi } from '@/api/agent'
import type { Agent, AgentSummary } from '@/types/agent'
import i18n from '@/locales'

const { t } = i18n.global

export const useAgentStore = defineStore('agent', () => {
  const agents = ref<Agent[]>([])
  const myAgents = ref<Agent[]>([])
  const plazaAgents = ref<Agent[]>([])
  const systemAgent = ref<Agent | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const summary = computed<AgentSummary>(() => ({
    total: agents.value.length,
    active: agents.value.filter((a) => a.status === 'active').length,
    disabled: agents.value.filter((a) => a.status === 'disabled').length,
  }))

  async function fetchAgents(scope?: string) {
    loading.value = true
    error.value = null
    try {
      const data = scope ? await listAgentsByScopeApi(scope) : await listAgentsApi()
      agents.value = (data ?? []).filter(a => !a.isSystem)
    } catch {
      error.value = t('error.fetchFailed', { entity: 'Agent 列表' })
    } finally {
      loading.value = false
    }
  }

  async function fetchMyAgents() {
    loading.value = true
    error.value = null
    try {
      const data = await listAgentsByScopeApi('my')
      myAgents.value = (data ?? []).filter(a => !a.isSystem)
    } catch {
      error.value = t('error.fetchFailed', { entity: 'Agent 列表' })
    } finally {
      loading.value = false
    }
  }

  async function fetchPlazaAgents() {
    loading.value = true
    error.value = null
    try {
      const data = await listAgentsByScopeApi('plaza')
      plazaAgents.value = (data ?? []).filter(a => !a.isSystem)
    } catch {
      error.value = t('error.fetchFailed', { entity: 'Agent 列表' })
    } finally {
      loading.value = false
    }
  }

  async function fetchSystemAgent() {
    try {
      const data = await getSystemAgentApi()
      systemAgent.value = data ?? null
      return systemAgent.value
    } catch {
      systemAgent.value = null
      return null
    }
  }

  return {
    agents, myAgents, plazaAgents, systemAgent, loading, error, summary,
    fetchAgents, fetchMyAgents, fetchPlazaAgents, fetchSystemAgent,
  }
})
