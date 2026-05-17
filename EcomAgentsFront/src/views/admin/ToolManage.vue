<script setup lang="ts">
import { h, ref, onMounted, defineExpose } from 'vue'
import { useMessage, NSwitch, NButton } from 'naive-ui'
import type { DataTableColumn, SelectOption } from 'naive-ui'
import { listToolsApi, updateToolApi, toggleToolApi, saveToolConfigApi } from '../../api/tool'
import { listModelsApi } from '../../api/model'
import type { ToolDefinition } from '../../api/tool'
import type { AiModel } from '../../types/api'

const message = useMessage()

const tools = ref<ToolDefinition[]>([])
const loading = ref(false)
const toggling = ref<Set<string>>(new Set())

/* ====== Config modal ====== */
const showConfigModal = ref(false)
const editingTool = ref<ToolDefinition | null>(null)
const editName = ref('')
const editDescription = ref('')
const configApiProvider = ref<'tavily' | 'firecrawl' | ''>('')
const configApiKey = ref('')
const configModelId = ref<number | null>(null)
const models = ref<SelectOption[]>([])
const loadingModels = ref(false)
const saving = ref(false)

const categoryLabels: Record<string, string> = {
  web: '网页搜索',
  media: '图片生成',
  browser: '浏览器',
  terminal_files: '终端与文件',
  memory: '记忆系统',
}

const categoryColors: Record<string, string> = {
  web: '#18a058',
  media: '#2080f0',
  browser: '#f0a020',
  terminal_files: '#d03050',
  memory: '#8050c0',
}

/* ====== Fetch ====== */
async function fetchTools() {
  loading.value = true
  try {
    const res = await listToolsApi()
    if (res.data.code === 200) {
      tools.value = res.data.data ?? []
    }
  } catch {
    message.error('加载工具列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(fetchTools)

/* ====== Toggle ====== */
async function handleToggle(tool: ToolDefinition) {
  if (toggling.value.has(tool.id)) return
  toggling.value = new Set(toggling.value).add(tool.id)
  try {
    const res = await toggleToolApi(tool.id)
    if (res.data.code === 200) {
      tool.enabled = res.data.data.enabled
      message.success(res.data.message || (tool.enabled ? '已启用' : '已禁用'))
    } else {
      message.error(res.data.message || '操作失败')
    }
  } catch {
    message.error('网络异常')
  } finally {
    const next = new Set(toggling.value)
    next.delete(tool.id)
    toggling.value = next
  }
}

/* ====== Edit/Config modal ====== */
async function openConfig(tool: ToolDefinition) {
  editingTool.value = tool
  editName.value = tool.name
  editDescription.value = tool.description
  // Parse existing config
  try {
    const cfg = tool.configJson ? JSON.parse(tool.configJson) : {}
    configApiProvider.value = cfg.provider || ''
    configApiKey.value = cfg.apiKey || ''
    configModelId.value = cfg.modelId ?? null
  } catch {
    configApiProvider.value = ''
    configApiKey.value = ''
    configModelId.value = null
  }
  showConfigModal.value = true

  // Load models for image_generation
  if (tool.id === 'image_generation') {
    await fetchEnabledModels()
  }
}

async function fetchEnabledModels() {
  loadingModels.value = true
  try {
    const res = await listModelsApi()
    if (res.data.code === 200) {
      const list: AiModel[] = res.data.data ?? []
      models.value = list
        .filter((m) => m.enabled)
        .map((m) => ({
          label: `${m.name}（${m.provider} · ${m.modelName}）`,
          value: m.id,
        }))
    } else {
      models.value = []
      message.error(res.data.message || '加载模型列表失败')
    }
  } catch {
    models.value = []
    message.error('网络异常，无法加载模型列表')
  } finally {
    loadingModels.value = false
  }
}

async function handleSaveConfig() {
  if (!editingTool.value) return
  saving.value = true
  try {
    // Update name/description first
    if (editName.value !== editingTool.value.name || editDescription.value !== editingTool.value.description) {
      const updateRes = await updateToolApi(editingTool.value.id, {
        name: editName.value,
        description: editDescription.value,
      })
      if (updateRes.data.code !== 200) {
        message.error(updateRes.data.message || '更新失败')
        return
      }
      editingTool.value.name = updateRes.data.data.name
      editingTool.value.description = updateRes.data.data.description
    }

    // Build config JSON based on tool type
    let configJson = ''
    if (editingTool.value.id === 'web_search' && configApiKey.value) {
      configJson = JSON.stringify({
        provider: configApiProvider.value || 'tavily',
        apiKey: configApiKey.value,
      })
    } else if (editingTool.value.id === 'image_generation') {
      if (configModelId.value != null) {
        configJson = JSON.stringify({ modelId: configModelId.value })
      }
    } else if (configApiKey.value) {
      configJson = JSON.stringify({ apiKey: configApiKey.value })
    }

    if (configJson !== editingTool.value.configJson) {
      const configRes = await saveToolConfigApi(editingTool.value.id, configJson)
      if (configRes.data.code !== 200) {
        message.error(configRes.data.message || '配置保存失败')
        return
      }
      editingTool.value.configJson = configRes.data.data.configJson
    }

    // Refresh list to reflect changes
    await fetchTools()
    message.success('保存成功')
    showConfigModal.value = false
  } catch {
    message.error('网络异常')
  } finally {
    saving.value = false
  }
}

/* ====== Expose for testing ====== */
defineExpose({ openConfig, fetchEnabledModels, fetchTools, showConfigModal, models, loadingModels, configModelId, configApiKey })

/* ====== Columns ====== */
const columns: DataTableColumn<ToolDefinition>[] = [
  {
    title: '名称',
    key: 'name',
    width: 140,
    ellipsis: { tooltip: true },
  },
  {
    title: '描述',
    key: 'description',
    ellipsis: { tooltip: true },
    minWidth: 200,
  },
  {
    title: '类别',
    key: 'category',
    width: 120,
    render: (row) =>
      h('span', {
        style: `display:inline-block;padding:1px 10px;border-radius:10px;font-size:12px;background:${categoryColors[row.category] || '#888'};color:#fff;line-height:20px;`,
      }, categoryLabels[row.category] || row.category),
  },
  {
    title: '状态',
    key: 'enabled',
    width: 90,
    render: (row) =>
      h(NSwitch, {
        value: row.enabled,
        loading: toggling.value.has(row.id),
        'onUpdate:value': () => handleToggle(row),
        size: 'small',
      }),
  },
  {
    title: '配置',
    key: 'config',
    width: 80,
    render: (row) =>
      h(NButton, {
        size: 'tiny',
        type: 'primary',
        onClick: () => openConfig(row),
      }, { default: () => '配置' }),
  },
]
</script>

<template>
  <n-space vertical size="large">
    <div style="display: flex; justify-content: space-between; align-items: center;">
      <n-h3 style="margin: 0;">工具管理</n-h3>
    </div>

    <n-data-table
      :columns="columns"
      :data="tools"
      :loading="loading"
      :bordered="true"
      :single-line="false"
      :row-key="(row: ToolDefinition) => row.id"
      striped
    />

    <!-- Config Modal -->
    <n-modal
      v-model:show="showConfigModal"
      :title="editName || '工具配置'"
      preset="card"
      style="width: 520px; max-width: 90vw;"
      :mask-closable="false"
      :segmented="true"
    >
      <n-form v-if="editingTool">
        <n-form-item label="工具名称">
          <n-input v-model:value="editName" :disabled="saving" />
        </n-form-item>
        <n-form-item label="工具描述">
          <n-input v-model:value="editDescription" type="textarea" :rows="2" :disabled="saving" />
        </n-form-item>

        <n-divider />

        <!-- web_search: API Provider + Key -->
        <template v-if="editingTool.id === 'web_search'">
          <n-form-item label="API 提供商">
            <n-radio-group v-model:value="configApiProvider" :disabled="saving">
              <n-radio-button value="tavily">Tavily</n-radio-button>
              <n-radio-button value="firecrawl">Firecrawl</n-radio-button>
            </n-radio-group>
          </n-form-item>
          <n-form-item label="API Key">
            <n-input
              v-model:value="configApiKey"
              type="password"
              show-password-on="click"
              placeholder="输入 API Key"
              :disabled="saving"
            />
          </n-form-item>
        </template>

        <!-- image_generation: Model selector only (API Key comes from model config) -->
        <template v-else-if="editingTool.id === 'image_generation'">
          <n-alert type="info" :bordered="false" style="margin-bottom: 12px;">
            选择模型管理中已配置的图片生成模型，API Key 等相关信息沿用模型配置。
          </n-alert>
          <n-form-item label="使用模型">
            <n-select
              v-model:value="configModelId"
              :options="models"
              :loading="loadingModels"
              :disabled="saving || loadingModels"
              filterable
              placeholder="选择图片生成模型"
              clearable
            />
          </n-form-item>
          <n-alert v-if="models.length === 0 && !loadingModels" type="warning" :bordered="false">
            模型管理中暂无已启用的模型，请先<a href="#/admin/models" style="text-decoration: underline;">添加模型</a>。
          </n-alert>
        </template>

        <!-- Other tools: generic API Key -->
        <template v-else>
          <n-empty description="此工具无需额外配置" style="padding: 16px 0;" />
        </template>

        <div style="display: flex; gap: 12px; justify-content: flex-end; margin-top: 16px;">
          <n-button @click="showConfigModal = false" :disabled="saving">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleSaveConfig">保存</n-button>
        </div>
      </n-form>
    </n-modal>
  </n-space>
</template>
