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
import type { SkillUploadResult } from '@/api/skill'
import type { SkillDefinition } from '@/types/api'
import {
  Upload,
  Link,
  Loader2,
  Trash2,
  FileArchive,
  Globe,
} from 'lucide-vue-next'
import { Badge } from '@/components/ui/badge'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'
import { Label } from '@/components/ui/label'
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
const uploadResult = ref<SkillUploadResult | null>(null)

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
  uploadResult.value = null
  try {
    const result = await uploadSkillZipApi(uploadFile.value)
    uploadResult.value = result
    if (result.failed.length === 0) {
      toast.success(t('skillManage.uploadAllSuccess', { count: result.successCount }))
      showUploadModal.value = false
      await fetchSkills()
    } else if (result.successCount === 0) {
      toast.error(result.failed[0].reason)
    } else {
      toast.success(t('skillManage.uploadPartialSuccess', { success: result.successCount, failed: result.failed.length }))
      await fetchSkills()
    }
  } catch { /* interceptor handles toast */ } finally {
    uploading.value = false
  }
}

function resetUpload() {
  uploadResult.value = null
  uploadFile.value = null
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
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{{ $t('skillManage.columns.name') }}</TableHead>
            <TableHead>{{ $t('skillManage.columns.description') }}</TableHead>
            <TableHead class="w-32">{{ $t('skillManage.columns.category') }}</TableHead>
            <TableHead class="w-20">{{ $t('skillManage.columns.action') }}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="skill in skills" :key="skill.name">
            <TableCell class="font-medium">{{ skill.name }}</TableCell>
            <TableCell class="max-w-xs truncate text-xs text-muted-foreground" :title="skill.description">
              {{ skill.description }}
            </TableCell>
            <TableCell>
              <Badge v-if="skill.category" variant="outline" class="text-xs">
                {{ $t(skillCategoryKeys[skill.category]) || skill.category }}
              </Badge>
              <span v-else class="text-xs text-muted-foreground">{{ $t('common.uncategorized') }}</span>
            </TableCell>
            <TableCell>
              <Button variant="ghost" size="icon" class="h-8 w-8 text-destructive hover:text-destructive" @click="confirmDelete(skill)">
                <Trash2 class="h-4 w-4" />
              </Button>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
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
            <Label for="skill-import-url">GitHub URL <span class="text-destructive">*</span></Label>
            <Input id="skill-import-url" name="skill-import-url" v-model="importUrl" placeholder="https://github.com/{owner}/{repo}" />
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

        <!-- Upload Form -->
        <template v-if="!uploadResult">
          <div class="space-y-4">
            <div class="space-y-2">
              <Label for="skill-zip-file">{{ $t('skillManage.zipFileSelect') }} <span class="text-destructive">*</span></Label>
              <input id="skill-zip-file" name="skill-zip-file" ref="fileInputRef" type="file" accept=".zip" class="hidden" @change="handleFileSelect" />
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
        </template>

        <!-- Upload Result -->
        <template v-else>
          <div class="space-y-4">
            <!-- Summary -->
            <div class="flex items-center gap-4 p-3 rounded-lg" :class="uploadResult.failed.length === 0 ? 'bg-green-50 text-green-800' : 'bg-amber-50 text-amber-800'">
              <div class="text-2xl font-bold">{{ uploadResult.successCount }}/{{ uploadResult.totalCount }}</div>
              <div class="text-sm">
                <p class="font-medium">{{ $t('skillManage.uploadResult') }}</p>
                <p class="text-xs opacity-80">{{ $t('skillManage.uploadResultHint', { success: uploadResult.successCount, failed: uploadResult.failed.length }) }}</p>
              </div>
            </div>

            <!-- Imported Skills -->
            <div v-if="uploadResult.imported.length > 0">
              <p class="text-sm font-medium text-green-700 mb-2">{{ $t('skillManage.importedSkills') }}</p>
              <div class="space-y-1">
                <div v-for="name in uploadResult.imported" :key="name" class="flex items-center gap-2 text-xs text-green-700 bg-green-50 rounded px-2 py-1">
                  <svg class="h-3.5 w-3.5 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/></svg>
                  {{ name }}
                </div>
              </div>
            </div>

            <!-- Failed Skills -->
            <div v-if="uploadResult.failed.length > 0">
              <p class="text-sm font-medium text-red-700 mb-2">{{ $t('skillManage.failedSkills') }}</p>
              <div class="space-y-1">
                <div v-for="item in uploadResult.failed" :key="item.name" class="flex items-start gap-2 text-xs text-red-700 bg-red-50 rounded px-2 py-1">
                  <svg class="h-3.5 w-3.5 shrink-0 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/></svg>
                  <span><strong>{{ item.name }}</strong>: {{ item.reason }}</span>
                </div>
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" @click="showUploadModal = false">{{ $t('common.close') }}</Button>
            <Button @click="resetUpload">{{ $t('common.retry') }}</Button>
          </DialogFooter>
        </template>
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
