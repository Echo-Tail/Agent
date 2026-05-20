<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { getAgentApi, createAgentApi, updateAgentApi } from '../../api/agent'
import { listModelsApi } from '../../api/model'
import { validate, usernameRules } from '../../utils/validation'
import type { AgentCreateRequest } from '../../types/agent'
import type { AiModel } from '../../types/api'

const route = useRoute()
const router = useRouter()
const message = useMessage()

const isEdit = computed(() => !!route.params.id)
const agentId = computed(() => Number(route.params.id))

const loading = ref(false)
const fetching = ref(false)

// Form fields
const name = ref('')
const icon = ref('bi-robot')
const description = ref('')
const systemPrompt = ref('')
const greeting = ref('')
const tags = ref<string[]>([])
const status = ref<'active' | 'disabled'>('active')
const models = ref<AiModel[]>([])
const selectedModelId = ref<number | null>(null)
const availableIcons = [
  { value: 'bi-robot', label: '机器人', path: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z' },
  { value: 'bi-chat-dots', label: '对话', path: 'M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H5.17L4 17.17V4h16v12zM7 9h2v2H7zm4 0h2v2h-2zm4 0h2v2h-2z' },
  { value: 'bi-search', label: '搜索', path: 'M15.5 14h-.79l-.28-.27A6.471 6.471 0 0016 9.5 6.5 6.5 0 109.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z' },
  { value: 'bi-cart', label: '电商', path: 'M7 18c-1.1 0-1.99.9-1.99 2S5.9 22 7 22s2-.9 2-2-.9-2-2-2zm10 0c-1.1 0-1.99.9-1.99 2S15.9 22 17 22s2-.9 2-2-.9-2-2-2zM7.17 14.75l.03-.12.9-1.63h7.45c.75 0 1.41-.41 1.75-1.03l3.86-7.01L19.42 4h-.01l-1.1 2-2.76 5H8.53l-.13-.27L6.16 6l-.95-2-.94-2H1v2h2l3.6 7.59-1.35 2.45c-.16.28-.25.61-.25.96 0 1.1.9 2 2 2h12v-2H7.42c-.14 0-.25-.11-.25-.25z' },
  { value: 'bi-gear', label: '工具', path: 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 00.12-.61l-1.92-3.32a.488.488 0 00-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 00-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.07.62-.07.94s.02.64.07.94l-2.03 1.58a.49.49 0 00-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z' },
  { value: 'bi-brain', label: '智能', path: 'M12 2C7.58 2 4 3.12 4 6.5c0 1.46.63 2.77 1.68 3.74-.26.4-.46.78-.56 1.13-.49 1.7.88 3.13 2.88 3.13.2 0 .4-.02.6-.05.9.86 2.07 1.35 3.4 1.35s2.5-.49 3.4-1.35c.2.03.4.05.6.05 2 0 3.37-1.43 2.88-3.13-.1-.35-.3-.73-.56-1.13C19.37 9.27 20 7.96 20 6.5 20 3.12 16.42 2 12 2zm0 10c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm-6 4c-.55 0-1 .45-1 1v1H4c-.55 0-1 .45-1 1s.45 1 1 1h1v1c0 .55.45 1 1 1s1-.45 1-1v-1h1c.55 0 1-.45 1-1s-.45-1-1-1H7v-1c0-.55-.45-1-1-1zm12 0c-.55 0-1 .45-1 1v1h-1c-.55 0-1 .45-1 1s.45 1 1 1h1v1c0 .55.45 1 1 1s1-.45 1-1v-1h1c.55 0 1-.45 1-1s-.45-1-1-1h-1v-1c0-.55-.45-1-1-1z' },
  { value: 'bi-tools', label: '运维', path: 'M22.7 19l-9.1-9.1c.9-2.3.4-5-1.5-6.9-2-2-5-2.4-7.4-1.3L9 6 6 9 1.6 4.7C.4 7.1.9 10.1 2.9 12.1c1.9 1.9 4.6 2.4 6.9 1.5l9.1 9.1c.4.4 1 .4 1.4 0l2.3-2.3c.5-.4.5-1.1.1-1.4z' },
]

const modelOptions = computed(() => {
  const grouped = new Map<string, { label: string; value: number }[]>()
  for (const m of models.value) {
    if (!grouped.has(m.provider)) {
      grouped.set(m.provider, [])
    }
    grouped.get(m.provider)!.push({ label: m.name, value: m.id })
  }
  return Array.from(grouped.entries()).map(([provider, children]) => ({
    type: 'group' as const,
    label: provider,
    key: provider,
    children,
  }))
})

async function fetchAgent() {
  if (!isEdit.value) return
  fetching.value = true
  try {
    const [res, modelRes] = await Promise.all([
      getAgentApi(agentId.value),
      listModelsApi(),
    ])
    if (modelRes.data.code === 200) {
      models.value = modelRes.data.data ?? []
    }
    const body = res.data
    if (body.code === 200 && body.data) {
      const a = body.data
      name.value = a.name
      icon.value = a.icon || 'bi-robot'
      description.value = a.description || ''
      systemPrompt.value = a.systemPrompt || ''
      greeting.value = a.greeting || ''
      tags.value = a.tags || []
      status.value = a.status || 'active'
      selectedModelId.value = a.modelId ?? null
    } else {
      message.error('Agent 不存在')
      router.push({ name: 'AgentList' })
    }
  } catch {
    message.error('加载失败')
    router.push({ name: 'AgentList' })
  } finally {
    fetching.value = false
  }
}

async function loadModels() {
  const res = await listModelsApi()
  if (res.data.code === 200) {
    models.value = res.data.data ?? []
  }
}

onMounted(() => {
  if (isEdit.value) {
    fetchAgent()
  } else {
    loadModels()
  }
})

async function handleSubmit() {
  const nameErr = validate(name.value, usernameRules)
  if (nameErr) {
    message.warning(nameErr)
    return
  }

  if (!selectedModelId.value) {
    message.warning('请选择一个模型')
    return
  }

  loading.value = true
  try {
    const payload: AgentCreateRequest = {
      name: name.value,
      icon: icon.value,
      description: description.value || undefined,
      systemPrompt: systemPrompt.value || undefined,
      greeting: greeting.value || undefined,
      tags: tags.value.length ? tags.value : undefined,
      modelId: selectedModelId.value ?? undefined,
    }

    if (isEdit.value) {
      const res = await updateAgentApi(agentId.value, { ...payload, status: status.value })
      if (res.data.code === 200) {
        message.success('更新成功')
        router.push({ name: 'AgentList' })
      } else {
        message.error(res.data.message || '更新失败')
      }
    } else {
      const res = await createAgentApi(payload)
      if (res.data.code === 200) {
        message.success('创建成功')
        router.push({ name: 'AgentList' })
      } else {
        message.error(res.data.message || '创建失败')
      }
    }
  } catch {
    message.error('网络异常')
  } finally {
    loading.value = false
  }
}

function handleTagsChange(v: string[]) {
  tags.value = v
}
</script>

<template>
  <n-spin :show="fetching">
    <n-space vertical size="large" style="max-width: 720px;">
      <n-h3 style="margin: 0;">{{ isEdit ? '编辑 Agent' : '创建 Agent' }}</n-h3>

      <n-card :bordered="true">
        <n-form @submit.prevent="handleSubmit">
          <!-- Name -->
          <n-form-item label="名称" required>
            <n-input
              v-model:value="name"
              placeholder="2-30 个字符"
              :disabled="loading"
              :maxlength="30"
              show-count
            />
          </n-form-item>

          <!-- Icon -->
          <n-form-item label="图标">
            <n-radio-group v-model:value="icon">
              <n-space>
                <n-radio
                  v-for="item in availableIcons"
                  :key="item.value"
                  :value="item.value"
                  :label="item.label"
                >
                  <template #default>
                    <n-avatar :size="36" round color="#C8815F">
                      <n-icon size="18" color="#fff">
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                          <path :d="item.path" />
                        </svg>
                      </n-icon>
                    </n-avatar>
                    <span style="margin-left: 8px;">{{ item.label }}</span>
                  </template>
                </n-radio>
              </n-space>
            </n-radio-group>
          </n-form-item>

          <!-- Description -->
          <n-form-item label="描述">
            <n-input
              v-model:value="description"
              type="textarea"
              placeholder="简要描述这个 Agent 的功能"
              :disabled="loading"
              :maxlength="500"
              show-count
              :rows="3"
            />
          </n-form-item>

          <!-- Greeting -->
          <n-form-item label="欢迎语">
            <n-input
              v-model:value="greeting"
              placeholder="首次对话时的欢迎语"
              :disabled="loading"
              :maxlength="200"
            />
          </n-form-item>

          <!-- System Prompt -->
          <n-form-item label="系统提示词">
            <n-input
              v-model:value="systemPrompt"
              type="textarea"
              placeholder="定义 Agent 的角色和行为，支持 Markdown 格式"
              :disabled="loading"
              :rows="6"
            />
          </n-form-item>

          <!-- Tags -->
          <n-form-item label="标签">
            <n-dynamic-tags
              :value="tags"
              @update:value="handleTagsChange"
              :max="10"
              :disabled="loading"
            />
          </n-form-item>

          <!-- Model -->
          <n-form-item label="模型" required>
            <n-select
              v-model:value="selectedModelId"
              :options="modelOptions"
              :disabled="loading || models.length === 0"
              :placeholder="models.length === 0 ? '暂无可用模型，请先配置' : '选择一个模型（可在对话时切换）'"
              clearable
              filterable
            />
            <template v-if="models.length === 0" #feedback>
              暂无可用模型，请先在<router-link :to="{ name: 'ModelManage' }" style="text-decoration: underline;">模型管理</router-link>中配置
            </template>
          </n-form-item>

          <!-- Status (edit only) -->
          <n-form-item v-if="isEdit" label="状态">
            <n-switch v-model:value="status" :checked-value="'active'" :unchecked-value="'disabled'">
              <template #checked>启用</template>
              <template #unchecked>停用</template>
            </n-switch>
          </n-form-item>

          <!-- Submit -->
          <div style="display: flex; gap: 12px; justify-content: flex-end;">
            <n-button @click="router.push({ name: 'AgentList' })" :disabled="loading">
              取消
            </n-button>
            <n-button type="primary" attr-type="submit" :loading="loading">
              {{ isEdit ? '保存更改' : '创建' }}
            </n-button>
          </div>
        </n-form>
      </n-card>
    </n-space>
  </n-spin>
</template>
