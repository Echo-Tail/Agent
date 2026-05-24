import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import axios from 'axios'
import { listAgentsApi, listAgentsByScopeApi } from '../api/agent'
import type { Agent, AgentSummary } from '../types/agent'

export const useAgentStore = defineStore('agent', () => {
  const agents = ref<Agent[]>([])
  const myAgents = ref<Agent[]>([])
  const plazaAgents = ref<Agent[]>([])
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
      const res = scope ? await listAgentsByScopeApi(scope) : await listAgentsApi()
      const body = res.data
      if (body.code === 200) {
        agents.value = (body.data ?? []).filter(a => !a.isSystem)
      } else {
        error.value = body.message || '获取 Agent 列表失败'
      }
    } catch (e: unknown) {
      if (axios.isAxiosError(e) && (e.response?.status === 401 || e.response?.status === 403)) {
        error.value = '登录已过期，请重新登录'
      } else {
        error.value = e instanceof Error ? e.message : '网络异常'
      }
    } finally {
      loading.value = false
    }
  }

  async function fetchMyAgents() {
    loading.value = true
    error.value = null
    try {
      const res = await listAgentsByScopeApi('my')
      const body = res.data
      if (body.code === 200) {
        myAgents.value = (body.data ?? []).filter(a => !a.isSystem)
      } else {
        error.value = body.message || '获取 Agent 列表失败'
      }
    } catch (e: unknown) {
      if (axios.isAxiosError(e) && (e.response?.status === 401 || e.response?.status === 403)) {
        error.value = '登录已过期，请重新登录'
      } else {
        error.value = e instanceof Error ? e.message : '网络异常'
      }
    } finally {
      loading.value = false
    }
  }

  async function fetchPlazaAgents() {
    loading.value = true
    error.value = null
    try {
      const res = await listAgentsByScopeApi('plaza')
      const body = res.data
      if (body.code === 200) {
        plazaAgents.value = (body.data ?? []).filter(a => !a.isSystem)
      } else {
        error.value = body.message || '获取 Agent 列表失败'
      }
    } catch (e: unknown) {
      if (axios.isAxiosError(e) && (e.response?.status === 401 || e.response?.status === 403)) {
        error.value = '登录已过期，请重新登录'
      } else {
        error.value = e instanceof Error ? e.message : '网络异常'
      }
    } finally {
      loading.value = false
    }
  }

  return {
    agents, myAgents, plazaAgents, loading, error, summary,
    fetchAgents, fetchMyAgents, fetchPlazaAgents,
  }
})
