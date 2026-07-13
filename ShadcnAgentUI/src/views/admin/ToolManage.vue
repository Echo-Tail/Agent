<script setup lang="ts">
import { ref, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog'
import {
  listToolsApi,
  updateToolApi,
  toggleToolApi,
  saveToolConfigApi,
} from '@/api/tool'

import { ToolCategoryKeys } from '@/types/enums'
import { useAuthStore } from '@/stores/auth'
import { useI18n } from 'vue-i18n'
import type { ToolDefinition } from '@/api/tool'

import { Settings2 } from 'lucide-vue-next'
import { Badge } from '@/components/ui/badge'
import { toast } from 'sonner'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'

const { t } = useI18n()
const authStore = useAuthStore()

const tools = ref<ToolDefinition[]>([])
const loading = ref(false)
const toggling = ref<Set<string>>(new Set())

// Config modal
const showConfigModal = ref(false)
const editingTool = ref<ToolDefinition | null>(null)
const editName = ref('')
const editDescription = ref('')
const configApiProvider = ref<'tavily' | 'firecrawl' | ''>('')
const configApiKey = ref('')
const saving = ref(false)

async function fetchTools() {
  loading.value = true
  try {
    const allTools = (await listToolsApi()) ?? []
    tools.value = allTools.filter(t => t.id !== 'image_generation')
  } catch {
    toast.error(t('error.loadFailed', { entity: '' }))
  } finally {
    loading.value = false
  }
}

onMounted(fetchTools)

async function handleToggle(tool: ToolDefinition) {
  if (toggling.value.has(tool.id)) return
  const valid = await authStore.verifyAuth()
  if (!valid || !authStore.isAdmin) {
    toast.error(t('error.forbidden'))
    return
  }
  toggling.value = new Set(toggling.value).add(tool.id)
  try {
    const data = await toggleToolApi(tool.id)
    tool.enabled = data.enabled
    toast.success(t('toast.updateSuccess'))
  } catch { /* interceptor handles toast */ } finally {
    const next = new Set(toggling.value)
    next.delete(tool.id)
    toggling.value = next
  }
}

async function openConfig(tool: ToolDefinition) {
  editingTool.value = tool
  editName.value = tool.name
  editDescription.value = tool.description
  try {
    const cfg = tool.configJson ? JSON.parse(tool.configJson) : {}
    configApiProvider.value = cfg.provider || ''
    configApiKey.value = cfg.apiKey || ''
  } catch {
    configApiProvider.value = ''
    configApiKey.value = ''
  }
  showConfigModal.value = true
}

async function handleSaveConfig() {
  if (!editingTool.value) return
  saving.value = true
  try {
    if (editName.value !== editingTool.value.name || editDescription.value !== editingTool.value.description) {
      const updated = await updateToolApi(editingTool.value.id, {
        name: editName.value,
        description: editDescription.value,
      })
      editingTool.value.name = updated.name
      editingTool.value.description = updated.description
    }

    let configJson = ''
    if (editingTool.value.id === 'web_search' && configApiKey.value) {
      configJson = JSON.stringify({
        provider: configApiProvider.value || 'tavily',
        apiKey: configApiKey.value,
      })
    } else if (configApiKey.value) {
      configJson = JSON.stringify({ apiKey: configApiKey.value })
    }

    if (configJson !== editingTool.value.configJson) {
      const configData = await saveToolConfigApi(editingTool.value.id, configJson)
      editingTool.value.configJson = configData.configJson
    }

    await fetchTools()
    toast.success(t('toast.saveSuccess'))
    showConfigModal.value = false
  } catch { /* interceptor handles toast */ } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader :title="$t('toolManage.title')" :description="$t('pageTitle.toolManage')" />

    <div v-if="loading" class="space-y-2">
      <Skeleton v-for="i in 4" :key="i" class="h-10 w-full" />
    </div>

    <div v-else class="border border-border rounded-lg overflow-hidden">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead class="w-auto">{{ $t('toolManage.columns.name') }}</TableHead>
            <TableHead class="w-auto">{{ $t('toolManage.columns.description') }}</TableHead>
            <TableHead class="w-28">{{ $t('toolManage.columns.category') }}</TableHead>
            <TableHead class="w-20">{{ $t('toolManage.columns.status') }}</TableHead>
            <TableHead class="w-20">{{ $t('toolManage.columns.action') }}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="tool in tools" :key="tool.id">
            <TableCell class="font-medium">{{ tool.name }}</TableCell>
            <TableCell class="max-w-xs truncate text-xs text-muted-foreground" :title="tool.description">
              {{ tool.description }}
            </TableCell>
            <TableCell>
              <Badge
                :class="[
                  'text-xs text-white',
                  tool.category === 'web' ? 'bg-green-600' :
                  tool.category === 'media' ? 'bg-blue-600' :
                  tool.category === 'browser' ? 'bg-amber-600' :
                  tool.category === 'terminal_files' ? 'bg-red-600' :
                  tool.category === 'memory' ? 'bg-purple-600' :
                  'bg-muted-foreground'
                ]"
              >
                {{ $t(ToolCategoryKeys[tool.category]) || tool.category }}
              </Badge>
            </TableCell>
            <TableCell>
              <Switch
                :checked="tool.enabled"
                :disabled="toggling.has(tool.id)"
                @update:checked="handleToggle(tool)"
              />
            </TableCell>
            <TableCell>
              <Button variant="outline" size="sm" class="h-7 text-xs" @click="openConfig(tool)">
                <Settings2 class="mr-1 h-3 w-3" />{{ $t('toolManage.config') }}
              </Button>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>

    <!-- Config Modal -->
    <Dialog :open="showConfigModal" @update:open="showConfigModal = $event">
      <DialogContent class="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{{ editName || $t('toolManage.config') }}</DialogTitle>
          <DialogDescription class="sr-only">{{ editName || $t('toolManage.config') }}</DialogDescription>
        </DialogHeader>
        <div v-if="editingTool" class="space-y-4">
          <div class="space-y-2">
            <Label for="tool-name">{{ $t('toolManage.columns.name') }}</Label>
            <Input id="tool-name" v-model="editName" />
          </div>
          <div class="space-y-2">
            <Label for="tool-description">{{ $t('toolManage.columns.description') }}</Label>
            <Textarea id="tool-description" v-model="editDescription" rows="3" />
          </div>

          <div class="border-t border-border pt-4">
            <!-- web_search -->
            <template v-if="editingTool.id === 'web_search'">
              <div class="space-y-2">
                <p class="text-sm font-medium">API Provider</p>
                <div class="flex gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    :class="configApiProvider === 'tavily' ? 'border-primary bg-primary/5' : ''"
                    @click="configApiProvider = 'tavily'"
                  >Tavily</Button>
                  <Button
                    variant="outline"
                    size="sm"
                    :class="configApiProvider === 'firecrawl' ? 'border-primary bg-primary/5' : ''"
                    @click="configApiProvider = 'firecrawl'"
                  >Firecrawl</Button>
                </div>
              </div>
              <div class="space-y-2 mt-3">
                <Label for="tool-api-key">{{ $t('toolManage.apiKeyConfig') }}</Label>
                <Input id="tool-api-key" name="tool-api-key" v-model="configApiKey" type="password" :placeholder="$t('toolManage.config')" />
              </div>
            </template>

            <!-- Other tools -->
            <template v-else>
              <div v-if="!configApiKey" class="text-center py-4 text-sm text-muted-foreground">
                {{ $t('toolManage.noExtraConfig') }}
              </div>
              <div v-else class="space-y-2">
                <Label for="tool-api-key-fallback">{{ $t('toolManage.apiKeyConfig') }}</Label>
                <Input id="tool-api-key-fallback" name="tool-api-key-fallback" v-model="configApiKey" type="password" :placeholder="$t('toolManage.config')" />
              </div>
            </template>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="showConfigModal = false">{{ $t('common.cancel') }}</Button>
          <Button :loading="saving" @click="handleSaveConfig">{{ $t('common.save') }}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
