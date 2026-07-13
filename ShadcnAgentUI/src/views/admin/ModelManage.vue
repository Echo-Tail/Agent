<script setup lang="ts">
import { computed, ref, watch, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  listModelsApi,
  createModelApi,
  updateModelApi,
  deleteModelApi,
  validateModelApi,
} from '@/api/model'
import type { AiModel } from '@/types/api'
import {
  Plus,
  Pencil,
  Trash2,
  RefreshCw,
} from 'lucide-vue-next'
import { toast } from 'sonner'
import { useI18n } from 'vue-i18n'

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

const { t } = useI18n()

const modelTypeKeys: Record<string, string> = {
  TEXT: 'modelManage.modelType.TEXT',
  IMAGE: 'modelManage.modelType.IMAGE',
  MULTIMODAL: 'modelManage.modelType.MULTIMODAL',
}

const models = ref<AiModel[]>([])
const loading = ref(false)
const saving = ref(false)

const showModal = ref(false)
const editingModel = ref<Partial<AiModel>>({})
const isEditMode = ref(false)
const showDeleteDialog = ref(false)
const deleteTarget = ref<number | null>(null)
const formError = ref('')
const showApiKeyInput = ref(false)

const fetchingModels = ref(false)
const availableModelIds = ref<{ label: string; value: string }[]>([])
const canFetchModels = computed(() => Boolean(editingModel.value.apiUrl && editingModel.value.apiKey && !fetchingModels.value))

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

const apiTypeOptions = [
  { label: 'OpenAI 格式', value: 'openai' },
  { label: 'Anthropic Messages 格式', value: 'anthropic' },
]

const tokenOptions = [
  { label: '4K', value: 4096 },
  { label: '16K', value: 16384 },
  { label: '64K', value: 65536 },
  { label: '128K', value: 131072 },
  { label: '256K', value: 262144 },
]

const modelTypeOptions = Object.keys(modelTypeKeys)

const providerLabels: Record<string, string> = {
  openai: 'OpenAI',
  anthropic: 'Anthropic',
  deepseek: 'DeepSeek',
  qwen: '阿里百炼',
  other: '其它',
}

onMounted(fetchModels)

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
    models.value = (await listModelsApi()) ?? []
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
    maxTokens: 65536,
    temperature: 0.7,
    modelType: 'TEXT',
    isDefault: false,
    enabled: true,
  }
  formError.value = ''
  showApiKeyInput.value = true
  availableModelIds.value = []
  showModal.value = true
}

function openEdit(model: AiModel) {
  editingModel.value = { ...model }
  formError.value = ''
  showApiKeyInput.value = false
  availableModelIds.value = []
  isEditMode.value = true
  showModal.value = true
}

function maskApiKey(key: string | undefined): string {
  if (!key || key.length <= 8) return '****'
  return key.slice(0, 8) + '********' + key.slice(-4)
}

async function handleFetchModels() {
  const { apiUrl, apiType, apiKey, provider } = editingModel.value
  if (!apiUrl) {
    toast.warning(t('modelManage.formInvalid'))
    return
  }
  fetchingModels.value = true
  availableModelIds.value = []
  try {
    const data = await validateModelApi({
      baseUrl: apiUrl,
      provider: provider || 'openai',
      apiType: apiType || 'openai',
      apiKey: apiKey || '',
    })
    if (data && data.length > 0) {
      availableModelIds.value = data.map((id) => ({ label: id, value: id }))
      if (!editingModel.value.modelName) {
        editingModel.value.modelName = data[0]
      }
      toast.success(t('toast.saveSuccess'))
    }
  } catch { /* interceptor handles toast */ } finally {
    fetchingModels.value = false
  }
}

function hasTextValue(value: unknown) {
  return typeof value === 'string' && value.trim().length > 0
}

function validateEditingModel() {
  const model = editingModel.value
  if (!hasTextValue(model.name) || !hasTextValue(model.provider) || !hasTextValue(model.modelName) || !hasTextValue(model.apiUrl)) {
    return t('modelManage.formInvalid')
  }
  if (!hasTextValue(model.modelType) || !modelTypeOptions.includes(model.modelType as string)) {
    return t('modelManage.formInvalid')
  }
  const maxTokens = Number(model.maxTokens)
  if (!Number.isFinite(maxTokens) || maxTokens < 1) {
    return t('modelManage.formInvalid')
  }
  const temperature = Number(model.temperature)
  if (!Number.isFinite(temperature) || temperature < 0 || temperature > 2) {
    return t('modelManage.formInvalid')
  }
  return ''
}

function buildModelPayload(): Partial<AiModel> {
  const model = editingModel.value
  return {
    ...model,
    name: model.name?.trim(),
    provider: model.provider?.trim(),
    modelName: model.modelName?.trim(),
    apiUrl: model.apiUrl?.trim(),
    apiType: model.apiType?.trim() || 'openai',
    modelType: model.modelType?.trim() || 'TEXT',
    maxTokens: Number(model.maxTokens),
    temperature: Number(model.temperature),
  }
}

async function handleSave() {
  if (saving.value) return
  const validationError = validateEditingModel()
  if (validationError) {
    toast.warning(validationError)
    return
  }
  saving.value = true
  const payload = buildModelPayload()
  try {
    if (isEditMode.value && payload.id) {
      await updateModelApi(payload.id, payload)
      toast.success(t('toast.updateSuccess'))
    } else {
      await createModelApi(payload)
      toast.success(t('toast.createSuccess'))
    }
    showModal.value = false
    await fetchModels()
  } catch (error) {
    const msg = error instanceof Error ? error.message : t('error.saveFailed')
    // 去除可能混入的代码前缀，只保留友好的用户提示
    const cleanMsg = msg.replace(/^(Error|TypeError|AxiosError):\s*/i, '')
    formError.value = cleanMsg
    toast.error(cleanMsg)
  } finally {
    saving.value = false
  }
}

function confirmDelete(model: AiModel) {
  deleteTarget.value = model.id
  showDeleteDialog.value = true
}

async function handleDelete() {
  if (deleteTarget.value === null) return
  try {
    await deleteModelApi(deleteTarget.value)
    toast.success(t('toast.deleteSuccess'))
    await fetchModels()
  } catch { /* interceptor handles toast */ } finally {
    showDeleteDialog.value = false
    deleteTarget.value = null
  }
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader :title="$t('modelManage.title')" :description="$t('pageTitle.modelManage')">
      <Button @click="openCreate">
        <Plus class="mr-2 h-4 w-4" />{{ $t('modelManage.addModel') }}
      </Button>
    </PageHeader>

    <!-- Loading skeleton -->
    <div v-if="loading" class="space-y-2">
      <Skeleton v-for="i in 5" :key="i" class="h-10 w-full" />
    </div>

    <!-- Table -->
    <div v-else class="border border-border rounded-lg overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead class="w-14">{{ $t('userManage.columns.id') }}</TableHead>
            <TableHead class="w-[200px]">{{ $t('modelManage.columns.name') }}</TableHead>
            <TableHead class="w-20 whitespace-nowrap">{{ $t('modelManage.columns.modelType') }}</TableHead>
            <TableHead class="w-20">{{ $t('modelManage.columns.provider') }}</TableHead>
            <TableHead>{{ $t('modelManage.columns.modelId') }}</TableHead>
            <TableHead>{{ $t('modelManage.form.apiUrl') }}</TableHead>
            <TableHead class="w-14">{{ $t('modelManage.columns.default') }}</TableHead>
            <TableHead class="w-14">{{ $t('modelManage.columns.status') }}</TableHead>
            <TableHead class="w-28">{{ $t('common.action') }}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="m in models" :key="m.id">
            <TableCell class="text-muted-foreground">{{ m.id }}</TableCell>
            <TableCell class="max-w-[200px] truncate font-medium" :title="m.name">{{ m.name }}</TableCell>
            <TableCell class="whitespace-nowrap">
              <Badge v-if="m.modelType === 'IMAGE'" variant="secondary" class="whitespace-nowrap text-xs">{{ $t(modelTypeKeys.IMAGE) }}</Badge>
              <Badge v-else-if="m.modelType === 'MULTIMODAL'" variant="outline" class="whitespace-nowrap text-xs">{{ $t(modelTypeKeys.MULTIMODAL) }}</Badge>
              <span v-else class="whitespace-nowrap text-xs text-muted-foreground">{{ $t(modelTypeKeys.TEXT) }}</span>
            </TableCell>
            <TableCell class="text-muted-foreground">{{ providerLabels[m.provider] || m.provider }}</TableCell>
            <TableCell class="max-w-[130px] truncate font-mono text-xs text-muted-foreground" :title="m.modelName">{{ m.modelName }}</TableCell>
            <TableCell class="max-w-[210px] truncate font-mono text-xs text-muted-foreground" :title="m.apiUrl">{{ m.apiUrl }}</TableCell>
            <TableCell>
              <Badge v-if="m.isDefault" class="bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300 border-green-200 text-xs">{{ $t('common.yes') }}</Badge>
              <span v-else class="text-xs text-muted-foreground">-</span>
            </TableCell>
            <TableCell>
              <Badge
                variant="outline"
                :class="m.enabled ? 'text-green-600 border-green-200 bg-green-50 dark:border-green-800 dark:bg-green-950' : 'text-muted-foreground'"
                class="text-xs"
              >
                {{ m.enabled ? $t('toolManage.enableTool') : $t('toolManage.disableTool') }}
              </Badge>
            </TableCell>
            <TableCell>
              <div class="flex items-center gap-1">
                <Button variant="ghost" size="icon" class="h-8 w-8" @click="openEdit(m)">
                  <Pencil class="h-4 w-4" />
                </Button>
                <Button variant="ghost" size="icon" class="h-8 w-8 text-destructive hover:text-destructive" @click="confirmDelete(m)">
                  <Trash2 class="h-4 w-4" />
                </Button>
              </div>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>

    <!-- Edit/Create Modal -->
    <Dialog :open="showModal" @update:open="showModal = $event">
      <DialogContent class="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{{ isEditMode ? $t('modelManage.editModel') : $t('modelManage.addModel') }}</DialogTitle>
        </DialogHeader>
        <div class="space-y-4 max-h-[60vh] overflow-y-auto pr-1">
          <div v-if="formError" class="bg-destructive/10 border border-destructive/30 text-destructive rounded-lg px-3 py-2 text-sm">
            {{ formError }}
          </div>
          <div class="space-y-2">
            <label for="model-name" class="text-sm font-medium">{{ $t('modelManage.form.name') }} <span class="text-destructive">*</span></label>
            <Input id="model-name" name="model-name" v-model="editingModel.name" placeholder="GPT-4o、DeepSeek-V3" />
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div class="space-y-2">
              <label for="model-provider" class="text-sm font-medium">{{ $t('modelManage.form.provider') }} <span class="text-destructive">*</span></label>
              <Select v-model="editingModel.provider">
                <SelectTrigger id="model-provider" name="model-provider" class="w-full"><SelectValue :placeholder="$t('common.select')" /></SelectTrigger>
                <SelectContent>
                  <SelectItem v-for="opt in providerOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div class="space-y-2">
              <label for="model-type" class="text-sm font-medium">{{ $t('modelManage.form.modelType') }}</label>
              <Select v-model="editingModel.modelType">
                <SelectTrigger id="model-type" name="model-type" class="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem v-for="type in modelTypeOptions" :key="type" :value="type">{{ $t(modelTypeKeys[type]) }}</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <div class="space-y-2">
            <label for="model-id" class="text-sm font-medium">{{ $t('modelManage.form.modelId') }} <span class="text-destructive">*</span></label>
            <div class="flex gap-2">
              <Select
                v-if="availableModelIds.length > 0"
                v-model="editingModel.modelName"
                class="flex-1"
              >
                <SelectTrigger id="model-id" name="model-id"><SelectValue :placeholder="$t('common.select')" /></SelectTrigger>
                <SelectContent>
                  <SelectItem v-for="opt in availableModelIds" :key="opt.value" :value="opt.value">{{ opt.label }}</SelectItem>
                </SelectContent>
              </Select>
              <Input v-else id="model-id" name="model-id" v-model="editingModel.modelName" placeholder="gpt-4o、deepseek-chat" class="flex-1" />
              <Button
                variant="secondary"
                size="sm"
                :loading="fetchingModels"
                :disabled="!canFetchModels"
                :title="!editingModel.apiKey ? $t('modelManage.form.apiKeyRequiredForFetch') : undefined"
                @click="handleFetchModels"
              >
                <RefreshCw class="mr-1 h-3.5 w-3.5" />{{ $t('modelManage.fetchModels') }}
              </Button>
            </div>
          </div>
          <div class="space-y-2">
            <label for="model-api-url" class="text-sm font-medium">{{ $t('modelManage.form.apiUrl') }} <span class="text-destructive">*</span></label>
            <Input id="model-api-url" name="model-api-url" v-model="editingModel.apiUrl" placeholder="https://api.openai.com" />
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div class="space-y-2">
              <label for="model-api-type" class="text-sm font-medium">{{ $t('modelManage.form.apiType') }}</label>
              <Select v-model="editingModel.apiType">
                <SelectTrigger id="model-api-type" name="model-api-type"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem v-for="opt in apiTypeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div class="space-y-2">
              <label for="model-temperature" class="text-sm font-medium">{{ $t('modelManage.form.temperature') }}</label>
              <Input id="model-temperature" name="model-temperature" v-model="editingModel.temperature" type="number" min="0" max="2" step="0.1" />
            </div>
          </div>
          <div class="space-y-2">
            <label class="text-sm font-medium">{{ $t('modelManage.form.apiKey') }}</label>
            <div v-if="isEditMode && editingModel.apiKey && !showApiKeyInput"
              class="cursor-pointer" @click="showApiKeyInput = true; editingModel.apiKey = ''"
            >
              <code class="text-sm font-mono bg-muted px-2.5 py-1.5 rounded border tracking-wider select-all">{{ maskApiKey(editingModel.apiKey) }}</code>
            </div>
            <Input
              v-else
              id="model-api-key"
              name="model-api-key"
              v-model="editingModel.apiKey"
              type="password"
              placeholder="sk-..."
              @focus="showApiKeyInput = true"
            />
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div class="space-y-2">
              <label for="model-max-tokens" class="text-sm font-medium">{{ $t('modelManage.form.maxTokens') }}</label>
              <Select v-model="editingModel.maxTokens">
                <SelectTrigger id="model-max-tokens" name="model-max-tokens"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem v-for="opt in tokenOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <div class="flex items-center gap-4 pt-2">
            <label for="model-is-default" class="flex items-center gap-2 cursor-pointer">
              <input id="model-is-default" name="model-is-default" type="checkbox" v-model="editingModel.isDefault" class="rounded border-gray-300" />
              <span class="text-sm">{{ $t('modelManage.form.default') }}</span>
            </label>
            <label for="model-enabled" class="flex items-center gap-2 cursor-pointer">
              <input id="model-enabled" name="model-enabled" type="checkbox" v-model="editingModel.enabled" class="rounded border-gray-300" />
              <span class="text-sm">{{ $t('modelManage.form.enabled') }}</span>
            </label>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="showModal = false">{{ $t('common.cancel') }}</Button>
          <Button :loading="saving" @click="handleSave">{{ isEditMode ? $t('common.save') : $t('common.create') }}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <ConfirmDialog
      :open="showDeleteDialog"
      @update:open="showDeleteDialog = $event"
      :title="$t('dialog.deleteConfirm.title')"
      :description="$t('modelManage.confirmDelete')"
      :confirm-text="$t('common.delete')"
      @confirm="handleDelete"
    />
  </div>
</template>
