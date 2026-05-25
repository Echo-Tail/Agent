<script setup lang="ts">
import { h, ref, watch, onMounted } from 'vue'
import type { DataTableColumn, SelectOption } from 'naive-ui'
import { useMessage, useDialog, NButton } from 'naive-ui'
import { listModelsApi, createModelApi, updateModelApi, deleteModelApi, validateModelApi } from '../../api/model'
import type { AiModel } from '../../types/api'

const message = useMessage()
const dialog = useDialog()

const models = ref<AiModel[]>([])
const loading = ref(false)
const saving = ref(false)

const showModal = ref(false)
const editingModel = ref<Partial<AiModel>>({})
const isEditMode = ref(false)

const fetchingModels = ref(false)
const availableModelIds = ref<SelectOption[]>([])

const providerDefaults: Record<string, { label: string; apiUrl: string; apiType: string }> = {
  anthropic: { label: 'Anthropic', apiUrl: 'https://api.anthropic.com', apiType: 'anthropic' },
  openai: { label: 'OpenAI', apiUrl: 'https://api.openai.com', apiType: 'openai' },
  qwen: { label: '阿里百炼', apiUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', apiType: 'openai' },
  deepseek: { label: 'DeepSeek', apiUrl: 'https://api.deepseek.com', apiType: 'openai' },
  other: { label: '其它', apiUrl: '', apiType: 'openai' },
}

const providerOptions = Object.entries(providerDefaults).map(([value, config]) => ({
  label: config.label,
  value,
}))

const tokenOptions: SelectOption[] = [
  { label: '128K', value: 131072 },
  { label: '256K', value: 262144 },
  { label: '1M', value: 1048576 },
]

onMounted(fetchModels)

// 供应商切换时自动带出默认 API 地址和格式。
watch(() => editingModel.value.provider, (provider) => {
  if (!provider || isEditMode.value) return
  const defaults = providerDefaults[provider]
  editingModel.value.apiUrl = defaults?.apiUrl || ''
  editingModel.value.apiType = defaults?.apiType || 'openai'
  availableModelIds.value = []
})

async function fetchModels() {
  loading.value = true
  try {
    const res = await listModelsApi()
    if (res.data.code === 200) {
      models.value = res.data.data ?? []
    }
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEditMode.value = false
  editingModel.value = {
    name: '',
    provider: 'openai',
    modelName: '',
    apiUrl: providerDefaults.openai.apiUrl,
    apiType: 'openai',
    apiKey: '',
    maxTokens: 131072,
    temperature: 0.7,
    modelType: 'TEXT',
    isDefault: false,
    enabled: true,
  }
  availableModelIds.value = []
  showModal.value = true
}

function openEdit(model: AiModel) {
  editingModel.value = { ...model }
  availableModelIds.value = []
  isEditMode.value = true
  showModal.value = true
}

async function handleFetchModels() {
  const { apiUrl, apiType, apiKey, provider } = editingModel.value
  if (!apiUrl) {
    message.warning('请先填写请求地址')
    return
  }
  fetchingModels.value = true
  availableModelIds.value = []
  try {
    const res = await validateModelApi({
      baseUrl: apiUrl,
      provider: provider || 'openai',
      apiType: apiType || 'openai',
      apiKey: apiKey || '',
    })
    if (res.data.code === 200 && res.data.data) {
      availableModelIds.value = res.data.data.map((id) => ({ label: id, value: id }))
      if (res.data.data.length > 0 && !editingModel.value.modelName) {
        editingModel.value.modelName = res.data.data[0]
      }
      message.success(res.data.message || '获取成功')
    } else {
      message.error(res.data.message || '获取模型列表失败')
    }
  } catch {
    message.error('网络异常，无法连接')
  } finally {
    fetchingModels.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    if (isEditMode.value && editingModel.value.id) {
      const res = await updateModelApi(editingModel.value.id, editingModel.value)
      if (res.data.code === 200) {
        message.success('更新成功')
        showModal.value = false
        await fetchModels()
      } else {
        message.error(res.data.message || '更新失败')
      }
    } else {
      const res = await createModelApi(editingModel.value)
      if (res.data.code === 200) {
        message.success('创建成功')
        showModal.value = false
        await fetchModels()
      } else {
        message.error(res.data.message || '创建失败')
      }
    }
  } catch {
    message.error('网络异常')
  } finally {
    saving.value = false
  }
}

function handleDelete(model: AiModel) {
  dialog.warning({
    title: '确认删除',
    content: `确定要删除模型「${model.name}」吗？`,
    positiveText: '删除',
    negativeText: '取消',
    positiveButtonProps: { type: 'error' },
    onPositiveClick: async () => {
      try {
        const res = await deleteModelApi(model.id)
        if (res.data.code === 200) {
          message.success('已删除')
          await fetchModels()
        } else {
          message.error(res.data.message || '删除失败')
        }
      } catch {
        message.error('网络异常')
      }
    },
  })
}

const providerLabels: Record<string, string> = {
  openai: 'OpenAI',
  anthropic: 'Anthropic',
  deepseek: 'DeepSeek',
  qwen: '阿里百炼',
  other: '其它',
}

const columns: DataTableColumn<AiModel>[] = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '名称', key: 'name', width: 120 },
  {
    title: '类型',
    key: 'modelType',
    width: 80,
    render: (row) =>
      row.modelType === 'IMAGE'
        ? h('n-tag', { type: 'info', size: 'tiny', bordered: false }, { default: () => '图片' })
        : h('n-tag', { size: 'tiny', bordered: false }, { default: () => '文本' }),
  },
  {
    title: '供应商',
    key: 'provider',
    width: 100,
    render: (row) => providerLabels[row.provider] || row.provider,
  },
  { title: '模型 ID', key: 'modelName', width: 160, ellipsis: { tooltip: true } },
  { title: 'API 地址', key: 'apiUrl', ellipsis: { tooltip: true }, width: 280 },
  {
    title: '默认',
    key: 'isDefault',
    width: 70,
    render: (row) =>
      row.isDefault
        ? h('n-tag', { type: 'success', size: 'tiny', bordered: false }, { default: () => '默认' })
        : '-',
  },
  {
    title: '状态',
    key: 'enabled',
    width: 90,
    render: (row) =>
      row.enabled
        ? h('span', { style: 'display:inline-block;padding:2px 12px;border-radius:10px;font-size:12px;background:#18a058;color:#fff;line-height:22px;' }, '启用')
        : h('span', { style: 'display:inline-block;padding:2px 12px;border-radius:10px;font-size:12px;background:rgba(128,128,128,0.15);color:#888;line-height:22px;' }, '停用'),
  },
  {
    title: '操作',
    key: 'actions',
    width: 170,
    render: (row) =>
      h('div', { style: 'display:flex;gap:8px;' }, [
        h(NButton, { size: 'tiny', type: 'primary', onClick: () => openEdit(row) },
          { default: () => '编辑' }),
        h(NButton, { size: 'tiny', type: 'error', onClick: () => handleDelete(row) },
          { default: () => '删除' }),
      ]),
  },
]
</script>

<template>
  <n-space vertical size="large">
    <!-- Toolbar -->
    <div style="display: flex; justify-content: space-between; align-items: center;">
      <n-h3 style="margin: 0;">模型管理</n-h3>
      <n-button type="primary" @click="openCreate">
        <template #icon>
          <n-icon>
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
              <path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>
            </svg>
          </n-icon>
        </template>
        添加模型
      </n-button>
    </div>

    <!-- Table -->
    <n-data-table
      :columns="columns"
      :data="models"
      :loading="loading"
      :bordered="true"
      :single-line="false"
      :row-key="(row: AiModel) => row.id"
      striped
    />

    <!-- Edit Modal -->
    <n-modal v-model:show="showModal" :title="isEditMode ? '编辑模型' : '添加模型'" preset="card"
      style="width: 560px; max-width: 90vw;" :mask-closable="false" :segmented="true">
      <n-form>
        <n-form-item label="名称" required>
          <n-input v-model:value="editingModel.name" placeholder="如 GPT-4o、DeepSeek-V3" />
        </n-form-item>
        <n-form-item label="供应商" required>
          <n-select
            v-model:value="editingModel.provider"
            :options="providerOptions"
          />
        </n-form-item>
        <n-form-item label="模型 ID" required>
          <n-space style="width: 100%;" :wrap="false">
            <n-select
              v-if="availableModelIds.length > 0"
              v-model:value="editingModel.modelName"
              :options="availableModelIds"
              :disabled="saving"
              filterable
              placeholder="选择模型 ID"
              style="flex: 1;"
            />
            <n-input
              v-else
              v-model:value="editingModel.modelName"
              placeholder="如 gpt-4o、deepseek-chat"
              :disabled="saving"
              style="flex: 1;"
            />
            <n-button
              type="primary" secondary
              :loading="fetchingModels"
              :disabled="saving || !editingModel.apiUrl"
              @click="handleFetchModels"
            >
              获取模型列表
            </n-button>
          </n-space>
        </n-form-item>
        <n-form-item label="请求地址">
          <n-input v-model:value="editingModel.apiUrl" placeholder="https://api.openai.com" :disabled="saving" />
        </n-form-item>
        <n-grid :cols="2" :x-gap="16">
          <n-gi>
            <n-form-item label="API 格式">
              <n-select
                v-model:value="editingModel.apiType"
                :disabled="saving"
                :options="[
                  { label: 'OpenAI 格式', value: 'openai' },
                  { label: 'Anthropic Messages 格式', value: 'anthropic' },
                ]"
              />
            </n-form-item>
          </n-gi>
          <n-gi>
            <n-form-item label="温度">
              <n-input-number v-model:value="editingModel.temperature" :min="0" :max="2" :step="0.1" style="width: 100%;" />
            </n-form-item>
          </n-gi>
        </n-grid>
        <n-form-item label="API 密钥">
          <n-input v-model:value="editingModel.apiKey" type="password" show-password-on="click" placeholder="sk-..." />
        </n-form-item>
        <n-grid :cols="2" :x-gap="16">
          <n-gi>
            <n-form-item label="最大 Token">
              <n-select v-model:value="editingModel.maxTokens" :options="tokenOptions" :disabled="saving" />
            </n-form-item>
          </n-gi>
          <n-gi>
            <n-form-item label="模型类型">
              <n-radio-group v-model:value="editingModel.modelType">
                <n-radio-button value="TEXT">文本</n-radio-button>
                <n-radio-button value="IMAGE">图片</n-radio-button>
              </n-radio-group>
            </n-form-item>
          </n-gi>
        </n-grid>
        <n-form-item>
          <n-space>
            <n-checkbox v-model:checked="editingModel.isDefault">设为默认模型</n-checkbox>
            <n-checkbox v-model:checked="editingModel.enabled">启用</n-checkbox>
          </n-space>
        </n-form-item>
        <div style="display: flex; gap: 12px; justify-content: flex-end;">
          <n-button @click="showModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleSave">
            {{ isEditMode ? '保存' : '创建' }}
          </n-button>
        </div>
      </n-form>
    </n-modal>
  </n-space>
</template>
