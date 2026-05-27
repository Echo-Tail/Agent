<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useKnowledgeStore } from '@/stores/knowledge'
import { useAuthStore } from '@/stores/auth'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import SearchInput from '@/components/SearchInput.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Plus,
  ArrowLeft,
  Upload,
  FileText,
  Trash2,
  Eye,
  Pencil,
  Library,
  BookMarked,
} from 'lucide-vue-next'
import { toast } from 'sonner'
import DocPreview from '@/components/DocPreview.vue'
import type { KnowledgeDocument } from '@/types/knowledge'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const store = useKnowledgeStore()
const { t, locale } = useI18n()

const showKbModal = ref(false)
const editingKb = ref<{ id?: number; name: string; description: string }>({ name: '', description: '' })
const isEditMode = ref(false)
const savingKb = ref(false)

const previewDoc = ref<KnowledgeDocument | null>(null)
const showPreview = ref(false)

const uploadLoading = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

const showDeleteKbDialog = ref(false)
const deleteKbTarget = ref<number | null>(null)
const showDeleteDocDialog = ref(false)
const deleteDocTarget = ref<number | null>(null)

let searchTimer: ReturnType<typeof setTimeout> | undefined

const isDetail = computed(() => !!route.params.id)
const kbId = computed(() => Number(route.params.id))

const isUnmounted = ref(false)
onUnmounted(() => { isUnmounted.value = true })

onMounted(async () => {
  try {
    if (isDetail.value) {
      await store.loadKb(kbId.value)
    } else {
      await store.fetchKbs()
    }
  } catch { /* handled in store */ }
})

watch(
  () => route.params.id,
  async (newId) => {
    if (isUnmounted.value) return
    try {
      if (newId) {
        store.clearCurrent()
        await store.loadKb(Number(newId))
      } else {
        store.clearCurrent()
        await store.fetchKbs()
      }
    } catch { /* handled in store */ }
  },
)

function openCreate() {
  editingKb.value = { name: '', description: '' }
  isEditMode.value = false
  showKbModal.value = true
}

function openEdit(kb: { id: number; name: string; description?: string }) {
  editingKb.value = { id: kb.id, name: kb.name, description: kb.description || '' }
  isEditMode.value = true
  showKbModal.value = true
}

async function handleSaveKb() {
  if (!editingKb.value.name.trim()) {
    toast.warning(t('knowledge.kbNameRequired'))
    return
  }
  savingKb.value = true
  try {
    if (isEditMode.value && editingKb.value.id) {
      await store.updateKb(editingKb.value.id, {
        name: editingKb.value.name,
        description: editingKb.value.description || undefined,
      })
      toast.success(t('toast.updateSuccess'))
    } else {
      const kb = await store.createKb({
        name: editingKb.value.name,
        description: editingKb.value.description || undefined,
      })
      toast.success(t('toast.createSuccess'))
      if (kb) {
        router.push({ name: 'KnowledgeDetail', params: { id: kb.id } })
      }
    }
    showKbModal.value = false
  } catch { /* interceptor handles toast */ } finally {
    savingKb.value = false
  }
}

function confirmDeleteKb(kb: { id: number; name: string }) {
  deleteKbTarget.value = kb.id
  showDeleteKbDialog.value = true
}

async function handleDeleteKb() {
  if (deleteKbTarget.value === null) return
  try {
    await store.removeKb(deleteKbTarget.value)
    toast.success(t('toast.deleteSuccess'))
    if (isDetail.value) {
      router.push({ name: 'KnowledgeBase' })
    }
  } catch { /* interceptor handles toast */ }
  showDeleteKbDialog.value = false
  deleteKbTarget.value = null
}

function selectFiles() {
  fileInputRef.value?.click()
}

async function handleFileSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files
  if (!files || files.length === 0) return
  const fileList = Array.from(files)
  uploadLoading.value = true
  try {
    const docs = await store.uploadDocs(kbId.value, fileList)
    if (docs.length === 1) {
      toast.success(t('knowledge.uploadSuccess', { name: docs[0].fileName }))
    } else {
      toast.success(t('knowledge.uploadSuccessBatch', { count: docs.length }))
    }
  } catch { /* interceptor handles toast */ } finally {
    uploadLoading.value = false
    input.value = ''
  }
}

function confirmDeleteDoc(docId: number) {
  deleteDocTarget.value = docId
  showDeleteDocDialog.value = true
}

async function handleDeleteDoc() {
  if (deleteDocTarget.value === null) return
  try {
    await store.removeDoc(kbId.value, deleteDocTarget.value)
    toast.success(t('toast.deleteSuccess'))
  } catch { /* interceptor handles toast */ }
  showDeleteDocDialog.value = false
  deleteDocTarget.value = null
}

function openPreview(doc: KnowledgeDocument) {
  previewDoc.value = doc
  showPreview.value = true
}

function handleSearchInput(val: string) {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => store.search(val), 300)
}

function formatSize(chars: number | null | undefined) {
  if (chars == null) return '-'
  const suffix = t('common.chars')
  if (chars < 1000) return `${chars} ${suffix}`
  if (chars < 1000000) return `${(chars / 1000).toFixed(1)}K ${suffix}`
  return `${(chars / 1000000).toFixed(1)}M ${suffix}`
}

function formatDate(dateStr: string) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  const loc = locale.value || 'zh-CN'
  return d.toLocaleDateString(loc, { year: 'numeric', month: '2-digit', day: '2-digit' })
    + ' ' + d.toLocaleTimeString(loc, { hour: '2-digit', minute: '2-digit' })
}
</script>

<template>
  <!-- ====== LIST VIEW ====== -->
  <div v-if="!isDetail" class="space-y-6">
    <PageHeader :title="$t('knowledge.title')" :description="$t('knowledge.desc')">
      <SearchInput
        :model-value="store.searchQuery"
        @update:model-value="(val: string | number) => handleSearchInput(String(val))"
        :placeholder="$t('knowledge.searchPlaceholder')"
        input-class="w-60 h-9 pl-8"
      />
      <Button v-if="authStore.isAdmin" @click="openCreate">
        <Plus class="mr-2 h-4 w-4" />{{ $t('knowledge.createKb') }}
      </Button>
    </PageHeader>

    <!-- Search Results -->
    <template v-if="store.isSearching">
      <div class="text-sm font-medium text-muted-foreground">{{ $t('common.search') }} ({{ store.searchResults.length }})</div>
      <div v-if="store.searchResults.length" class="space-y-2">
        <div
          v-for="doc in store.searchResults"
          :key="doc.id"
          class="flex items-center justify-between p-3 border border-border rounded-lg hover:bg-muted/50 cursor-pointer transition-colors"
          @click="openPreview(doc)"
        >
          <div class="flex-1 min-w-0">
            <div class="text-sm font-medium">{{ doc.fileName }}</div>
            <div class="text-xs text-muted-foreground mt-0.5">
              {{ $t('knowledge.title') }} #{{ doc.knowledgeBaseId }}
              <span v-if="doc.content" class="ml-2 line-clamp-1">{{ doc.content }}</span>
            </div>
          </div>
          <Badge variant="outline" class="text-xs shrink-0 ml-2">{{ doc.fileType }}</Badge>
        </div>
      </div>
      <div v-else class="text-center py-8 text-muted-foreground text-sm">{{ $t('knowledge.noSearchResults') }}</div>
    </template>

    <!-- KB List -->
    <template v-else>
      <div v-if="store.loading" class="grid gap-4 md:grid-cols-2">
        <div v-for="i in 4" :key="i">
          <Card><CardContent class="p-5"><Skeleton class="h-16 w-full" /></CardContent></Card>
        </div>
      </div>
      <EmptyState
        v-else-if="store.kbs.length === 0"
        :icon="Library"
        :title="authStore.isAdmin ? $t('knowledge.noKb') : $t('knowledge.noKbUser')"
        :description="authStore.isAdmin ? $t('knowledge.noKbDesc') : undefined"
      />
      <div v-else class="grid gap-3 grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
        <Card
          v-for="kb in store.kbs"
          :key="kb.id"
          class="group relative cursor-pointer hover:shadow-md transition-shadow"
          @click="router.push({ name: 'KnowledgeDetail', params: { id: kb.id } })"
        >
          <div
            v-if="authStore.isAdmin"
            class="absolute top-1 right-1 flex items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity"
            @click.stop
          >
            <Button variant="ghost" size="icon" class="h-7 w-7" @click="openEdit(kb)">
              <Pencil class="h-3.5 w-3.5" />
            </Button>
            <Button variant="ghost" size="icon" class="h-7 w-7 text-destructive hover:text-destructive" @click="confirmDeleteKb(kb)">
              <Trash2 class="h-3.5 w-3.5" />
            </Button>
          </div>
          <CardContent class="p-4">
            <div class="flex flex-col items-center text-center gap-2">
              <div class="flex h-10 w-10 items-center justify-center rounded-full bg-primary/10 text-primary">
                <BookMarked class="h-5 w-5" />
              </div>
              <h3 class="font-semibold text-sm line-clamp-1 w-full">{{ kb.name }}</h3>
              <p v-if="kb.description" class="text-xs text-muted-foreground line-clamp-2 w-full min-h-[2rem]">{{ kb.description }}</p>
            </div>
          </CardContent>
        </Card>
      </div>
    </template>

    <!-- KB Create/Edit Modal -->
    <Dialog :open="showKbModal" @update:open="showKbModal = $event">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{{ isEditMode ? $t('knowledge.editKb') : $t('knowledge.createKb') }}</DialogTitle>
        </DialogHeader>
        <div class="space-y-4">
          <div class="space-y-2">
            <label for="knowledge-base-name" class="text-sm font-medium">{{ $t('knowledge.kbName') }} <span class="text-destructive">*</span></label>
            <Input id="knowledge-base-name" name="knowledge-base-name" v-model="editingKb.name" :placeholder="$t('placeholder.kbName')" maxlength="100" />
          </div>
          <div class="space-y-2">
            <label for="knowledge-base-description" class="text-sm font-medium">{{ $t('knowledge.kbDescription') }}</label>
            <textarea
              id="knowledge-base-description"
              name="knowledge-base-description"
              v-model="editingKb.description"
              :placeholder="$t('placeholder.kbDescription')"
              class="flex min-h-[80px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              maxlength="500"
            />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="showKbModal = false">{{ $t('common.cancel') }}</Button>
          <Button :loading="savingKb" @click="handleSaveKb">
            {{ isEditMode ? $t('common.save') : $t('common.create') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <ConfirmDialog
      :open="showDeleteKbDialog"
      @update:open="showDeleteKbDialog = $event"
      :title="$t('dialog.deleteKb.title')"
      :description="$t('dialog.deleteKb.desc')"
      :confirm-text="$t('common.delete')"
      @confirm="handleDeleteKb"
    />
  </div>

  <!-- ====== DETAIL VIEW ====== -->
  <div v-else class="space-y-6">
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-3">
        <Button variant="outline" size="sm" @click="router.push({ name: 'KnowledgeBase' })">
          <ArrowLeft class="mr-1 h-4 w-4" />{{ $t('common.back') }}
        </Button>
        <h2 v-if="store.currentKb" class="text-xl font-bold tracking-tight">{{ store.currentKb.name }}</h2>
        <Skeleton v-else class="h-7 w-32" />
      </div>
      <div v-if="authStore.isAdmin && store.currentKb" class="flex items-center gap-2">
        <Button variant="outline" size="sm" @click="openEdit(store.currentKb)">
          <Pencil class="mr-1 h-3.5 w-3.5" />{{ $t('common.edit') }}
        </Button>
        <Button variant="destructive" size="sm" @click="confirmDeleteKb(store.currentKb)">
          <Trash2 class="mr-1 h-3.5 w-3.5" />{{ $t('common.delete') }}
        </Button>
      </div>
    </div>

    <p v-if="store.currentKb?.description" class="text-sm text-muted-foreground -mt-3">
      {{ store.currentKb.description }}
    </p>

    <div v-if="store.loading" class="space-y-4">
      <Skeleton class="h-24 w-full" />
      <Skeleton class="h-8 w-full" />
    </div>

    <template v-else-if="store.currentKb">
      <!-- Upload -->
      <div
        class="border-2 border-dashed border-border rounded-lg p-8 text-center hover:border-primary/50 transition-colors cursor-pointer"
        @click="selectFiles"
      >
          <input
            id="knowledge-document-upload"
            name="knowledge-document-upload"
            ref="fileInputRef"
          type="file"
          accept=".txt,.md,.pdf,.docx,.xlsx,.csv,.json"
          class="hidden"
          multiple
          @change="handleFileSelected"
        />
        <Upload class="mx-auto h-10 w-10 text-muted-foreground/60" />
        <p class="mt-3 text-sm font-medium">{{ $t('knowledge.uploadArea') }}</p>
        <p class="text-xs text-muted-foreground mt-1">{{ $t('knowledge.uploadFormats') }}</p>
      </div>

      <!-- Document list -->
      <div>
        <h3 class="text-sm font-semibold mb-3">{{ $t('knowledge.docList', { count: store.documents.length }) }}</h3>
        <div v-if="store.documents.length" class="border border-border rounded-lg divide-y divide-border">
          <div
            v-for="doc in store.documents"
            :key="doc.id"
            class="flex items-center gap-3 px-4 py-3 hover:bg-muted/30 transition-colors"
          >
            <FileText class="h-5 w-5 text-muted-foreground shrink-0" />
            <div class="flex-1 min-w-0">
              <div class="text-sm font-medium truncate">{{ doc.fileName }}</div>
              <div class="text-xs text-muted-foreground">
                {{ doc.fileType }} · {{ formatSize(doc.charCount) }} · {{ formatDate(doc.uploadedAt) }}
              </div>
            </div>
            <div class="flex items-center gap-1 shrink-0">
              <Button variant="ghost" size="sm" class="h-8 text-xs" @click="openPreview(doc)">
                <Eye class="h-3.5 w-3.5 mr-1" />{{ $t('common.preview') }}
              </Button>
              <Button variant="ghost" size="icon" class="h-8 w-8 text-destructive hover:text-destructive" @click="confirmDeleteDoc(doc.id)">
                <Trash2 class="h-4 w-4" />
              </Button>
            </div>
          </div>
        </div>
        <div v-else class="text-center py-8 text-sm text-muted-foreground border border-dashed border-border rounded-lg">
          {{ $t('knowledge.noDocs') }}
        </div>
      </div>
    </template>

    <ConfirmDialog
      :open="showDeleteDocDialog"
      @update:open="showDeleteDocDialog = $event"
      :title="$t('dialog.deleteDoc.title')"
      :description="$t('dialog.deleteDoc.desc')"
      :confirm-text="$t('common.delete')"
      @confirm="handleDeleteDoc"
    />

    <!-- Doc Preview -->
    <DocPreview
      v-if="previewDoc"
      :doc="previewDoc"
      :show="showPreview"
      @update:show="showPreview = $event"
    />
  </div>
</template>
