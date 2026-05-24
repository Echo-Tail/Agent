<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { createAgentApi, updateAgentApi, getAgentApi } from '@/api/agent'
import { listModelsApi } from '@/api/model'
import { listToolsApi } from '@/api/tool'
import { listSkillsApi } from '@/api/skill'
import type { AiModel } from '@/types/api'
import type { ToolDefinition } from '@/api/tool'
import type { SkillDefinition } from '@/types/api'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Switch } from '@/components/ui/switch'
import { ArrowLeft, Loader2, Save } from 'lucide-vue-next'
import { toast } from 'sonner'
import AvatarPicker from '@/components/AvatarPicker.vue'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()

const isEdit = !!route.params.id
const loading = ref(false)
const saving = ref(false)

const models = ref<AiModel[]>([])
const tools = ref<ToolDefinition[]>([])
const skills = ref<SkillDefinition[]>([])

const form = ref({
  name: '',
  icon: '',
  description: '',
  systemPrompt: '',
  greeting: '',
  modelId: 0,
  tags: [] as string[],
  tools: [] as string[],
  skills: [] as string[],
  ragMode: 'AGENTIC' as 'GENERIC' | 'AGENTIC',
})

const tagInput = ref('')

onMounted(async () => {
  loading.value = true
  try {
    const [modelsData, toolsData, skillsData] = await Promise.all([
      listModelsApi(),
      listToolsApi(),
      listSkillsApi(),
    ])
    models.value = modelsData ?? []
    tools.value = (toolsData ?? []).filter(t => t.enabled)
    skills.value = skillsData ?? []

    if (isEdit) {
      const id = Number(route.params.id)
      const a = await getAgentApi(id)
      if (a) {
        form.value = {
          name: a.name,
          icon: a.icon || '',
          description: a.description || '',
          systemPrompt: a.systemPrompt || '',
          greeting: a.greeting || '',
          modelId: a.modelId,
          tags: a.tags || [],
          tools: a.tools || [],
          skills: a.skills || [],
          ragMode: a.ragMode || 'AGENTIC',
        }
      }
    }
  } catch {
    toast.error(t('error.loadFailed', { entity: '' }))
  } finally {
    loading.value = false
  }
})

function addTag() {
  const tag = tagInput.value.trim()
  if (tag && !form.value.tags.includes(tag) && form.value.tags.length < 10) {
    form.value.tags.push(tag)
    tagInput.value = ''
  }
}

function removeTag(tag: string) {
  form.value.tags = form.value.tags.filter(t => t !== tag)
}

function toggleTool(toolId: string) {
  const idx = form.value.tools.indexOf(toolId)
  if (idx >= 0) form.value.tools.splice(idx, 1)
  else form.value.tools.push(toolId)
}

function toggleSkill(name: string) {
  const idx = form.value.skills.indexOf(name)
  if (idx >= 0) form.value.skills.splice(idx, 1)
  else form.value.skills.push(name)
}

async function handleSave() {
  if (!form.value.name.trim()) {
    toast.error(t('agent.nameRequired'))
    return
  }
  if (!form.value.modelId) {
    toast.error(t('agent.modelRequired'))
    return
  }

  saving.value = true
  try {
    const payload = {
      name: form.value.name.trim(),
      icon: form.value.icon || undefined,
      description: form.value.description.trim() || undefined,
      systemPrompt: form.value.systemPrompt.trim() || undefined,
      greeting: form.value.greeting.trim() || undefined,
      modelId: form.value.modelId,
      tags: form.value.tags.length ? form.value.tags : undefined,
      tools: form.value.tools.length ? form.value.tools : undefined,
      skills: form.value.skills.length ? form.value.skills : undefined,
      ragMode: form.value.ragMode,
    }

    if (isEdit) {
      await updateAgentApi(Number(route.params.id), payload)
      toast.success(t('toast.updateSuccess'))
    } else {
      await createAgentApi(payload)
      toast.success(t('toast.createSuccess'))
    }
    router.push({ name: 'AgentList' })
  } catch { /* interceptor handles toast */ } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="max-w-3xl mx-auto space-y-6">
    <div class="flex items-center gap-4">
      <Button variant="ghost" size="icon" @click="router.back()">
        <ArrowLeft class="h-4 w-4" />
      </Button>
      <div>
        <h2 class="text-2xl font-bold tracking-tight">{{ isEdit ? $t('agent.edit') : $t('agent.create') }}</h2>
        <p class="text-muted-foreground text-sm">{{ isEdit ? $t('agent.edit') : $t('agent.create') }}</p>
      </div>
    </div>

    <div v-if="loading" class="text-center py-12">
      <Loader2 class="mx-auto h-8 w-8 animate-spin text-muted-foreground" />
    </div>

    <template v-else>
      <!-- Basic Info -->
      <Card>
        <CardHeader><CardTitle class="text-lg">Basic Info</CardTitle></CardHeader>
        <CardContent class="space-y-4">
          <div class="grid grid-cols-2 gap-4">
            <div class="space-y-2">
              <Label>{{ $t('agent.name') }} <span class="text-destructive">*</span></Label>
              <Input v-model="form.name" :placeholder="$t('agent.formNamePlaceholder')" maxlength="30" />
            </div>
            <div class="space-y-2">
              <Label>{{ $t('agent.icon') }}</Label>
              <AvatarPicker v-model="form.icon" />
            </div>
          </div>
          <div class="space-y-2">
            <Label>{{ $t('agent.description') }}</Label>
            <Textarea v-model="form.description" placeholder="Briefly describe this Agent" maxlength="500" />
          </div>
          <div class="space-y-2">
            <Label>{{ $t('agent.greeting') }}</Label>
            <Input v-model="form.greeting" :placeholder="$t('agent.formGreetingPlaceholder')" maxlength="200" />
          </div>
        </CardContent>
      </Card>

      <!-- Model & RAG -->
      <Card>
        <CardHeader><CardTitle class="text-lg">{{ $t('agent.model') }} &amp; {{ $t('agent.knowledgeBase') }}</CardTitle></CardHeader>
        <CardContent class="space-y-4">
          <div class="space-y-2">
            <Label>{{ $t('agent.model') }} <span class="text-destructive">*</span></Label>
            <Select v-model="form.modelId" :disabled="models.length === 0">
              <SelectTrigger>
                <SelectValue :placeholder="models.length === 0 ? $t('agent.noToolsConfig') : $t('agent.selectModel')" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem v-for="m in models" :key="m.id" :value="m.id">
                  {{ m.name }} ({{ m.modelName }})
                </SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div class="flex items-center justify-between">
            <div>
              <Label>{{ $t('agent.ragMode') }}</Label>
              <p class="text-xs text-muted-foreground">{{ $t('agent.ragModeDesc') }}</p>
            </div>
            <Switch
              :checked="form.ragMode === 'AGENTIC'"
              @update:checked="(v: boolean) => form.ragMode = v ? 'AGENTIC' : 'GENERIC'"
            />
          </div>
        </CardContent>
      </Card>

      <!-- Tags -->
      <Card>
        <CardHeader><CardTitle class="text-lg">{{ $t('agent.tags') }}</CardTitle></CardHeader>
        <CardContent class="space-y-3">
          <div class="flex gap-2">
            <Input v-model="tagInput" placeholder="Enter tag and press Enter" @keyup.enter.prevent="addTag" />
            <Button variant="outline" @click="addTag" type="button">Add</Button>
          </div>
          <div v-if="form.tags.length" class="flex flex-wrap gap-1">
            <Badge v-for="tag in form.tags" :key="tag" variant="secondary" class="cursor-pointer" @click="removeTag(tag)">
              {{ tag }} ✕
            </Badge>
          </div>
        </CardContent>
      </Card>

      <!-- Tools -->
      <Card v-if="tools.length">
        <CardHeader><CardTitle class="text-lg">{{ $t('agent.tools') }}</CardTitle></CardHeader>
        <CardContent>
          <div class="flex flex-wrap gap-2">
            <Badge
              v-for="tool in tools"
              :key="tool.id"
              :variant="form.tools.includes(tool.id) ? 'default' : 'outline'"
              class="cursor-pointer"
              @click="toggleTool(tool.id)"
            >
              {{ tool.name }}
            </Badge>
          </div>
        </CardContent>
      </Card>

      <!-- Skills -->
      <Card v-if="skills.length">
        <CardHeader><CardTitle class="text-lg">{{ $t('agent.skill') }}</CardTitle></CardHeader>
        <CardContent>
          <div class="flex flex-wrap gap-2">
            <Badge
              v-for="skill in skills"
              :key="skill.name"
              :variant="form.skills.includes(skill.name) ? 'default' : 'outline'"
              class="cursor-pointer"
              @click="toggleSkill(skill.name)"
            >
              {{ skill.name }}
            </Badge>
          </div>
        </CardContent>
      </Card>

      <!-- System Prompt -->
      <Card>
        <CardHeader><CardTitle class="text-lg">{{ $t('agent.systemPrompt') }}</CardTitle></CardHeader>
        <CardContent>
          <Textarea v-model="form.systemPrompt" :placeholder="$t('agent.formPromptPlaceholder')" class="min-h-[200px]" />
        </CardContent>
      </Card>

      <!-- Save -->
      <div class="flex justify-end gap-2">
        <Button variant="outline" @click="router.back()">{{ $t('common.cancel') }}</Button>
        <Button :disabled="saving" @click="handleSave">
          <Loader2 v-if="saving" class="mr-2 h-4 w-4 animate-spin" />
          <Save v-else class="mr-2 h-4 w-4" />
          {{ isEdit ? $t('common.save') : $t('agent.create') }}
        </Button>
      </div>
    </template>
  </div>
</template>
