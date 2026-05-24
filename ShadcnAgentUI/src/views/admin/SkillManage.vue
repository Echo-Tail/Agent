<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  listSkillsApi,
  importFromUrlApi,
  uploadSkillZipApi,
  deleteSkillApi,
} from '@/api/skill'
import type { SkillDefinition } from '@/types/api'
import {
  Upload,
  Link,
  Loader2,
  Trash2,
  FileArchive,
  Globe,
} from 'lucide-vue-next'
import { toast } from 'sonner'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const skills = ref<SkillDefinition[]>([])
const loading = ref(false)

// URL Import
const showUrlModal = ref(false)
const importUrl = ref('')
const importingUrl = ref(false)

// ZIP Upload
const showUploadModal = ref(false)
const uploadFile = ref<File | null>(null)
const uploading = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

// Delete
const showDeleteDialog = ref(false)
const deleteTarget = ref<string | null>(null)

// Import animation state
const statusMessages = [
  t('skillManage.importStatus.parsingUrl'),
  t('skillManage.importStatus.checkingGit'),
  t('skillManage.importStatus.cloningRepo'),
  t('skillManage.importStatus.scanningSkill'),
  t('skillManage.importStatus.writingWorkspace'),
]
const currentStatusIndex = ref(0)
const elapsedSeconds = ref(0)
let statusTimer: ReturnType<typeof setInterval> | null = null
let elapsedTimer: ReturnType<typeof setInterval> | null = null

const tips = [
  t('skillManage.tip.gitCloneProxy'),
  t('skillManage.tip.batchImport'),
  t('skillManage.tip.subtreePath'),
  t('skillManage.tip.skillPackage'),
  t('skillManage.tip.autoGrant'),
  t('skillManage.tip.reimport'),
]
const currentTip = ref(tips[0])

const skillCategoryKeys: Record<string, string> = {
  'content-creation': 'skillManage.category.content-creation',
  'video-creation': 'skillManage.category.video-creation',
  'ecommerce-marketing': 'skillManage.category.ecommerce-marketing',
  'presentation': 'skillManage.category.presentation',
  'digital-human': 'skillManage.category.digital-human',
  'document-analysis': 'skillManage.category.document-analysis',
  'voice-audio': 'skillManage.category.voice-audio',
  'agent-collaboration': 'skillManage.category.agent-collaboration',
  'product-management': 'skillManage.category.product-management',
  'financial-analysis': 'skillManage.category.financial-analysis',
  'design-visualization': 'skillManage.category.design-visualization',
  'cultural-creation': 'skillManage.category.cultural-creation',
  'document-processing': 'skillManage.category.document-processing',
  'skill-management': 'skillManage.category.skill-management',
  other: 'skillManage.category.other',
}

const categoryColors: Record<string, string> = {
  'content-creation': '#e74c3c',
  'video-creation': '#e67e22',
  'ecommerce-marketing': '#f39c12',
  'presentation': '#2ecc71',
  'digital-human': '#1abc9c',
  'document-analysis': '#3498db',
  'voice-audio': '#9b59b6',
  'agent-collaboration': '#8e44ad',
  'product-management': '#2980b9',
  'financial-analysis': '#16a085',
  'design-visualization': '#e91e63',
  'cultural-creation': '#ff7043',
  'document-processing': '#607d8b',
  'skill-management': '#795548',
  other: '#888',
}

async function fetchSkills() {
  loading.value = true
  try {
    skills.value = (await listSkillsApi()) ?? []
  } catch {
    toast.error(t('error.loadFailed', { entity: '' }))
  } finally {
    loading.value = false
  }
}

onMounted(fetchSkills)

function startImportAnimation() {
  currentStatusIndex.value = 0
  elapsedSeconds.value = 0
  currentTip.value = tips[Math.floor(Math.random() * tips.length)]

  statusTimer = setInterval(() => {
    currentStatusIndex.value = (currentStatusIndex.value + 1) % statusMessages.length
  }, 8000)

  elapsedTimer = setInterval(() => {
    elapsedSeconds.value++
  }, 1000)
}

function stopImportAnimation() {
  if (statusTimer) { clearInterval(statusTimer); statusTimer = null }
  if (elapsedTimer) { clearInterval(elapsedTimer); elapsedTimer = null }
}

onUnmounted(stopImportAnimation)

const formatElapsed = (seconds: number) => {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
}

function openUrlImport() {
  importUrl.value = ''
  showUrlModal.value = true
}

async function handleUrlImport() {
  if (!importUrl.value.trim()) {
    toast.warning(t('skillManage.urlRequired'))
    return
  }
  importingUrl.value = true
  startImportAnimation()
  try {
    await importFromUrlApi(importUrl.value.trim())
    toast.success(t('toast.createSuccess'))
    showUrlModal.value = false
    await fetchSkills()
  } catch { /* interceptor handles toast */ } finally {
    stopImportAnimation()
    importingUrl.value = false
  }
}

function openUpload() {
  uploadFile.value = null
  showUploadModal.value = true
}

function handleFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    uploadFile.value = file
  }
}

async function handleUpload() {
  if (!uploadFile.value) {
    toast.warning(t('skillManage.zipRequired'))
    return
  }
  if (!uploadFile.value.name.toLowerCase().endsWith('.zip')) {
    toast.warning(t('skillManage.zipOnly'))
    return
  }
  uploading.value = true
  try {
    await uploadSkillZipApi(uploadFile.value)
    toast.success(t('toast.uploadSuccess'))
    showUploadModal.value = false
    await fetchSkills()
  } catch { /* interceptor handles toast */ } finally {
    uploading.value = false
  }
}

function confirmDelete(skill: SkillDefinition) {
  deleteTarget.value = skill.name
  showDeleteDialog.value = true
}

async function handleDelete() {
  if (deleteTarget.value === null) return
  try {
    await deleteSkillApi(deleteTarget.value)
    toast.success(t('toast.deleteSuccess'))
    await fetchSkills()
  } catch { /* interceptor handles toast */ } finally {
    showDeleteDialog.value = false
    deleteTarget.value = null
  }
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader :title="$t('skillManage.title')" :description="$t('skillManage.desc')">
      <Button variant="outline" @click="openUrlImport">
        <Link class="mr-2 h-4 w-4" />{{ $t('skillManage.importUrl') }}
      </Button>
      <Button @click="openUpload">
        <Upload class="mr-2 h-4 w-4" />{{ $t('skillManage.upload') }}
      </Button>
    </PageHeader>

    <div v-if="loading" class="space-y-2">
      <Skeleton v-for="i in 3" :key="i" class="h-10 w-full" />
    </div>

    <EmptyState v-else-if="skills.length === 0" :title="$t('common.noData')" :description="$t('skillManage.emptyDesc')" />

    <div v-else class="border border-border rounded-lg overflow-hidden">
      <table class="w-full text-sm">
        <thead>
          <tr class="bg-muted/50 border-b border-border">
            <th class="text-left px-4 py-2.5 font-medium text-muted-foreground">{{ $t('skillManage.columns.name') }}</th>
            <th class="text-left px-4 py-2.5 font-medium text-muted-foreground">{{ $t('skillManage.columns.description') }}</th>
            <th class="text-left px-4 py-2.5 font-medium text-muted-foreground w-32">{{ $t('skillManage.columns.category') }}</th>
            <th class="text-left px-4 py-2.5 font-medium text-muted-foreground w-20">{{ $t('skillManage.columns.action') }}</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-border">
          <tr v-for="skill in skills" :key="skill.name" class="hover:bg-muted/30 transition-colors">
            <td class="px-4 py-2.5 font-medium">{{ skill.name }}</td>
            <td class="px-4 py-2.5 text-muted-foreground text-xs max-w-xs truncate" :title="skill.description">
              {{ skill.description }}
            </td>
            <td class="px-4 py-2.5">
              <span
                v-if="skill.category"
                class="inline-block px-2 py-0.5 rounded-full text-xs text-white"
                :style="{ background: categoryColors[skill.category] || '#888' }"
              >
                {{ $t(skillCategoryKeys[skill.category]) || skill.category }}
              </span>
              <span v-else class="text-xs text-muted-foreground">{{ $t('common.uncategorized') }}</span>
            </td>
            <td class="px-4 py-2.5">
              <Button variant="ghost" size="icon" class="h-8 w-8 text-destructive hover:text-destructive" @click="confirmDelete(skill)">
                <Trash2 class="h-4 w-4" />
              </Button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- URL Import Modal -->
    <Dialog :open="showUrlModal" @update:open="showUrlModal = $event">
      <DialogContent class="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{{ $t('skillManage.importUrl') }}</DialogTitle>
        </DialogHeader>

        <template v-if="!importingUrl">
          <div class="text-sm text-muted-foreground space-y-1 mb-4">
            <p>{{ $t('skillManage.urlImport.supportedFormats') }}</p>
            <p class="pl-3"><b>{{ $t('skillManage.urlImport.repoRoot') }}</b> — https://github.com/{owner}/{repo}</p>
            <p class="pl-3"><b>{{ $t('skillManage.urlImport.subtree') }}</b> — https://github.com/{owner}/{repo}/tree/main/skills/{name}</p>
          </div>
          <div class="space-y-2">
            <label class="text-sm font-medium">GitHub URL <span class="text-destructive">*</span></label>
            <Input v-model="importUrl" placeholder="https://github.com/{owner}/{repo}" />
          </div>
          <DialogFooter>
            <Button variant="outline" @click="showUrlModal = false">{{ $t('common.cancel') }}</Button>
            <Button @click="handleUrlImport">
              <Globe class="mr-2 h-4 w-4" />{{ $t('common.import') }}
            </Button>
          </DialogFooter>
        </template>

        <template v-else>
          <div class="text-center py-4">
            <Loader2 class="mx-auto h-10 w-10 animate-spin text-primary" />
            <div class="mt-4 text-2xl font-bold tabular-nums text-muted-foreground">
              {{ formatElapsed(elapsedSeconds) }}
            </div>
            <div class="mt-3 text-sm font-medium text-foreground">
              {{ statusMessages[currentStatusIndex] }}
            </div>
            <!-- Progress bar -->
            <div class="mt-4 w-full bg-muted rounded-full h-1.5 overflow-hidden">
              <div class="h-full bg-primary rounded-full animate-pulse" style="width: 60%" />
            </div>
            <div class="mt-4 bg-muted/50 rounded-lg p-3 text-left text-xs text-muted-foreground">
              <p class="font-medium text-foreground mb-1">💡 {{ currentTip }}</p>
              <p>{{ $t('skillManage.importStatus.timeoutNote') }}</p>
            </div>
            <p class="mt-4 text-xs text-muted-foreground">{{ $t('skillManage.importStatus.pleaseWait') }}</p>
          </div>
        </template>
      </DialogContent>
    </Dialog>

    <!-- ZIP Upload Modal -->
    <Dialog :open="showUploadModal" @update:open="showUploadModal = $event">
      <DialogContent class="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{{ $t('skillManage.upload') }}</DialogTitle>
        </DialogHeader>
        <div class="space-y-4">
          <div class="space-y-2">
            <label class="text-sm font-medium">{{ $t('skillManage.zipFileSelect') }} <span class="text-destructive">*</span></label>
            <input ref="fileInputRef" type="file" accept=".zip" class="hidden" @change="handleFileSelect" />
            <div class="flex items-center gap-2">
              <Button variant="outline" size="sm" @click="fileInputRef?.click()">
                <FileArchive class="mr-1 h-4 w-4" />{{ uploadFile ? uploadFile.name : $t('skillManage.selectFile') }}
              </Button>
              <span v-if="uploadFile" class="text-xs text-muted-foreground">{{ (uploadFile.size / 1024).toFixed(1) }} KB</span>
            </div>
          </div>
          <div class="text-xs text-muted-foreground space-y-1 bg-muted/30 rounded-lg p-3">
            <p>{{ $t('skillManage.zipFormat') }}</p>
            <pre class="mt-2 font-mono text-[11px] leading-relaxed">
my-skill/
  ├── SKILL.md          # {{ $t('skillManage.zipRequired') }}
  ├── assets/           # {{ $t('skillManage.zipOptional') }}
  └── examples/         # {{ $t('skillManage.zipOptional') }}

another-skill/
  └── SKILL.md          # {{ $t('skillManage.zipBatchHint') }}</pre>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="showUploadModal = false">{{ $t('common.cancel') }}</Button>
          <Button :loading="uploading" @click="handleUpload">{{ $t('common.upload') }}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <ConfirmDialog
      :open="showDeleteDialog"
      @update:open="showDeleteDialog = $event"
      :title="$t('dialog.deleteConfirm.title')"
      :description="$t('skillManage.confirmDelete')"
      :confirm-text="$t('common.delete')"
      @confirm="handleDelete"
    />
  </div>
</template>
