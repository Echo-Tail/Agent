<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import PageHeader from '@/components/PageHeader.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Card, CardContent } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Search, Plus, Copy, Trash2, Pencil, Eye, Image, Loader2, Upload } from 'lucide-vue-next'
import { VisuallyHidden } from 'reka-ui'
import {
  listPrompts, createPrompt as createPromptApi, updatePrompt,
  setCoverRef, deletePrompt,
  type PromptLibrary,
} from '@/api/prompts'
import { listSpaces, listAssets } from '@/api/assets'
import type { PageResponse } from '@/api/assets'
import { listImageRecords } from '@/api/image'
import { toast } from 'sonner'

const { t } = useI18n()
const auth = useAuthStore()

// ── Categories ──
const categories = [
  { value: '车载主机', label: '车载主机' },
  { value: '扬声器', label: '扬声器' },
  { value: '低音炮', label: '低音炮' },
  { value: '功放', label: '功放' },
  { value: 'DSP', label: 'DSP' },
  { value: '显示屏', label: '显示屏' },
  { value: '摄像头', label: '摄像头' },
  { value: '线材配件', label: '线材配件' },
  { value: '安装支架', label: '安装支架' },
]

// ── Data ──
const prompts = ref<PromptLibrary[]>([])
const loading = ref(false)
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)

// ── Search / Filter ──
const keyword = ref('')
const filterCategory = ref<string | undefined>(undefined)
const filterCreator = ref<'all' | 'mine' | 'others'>('all')

// Copied feedback
const copiedId = ref<number | null>(null)

// History search
const historyKeyword = ref('')

async function doSearch() {
  page.value = 0
  await loadPrompts()
}

function clearSearch() {
  keyword.value = ''
  filterCategory.value = undefined
  filterCreator.value = 'all'
  page.value = 0
  loadPrompts()
}

// ── Create dialog ──
const coverInput = ref<HTMLInputElement | null>(null)
const editCoverInput = ref<HTMLInputElement | null>(null)

const createOpen = ref(false)
const createPrompt = ref('')
const createCategory = ref('')
const createTags = ref('')
const createCoverFile = ref<File | undefined>(undefined)
const createCoverPreview = ref<string | undefined>(undefined)
const createSourceType = ref<'upload' | 'asset' | 'history'>('upload')
const createBusy = ref(false)

// Asset picker state
const assetPickerOpen = ref(false)
const assetSpaces = ref<{ id: number; name: string }[]>([])
const assetSpaceId = ref<number | undefined>(undefined)
const assetList = ref<any[]>([])
const assetLoading = ref(false)

// History picker state
const historyPickerOpen = ref(false)
const historyList = ref<any[]>([])
const historyLoading = ref(false)

function resetCreateForm() {
  createPrompt.value = ''
  createCategory.value = ''
  createTags.value = ''
  createCoverFile.value = undefined
  if (createCoverPreview.value) {
    URL.revokeObjectURL(createCoverPreview.value)
  }
  createCoverPreview.value = undefined
  createSourceType.value = 'upload'
  createBusy.value = false
}

function openCreateDialog() {
  resetCreateForm()
  createOpen.value = true
}

async function handleCreate() {
  if (!createPrompt.value.trim() || !createCategory.value || createBusy.value) return
  createBusy.value = true
  try {
    const fd = new FormData()
    fd.append('prompt', createPrompt.value.trim())
    fd.append('category', createCategory.value)
    if (createTags.value.trim()) fd.append('tags', createTags.value.trim())
    if (createCoverFile.value) fd.append('cover', createCoverFile.value)
    const created = await createPromptApi(fd)
    // 如果选择了素材库或生图历史的封面，创建后调用 setCoverRef
    if (pickedCoverPath.value && created?.id) {
      await setCoverRef(created.id, pickedCoverPath.value)
    }
    toast.success(t('promptLibrary.created'))
    createOpen.value = false
    resetCreateForm()
    await loadPrompts()
  } catch { /* toast handled */ }
  finally { createBusy.value = false }
}

// ── Cover source: upload ──
function onCoverFileSelected(e: Event) {
  const input = e.target as HTMLInputElement
  if (!input.files?.length) return
  const file = input.files[0]
  createCoverFile.value = file
  if (createCoverPreview.value) URL.revokeObjectURL(createCoverPreview.value)
  createCoverPreview.value = URL.createObjectURL(file)
  input.value = ''
}

// ── Cover source: from asset library ──
async function openAssetPicker() {
  createSourceType.value = 'asset'
  assetPickerOpen.value = true
  try {
    assetSpaces.value = await listSpaces()
  } catch { /* ignore */ }
  loadAssetsForPicker()
}

async function loadAssetsForPicker() {
  assetLoading.value = true
  try {
    const sid = assetSpaceId.value === (undefined as any) ? undefined : assetSpaceId.value
    const res: PageResponse<any> = await listAssets({ spaceId: sid, page: 0, size: 50 })
    assetList.value = res.content ?? []
  } catch { /* ignore */ }
  finally { assetLoading.value = false }
}

// Watch space filter for dynamic reload
watch(assetSpaceId, () => {
  if (assetPickerOpen.value) loadAssetsForPicker()
})

function selectAssetCover(asset: any) {
  if (createCoverPreview.value) URL.revokeObjectURL(createCoverPreview.value)
  // Store the coverFile as undefined — we'll set it via cover-ref
  createCoverFile.value = undefined
  createCoverPreview.value = imageUrl(asset.filePath)
  // For create: store path to be set after creation
  // Actually for create we need to handle this differently...
  // For now in create mode via multipart we need a file. Let's just close and handle it.
  assetPickerOpen.value = false
  // We'll handle this via the cover-ref endpoint after creation
  // For simplicity: if in create mode and we pick from asset, store the path
  // Actually let me rethink - for create dialog, using cover-ref requires two steps.
  // Simplest approach: Create first, then immediately call setCoverRef
  // Store the picked path for post-create
  pickedCoverPath.value = createCoverPreview.value ? createCoverPreview.value.replace('/uploads/', '') : null
  toast.success(t('promptLibrary.coverSelected'))
}

// For tracking picked cover from asset/history
const pickedCoverPath = ref<string | null>(null)

// Actually, let me simplify: the create button handler will check if we picked a cover from asset/history
// and call setCoverRef after creation

// ── Cover source: from generation history ──
/** 清理 resultPath：去除前导 ./uploads/ 或 /uploads/ 避免路径双拼 */
function cleanResultPath(p: string | null | undefined): string {
  if (!p) return ''
  return p.replace(/\\/g, '/')
    .replace(/^\/uploads\//, '')
    .replace(/^\.\/uploads\//, '')
    .replace(/^\.\//, '')
}

async function openHistoryPicker() {
  createSourceType.value = 'history'
  historyPickerOpen.value = true
  historyKeyword.value = ''
  historyLoading.value = true
  try {
    const res: any = await listImageRecords({ page: 0, size: 8 })
    if (res.content) historyList.value = res.content
    else if (res.data?.content) historyList.value = res.data.content
    else historyList.value = []
  } catch { /* ignore */ }
  finally { historyLoading.value = false }
}

async function searchHistory() {
  historyLoading.value = true
  try {
    const kw = historyKeyword.value.trim() || undefined
    const res: any = await listImageRecords({ page: 0, size: 8, prompt: kw })
    if (res.content) historyList.value = res.content
    else if (res.data?.content) historyList.value = res.data.content
    else historyList.value = []
  } catch { /* ignore */ }
  finally { historyLoading.value = false }
}

function selectHistoryCover(record: any) {
  if (createCoverPreview.value) URL.revokeObjectURL(createCoverPreview.value)
  createCoverFile.value = undefined
  const path = cleanResultPath(record.resultPath)
  createCoverPreview.value = imageUrl(record.resultPath)
  pickedCoverPath.value = path
  historyPickerOpen.value = false
  toast.success(t('promptLibrary.coverSelected'))
}

// ── Edit dialog ──
const editOpen = ref(false)
const editTarget = ref<PromptLibrary | null>(null)
const editPrompt = ref('')
const editCategory = ref('')
const editTags = ref('')
const editCoverFile = ref<File | undefined>(undefined)
const editCoverPreview = ref<string | undefined>(undefined)
const editBusy = ref(false)

function openEditDialog(p: PromptLibrary) {
  editTarget.value = p
  editPrompt.value = p.prompt
  editCategory.value = p.category
  editTags.value = p.tags ?? ''
  editCoverFile.value = undefined
  if (p.coverPath) {
    editCoverPreview.value = imageUrl(p.coverPath)
  } else {
    editCoverPreview.value = undefined
  }
  editOpen.value = true
}

async function handleEdit() {
  if (!editTarget.value || !editPrompt.value.trim() || !editCategory.value || editBusy.value) return
  editBusy.value = true
  try {
    const fd = new FormData()
    fd.append('prompt', editPrompt.value.trim())
    fd.append('category', editCategory.value)
    if (editTags.value.trim()) fd.append('tags', editTags.value.trim())
    if (editCoverFile.value) fd.append('cover', editCoverFile.value)
    await updatePrompt(editTarget.value.id, fd)
    toast.success(t('promptLibrary.updated'))
    editOpen.value = false
    await loadPrompts()
  } catch { /* toast handled */ }
  finally { editBusy.value = false }
}

function onEditCoverSelected(e: Event) {
  const input = e.target as HTMLInputElement
  if (!input.files?.length) return
  const file = input.files[0]
  editCoverFile.value = file
  if (editCoverPreview.value) URL.revokeObjectURL(editCoverPreview.value)
  editCoverPreview.value = URL.createObjectURL(file)
  input.value = ''
}

// Edit cover from asset/history
const editPickedCoverPath = ref<string | null>(null)

async function openEditAssetPicker() {
  // 不关闭编辑对话框，两个 Dialog Portal 同时打开时后打开的在上面
  assetSpaceId.value = undefined
  assetPickerOpen.value = true
  assetLoading.value = true
  try { assetSpaces.value = await listSpaces() } catch { /* ignore */ }
  loadAssetsForPicker()
}

async function openEditHistoryPicker() {
  historyKeyword.value = ''
  historyPickerOpen.value = true
  historyLoading.value = true
  try {
    const res: any = await listImageRecords({ page: 0, size: 8 })
    if (res.content) historyList.value = res.content
    else if (res.data?.content) historyList.value = res.data.content
    else historyList.value = []
  } catch { /* ignore */ }
  finally { historyLoading.value = false }
}

// Override selectAssetCover behavior for edit mode: call setCoverRef immediately
function selectEditAssetCover(asset: any) {
  const path = asset.filePath?.replace(/\\/g, '/').replace(/^\.\//, '')
  editCoverFile.value = undefined
  editCoverPreview.value = imageUrl(asset.filePath ?? '')
  assetPickerOpen.value = false
  editPickedCoverPath.value = path
  if (editTarget.value) {
    setCoverRef(editTarget.value.id, path).then(() => {
      toast.success(t('promptLibrary.coverSelected'))
    }).catch(() => {})
  } else {
    toast.success(t('promptLibrary.coverSelected'))
  }
}

function selectEditHistoryCover(record: any) {
  const path = cleanResultPath(record.resultPath)
  editCoverFile.value = undefined
  editCoverPreview.value = imageUrl(record.resultPath)
  historyPickerOpen.value = false
  editPickedCoverPath.value = path
  if (editTarget.value) {
    setCoverRef(editTarget.value.id, path).then(() => {
      toast.success(t('promptLibrary.coverSelected'))
    }).catch(() => {})
  } else {
    toast.success(t('promptLibrary.coverSelected'))
  }
}

// ── Delete confirmation ──
const deleteOpen = ref(false)
const deleteTarget = ref<PromptLibrary | null>(null)
const deleteBusy = ref(false)

function openDeleteDialog(p: PromptLibrary) {
  deleteTarget.value = p
  deleteOpen.value = true
}

async function handleDelete() {
  if (!deleteTarget.value || deleteBusy.value) return
  deleteBusy.value = true
  try {
    await deletePrompt(deleteTarget.value.id)
    toast.success(t('promptLibrary.deleted'))
    deleteOpen.value = false
    deleteTarget.value = null
    await loadPrompts()
  } catch { /* toast handled */ }
  finally { deleteBusy.value = false }
}

// ── Detail view ──
const detailOpen = ref(false)
const detailTarget = ref<PromptLibrary | null>(null)

function openDetailView(p: PromptLibrary) {
  detailTarget.value = p
  detailOpen.value = true
}

// ── Copy prompt ──
async function copyPrompt(text: string, id: number) {
  try {
    await navigator.clipboard.writeText(text)
    copiedId.value = id
    setTimeout(() => { copiedId.value = null }, 1500)
  } catch {
    toast.error(t('common.failed'))
  }
}

// ── Permissions ──
const isAdmin = computed(() => auth.isAdmin)
const currentUserId = computed(() => auth.currentUser?.id)

function canModify(p: PromptLibrary): boolean {
  return p.createdBy === currentUserId.value || isAdmin.value
}

// ── Data loading ──

onMounted(async () => {
  await loadPrompts()
})

async function loadPrompts() {
  loading.value = true
  try {
    let createdBy: number | undefined
    let excludeUser: number | undefined
    if (filterCreator.value === 'mine') {
      createdBy = currentUserId.value
    } else if (filterCreator.value === 'others') {
      excludeUser = currentUserId.value
    }
    const cat = filterCategory.value === '__all__' ? undefined : filterCategory.value
    const res: PageResponse<PromptLibrary> = await listPrompts({
      category: cat,
      createdBy,
      excludeUser,
      keyword: keyword.value || undefined,
      page: page.value,
      size: 20,
    })
    prompts.value = res.content ?? []
    totalPages.value = res.page?.totalPages ?? 0
    totalElements.value = res.page?.totalElements ?? 0
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function goToPage(p: number) {
  if (p < 0 || p >= totalPages.value) return
  page.value = p
  loadPrompts()
}

function imageUrl(path: string): string {
  if (!path) return ''
  const n = path.replace(/\\/g, '/').replace(/^\.\//, '')
  if (n.startsWith('/uploads/')) return n
  return '/uploads/' + n
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader :title="$t('pageTitle.promptLibrary')" :description="$t('pageTitle.promptLibraryDesc')" />

    <!-- ── Search toolbar ── -->
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div class="flex items-center gap-2 flex-wrap">
        <div class="relative">
          <Search class="absolute left-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input v-model="keyword" :placeholder="$t('promptLibrary.searchPlaceholder')" class="pl-8 w-44" @keyup.enter="doSearch" />
        </div>
        <Select v-model="filterCategory">
          <SelectTrigger class="w-32"><SelectValue :placeholder="$t('promptLibrary.allCategories')" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="__all__">{{ $t('promptLibrary.allCategories') }}</SelectItem>
            <SelectItem v-for="c in categories" :key="c.value" :value="c.value">{{ c.label }}</SelectItem>
          </SelectContent>
        </Select>
        <Select v-model="filterCreator">
          <SelectTrigger class="w-28"><SelectValue :placeholder="$t('promptLibrary.all')" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{{ $t('promptLibrary.all') }}</SelectItem>
            <SelectItem value="mine">{{ $t('promptLibrary.mine') }}</SelectItem>
            <SelectItem value="others">{{ $t('promptLibrary.others') }}</SelectItem>
          </SelectContent>
        </Select>
        <Button variant="outline" size="sm" @click="doSearch">{{ $t('promptLibrary.search') }}</Button>
        <Button v-if="keyword || filterCategory || filterCreator !== 'all'" variant="ghost" size="sm" @click="clearSearch">
          {{ $t('promptLibrary.clear') }}
        </Button>
      </div>
      <Button size="sm" @click="openCreateDialog()">
        <Plus class="mr-1 h-4 w-4" />
        {{ $t('promptLibrary.newPrompt') }}
      </Button>
    </div>

    <!-- ── Prompt count ── -->
    <p v-if="!loading && totalElements > 0" class="text-xs text-muted-foreground">
      {{ $t('promptLibrary.total', { n: totalElements }) }}
    </p>

    <!-- ── Loading ── -->
    <div v-if="loading" class="flex justify-center py-16">
      <Loader2 class="h-8 w-8 animate-spin text-muted-foreground" />
    </div>

    <!-- ── Empty state ── -->
    <div v-else-if="prompts.length === 0" class="flex flex-col items-center py-16 text-muted-foreground">
      <Image class="h-16 w-16 mb-3 opacity-30" />
      <p>{{ $t('promptLibrary.noPrompts') }}</p>
    </div>

    <!-- ── Masonry grid ── -->
    <!-- 卡片网格：CSS Grid 4 列 -->
    <div v-else class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
      <Card
        v-for="p in prompts"
        :key="p.id"
        class="overflow-hidden group pt-0"
      >
        <!-- Cover（固定 4:3 裁剪区域，object-cover 按比例填满） -->
        <div class="relative bg-muted/30 overflow-hidden aspect-[4/3]">
          <img
            v-if="p.coverPath"
            :src="imageUrl(p.coverPath)"
            class="absolute inset-0 w-full h-full object-cover"
            :alt="p.prompt"
            loading="lazy"
          />
          <div v-else class="absolute inset-0 flex items-center justify-center text-muted-foreground/40">
            <Image class="h-10 w-10" />
          </div>
        </div>

        <CardContent class="p-3 space-y-2">
          <!-- Prompt (truncated) -->
          <p class="text-xs leading-relaxed line-clamp-4">{{ p.prompt }}</p>

          <!-- Meta -->
          <div class="flex flex-wrap gap-1">
            <span class="text-[10px] px-1.5 py-0.5 rounded-full bg-primary/10 text-primary/80">{{ p.category }}</span>
            <span v-if="p.tags" class="text-[10px] px-1.5 py-0.5 rounded-full bg-muted text-muted-foreground">{{ p.tags }}</span>
          </div>

          <!-- Actions -->
          <div class="flex gap-1 pt-1">
            <Button variant="ghost" size="sm" class="h-7 w-7 p-0 text-muted-foreground hover:text-primary" @click="copyPrompt(p.prompt, p.id)">
              <span v-if="copiedId === p.id" class="h-3.5 w-3.5 flex items-center justify-center text-green-500">✓</span>
              <Copy v-else class="h-3.5 w-3.5" />
            </Button>
            <Button variant="ghost" size="sm" class="h-7 w-7 p-0 text-muted-foreground" @click="openDetailView(p)">
              <Eye class="h-3.5 w-3.5" />
            </Button>
            <Button
              v-if="canModify(p)"
              variant="ghost" size="sm" class="h-7 w-7 p-0 text-muted-foreground hover:text-primary"
              @click="openEditDialog(p)"
            >
              <Pencil class="h-3.5 w-3.5" />
            </Button>
            <Button
              v-if="canModify(p)"
              variant="ghost" size="sm" class="h-7 w-7 p-0 text-muted-foreground hover:text-destructive"
              @click="openDeleteDialog(p)"
            >
              <Trash2 class="h-3.5 w-3.5" />
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>

    <!-- ── Pagination ── -->
    <div v-if="totalPages > 1" class="flex justify-center gap-2">
      <Button variant="outline" size="sm" :disabled="page <= 0" @click="goToPage(page - 1)">{{ $t('common.prev') }}</Button>
      <span class="flex items-center text-sm text-muted-foreground">{{ page + 1 }} / {{ totalPages }}</span>
      <Button variant="outline" size="sm" :disabled="page >= totalPages - 1" @click="goToPage(page + 1)">{{ $t('common.next') }}</Button>
    </div>

    <!-- ══════════ CREATE DIALOG ══════════ -->
    <Dialog v-model:open="createOpen">
      <DialogContent class="sm:max-w-[520px]" aria-describedby="create-prompt-desc">
        <DialogHeader><DialogTitle>{{ $t('promptLibrary.newPrompt') }}</DialogTitle></DialogHeader>
        <VisuallyHidden><div id="create-prompt-desc">{{ $t('promptLibrary.newPrompt') }}</div></VisuallyHidden>
        <div class="space-y-4 py-2">
          <div class="space-y-1.5">
            <label class="text-xs font-medium">{{ $t('promptLibrary.prompt') }}</label>
            <Textarea v-model="createPrompt" :placeholder="$t('promptLibrary.promptPlaceholder')" rows="4" class="max-h-[120px] overflow-y-auto" />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div class="space-y-1.5">
              <label class="text-xs font-medium">{{ $t('promptLibrary.category') }}</label>
              <Select v-model="createCategory">
                <SelectTrigger><SelectValue :placeholder="$t('promptLibrary.selectCategory')" /></SelectTrigger>
                <SelectContent>
                  <SelectItem v-for="c in categories" :key="c.value" :value="c.value">{{ c.label }}</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div class="space-y-1.5">
              <label class="text-xs font-medium">{{ $t('promptLibrary.tags') }}</label>
              <Input v-model="createTags" :placeholder="$t('promptLibrary.tagsPlaceholder')" />
            </div>
          </div>

          <!-- Cover -->
          <div class="space-y-1.5">
            <label class="text-xs font-medium">{{ $t('promptLibrary.cover') }}</label>
            <div v-if="createCoverPreview" class="relative w-36 h-24 rounded-md overflow-hidden bg-muted/30 mb-2">
              <img :src="createCoverPreview" class="w-full h-full object-cover" alt="" />
              <button
                class="absolute top-1 right-1 h-5 w-5 rounded-full bg-black/50 text-white flex items-center justify-center text-xs cursor-pointer"
                @click="createCoverPreview = undefined; createCoverFile = undefined; pickedCoverPath = null"
              >✕</button>
            </div>
            <div class="flex gap-2">
              <Button variant="outline" size="sm" @click="createSourceType = 'upload'; coverInput?.click()">
                <Upload class="h-3.5 w-3.5 mr-1" />{{ $t('promptLibrary.upload') }}
              </Button>
              <Button variant="outline" size="sm" @click="openAssetPicker()">
                <Image class="h-3.5 w-3.5 mr-1" />{{ $t('promptLibrary.fromAsset') }}
              </Button>
              <Button variant="outline" size="sm" @click="openHistoryPicker()">
                <Image class="h-3.5 w-3.5 mr-1" />{{ $t('promptLibrary.fromHistory') }}
              </Button>
            </div>
            <input ref="coverInput" type="file" accept="image/jpeg,image/png,image/webp" class="hidden" @change="onCoverFileSelected" />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" :disabled="createBusy" @click="createOpen = false">{{ $t('common.cancel') }}</Button>
          <Button :disabled="!createPrompt.trim() || !createCategory || createBusy" @click="handleCreate">
            <Loader2 v-if="createBusy" class="mr-1 h-4 w-4 animate-spin" />
            {{ $t('promptLibrary.create') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- ═════════️ ASSET PICKER DIALOG ══════════ -->
    <Dialog v-model:open="assetPickerOpen">
      <DialogContent class="sm:max-w-[600px] z-[60]" aria-describedby="asset-picker-desc">
        <DialogHeader><DialogTitle>{{ $t('promptLibrary.fromAsset') }}</DialogTitle></DialogHeader>
        <VisuallyHidden><div id="asset-picker-desc">{{ $t('promptLibrary.selectCoverHint') }}</div></VisuallyHidden>
        <div class="space-y-3 py-2">
          <Select v-model="assetSpaceId">
            <SelectTrigger class="min-w-[160px]"><SelectValue :placeholder="$t('promptLibrary.allSpaces')" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="__all__">{{ $t('promptLibrary.allSpaces') }}</SelectItem>
              <SelectItem v-for="s in assetSpaces" :key="s.id" :value="s.id">{{ s.name }}</SelectItem>
            </SelectContent>
          </Select>
          <div v-if="assetLoading" class="flex justify-center py-8">
            <Loader2 class="h-6 w-6 animate-spin text-muted-foreground" />
          </div>
          <div v-else class="grid grid-cols-4 gap-2 max-h-60 overflow-y-auto">
            <div
              v-for="a in assetList" :key="a.id"
              class="aspect-square rounded-md overflow-hidden bg-muted/30 cursor-pointer hover:ring-2 hover:ring-primary transition-all"
              @click="editOpen ? selectEditAssetCover(a) : selectAssetCover(a)"
            >
              <img :src="imageUrl(a.filePath ?? '')" class="w-full h-full object-cover" alt="" loading="lazy" />
            </div>
            <div v-if="!assetList.length" class="col-span-4 text-center text-sm text-muted-foreground py-8">
              {{ $t('assetLibrary.noAssets') }}
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>

    <!-- ══════════ HISTORY PICKER DIALOG ══════════ -->
    <Dialog v-model:open="historyPickerOpen">
      <DialogContent class="sm:max-w-[600px] z-[60]" aria-describedby="history-picker-desc">
        <DialogHeader><DialogTitle>{{ $t('promptLibrary.fromHistory') }}</DialogTitle></DialogHeader>
        <VisuallyHidden><div id="history-picker-desc">{{ $t('promptLibrary.selectCoverHint') }}</div></VisuallyHidden>
        <div class="py-2 space-y-2">
          <div class="relative">
            <Search class="absolute left-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input v-model="historyKeyword" :placeholder="$t('promptLibrary.historySearchPlaceholder')" class="pl-8" @keyup.enter="searchHistory" />
          </div>
          <div v-if="historyLoading" class="flex justify-center py-8">
            <Loader2 class="h-6 w-6 animate-spin text-muted-foreground" />
          </div>
          <div v-else-if="historyList.length === 0" class="text-center text-sm text-muted-foreground py-8">
            {{ $t('promptLibrary.noHistory') }}
          </div>
          <div v-else class="grid grid-cols-4 gap-2 max-h-52 overflow-y-auto">
            <div
              v-for="r in historyList" :key="r.id"
              class="aspect-square rounded-md overflow-hidden bg-muted/30 cursor-pointer hover:ring-2 hover:ring-primary transition-all"
              @click="editOpen ? selectEditHistoryCover(r) : selectHistoryCover(r)"
            >
              <img
                :src="imageUrl(r.resultPath)"
                class="w-full h-full object-cover" alt="" loading="lazy"
              />
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>

    <!-- ══════════ EDIT DIALOG ══════════ -->
    <Dialog v-model:open="editOpen">
      <DialogContent class="sm:max-w-[520px]" aria-describedby="edit-prompt-desc">
        <DialogHeader><DialogTitle>{{ $t('promptLibrary.editPrompt') }}</DialogTitle></DialogHeader>
        <VisuallyHidden><div id="edit-prompt-desc">{{ $t('promptLibrary.editPrompt') }}</div></VisuallyHidden>
        <div class="space-y-4 py-2">
          <div class="space-y-1.5">
            <label class="text-xs font-medium">{{ $t('promptLibrary.prompt') }}</label>
            <Textarea v-model="editPrompt" :placeholder="$t('promptLibrary.promptPlaceholder')" rows="4" class="max-h-[120px] overflow-y-auto" />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div class="space-y-1.5">
              <label class="text-xs font-medium">{{ $t('promptLibrary.category') }}</label>
              <Select v-model="editCategory">
                <SelectTrigger><SelectValue :placeholder="$t('promptLibrary.selectCategory')" /></SelectTrigger>
                <SelectContent>
                  <SelectItem v-for="c in categories" :key="c.value" :value="c.value">{{ c.label }}</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div class="space-y-1.5">
              <label class="text-xs font-medium">{{ $t('promptLibrary.tags') }}</label>
              <Input v-model="editTags" :placeholder="$t('promptLibrary.tagsPlaceholder')" />
            </div>
          </div>
          <div class="space-y-1.5">
            <label class="text-xs font-medium">{{ $t('promptLibrary.cover') }}</label>
            <div v-if="editCoverPreview" class="relative w-36 h-24 rounded-md overflow-hidden bg-muted/30 mb-2">
              <img :src="editCoverPreview" class="w-full h-full object-cover" alt="" />
              <button
                class="absolute top-1 right-1 h-5 w-5 rounded-full bg-black/50 text-white flex items-center justify-center text-xs cursor-pointer"
                @click="editCoverPreview = undefined; editCoverFile = undefined"
              >✕</button>
            </div>
            <div class="flex gap-2 flex-wrap">
              <Button variant="outline" size="sm" @click="editCoverInput?.click()">
                <Upload class="h-3.5 w-3.5 mr-1" />{{ $t('promptLibrary.upload') }}
              </Button>
              <Button variant="outline" size="sm" @click="openEditAssetPicker()">
                <Image class="h-3.5 w-3.5 mr-1" />{{ $t('promptLibrary.fromAsset') }}
              </Button>
              <Button variant="outline" size="sm" @click="openEditHistoryPicker()">
                <Image class="h-3.5 w-3.5 mr-1" />{{ $t('promptLibrary.fromHistory') }}
              </Button>
            </div>
            <input ref="editCoverInput" type="file" accept="image/jpeg,image/png,image/webp" class="hidden" @change="onEditCoverSelected" />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" :disabled="editBusy" @click="editOpen = false">{{ $t('common.cancel') }}</Button>
          <Button :disabled="!editPrompt.trim() || !editCategory || editBusy" @click="handleEdit">
            <Loader2 v-if="editBusy" class="mr-1 h-4 w-4 animate-spin" />
            {{ $t('promptLibrary.save') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- ══════════ DETAIL VIEW DIALOG ══════════ -->
    <Dialog v-model:open="detailOpen">
      <DialogContent class="sm:max-w-[560px]" aria-describedby="detail-prompt-desc">
        <DialogHeader><DialogTitle>{{ $t('promptLibrary.promptDetail') }}</DialogTitle></DialogHeader>
        <VisuallyHidden><div id="detail-prompt-desc">{{ $t('promptLibrary.promptDetail') }}</div></VisuallyHidden>
        <div v-if="detailTarget" class="space-y-4 py-2">
          <div v-if="detailTarget.coverPath" class="w-full rounded-md overflow-hidden bg-muted/30">
            <img :src="imageUrl(detailTarget.coverPath)" class="w-full max-h-72 object-contain" alt="" />
          </div>
          <div class="max-h-52 overflow-y-auto rounded-md border p-3 bg-muted/20">
            <p class="text-sm whitespace-pre-wrap leading-relaxed">{{ detailTarget.prompt }}</p>
          </div>
          <div class="flex flex-wrap gap-2 items-center">
            <span class="text-xs px-2 py-0.5 rounded-full bg-primary/10 text-primary/80">{{ detailTarget.category }}</span>
            <span v-if="detailTarget.tags" class="text-xs px-2 py-0.5 rounded-full bg-muted text-muted-foreground">{{ detailTarget.tags }}</span>
            <span class="text-xs text-muted-foreground ml-auto">{{ detailTarget.createdAt ? new Date(detailTarget.createdAt).toLocaleString() : '' }}</span>
          </div>
          <Button size="sm" class="w-full" @click="copyPrompt(detailTarget!.prompt, detailTarget!.id)">
            <span v-if="copiedId === detailTarget?.id" class="mr-1 h-4 w-4 flex items-center justify-center text-green-500">✓</span>
            <Copy v-else class="mr-1 h-4 w-4" />{{ $t('promptLibrary.copyPrompt') }}
          </Button>
        </div>
      </DialogContent>
    </Dialog>

    <!-- ══════════ DELETE CONFIRMATION ══════════ -->
    <Dialog v-model:open="deleteOpen">
      <DialogContent class="sm:max-w-sm" aria-describedby="delete-prompt-desc">
        <DialogHeader><DialogTitle>{{ $t('promptLibrary.deletePrompt') }}</DialogTitle></DialogHeader>
        <VisuallyHidden><div id="delete-prompt-desc">{{ $t('promptLibrary.confirmDelete') }}</div></VisuallyHidden>
        <div class="py-2">
          <p class="text-sm text-muted-foreground">{{ $t('promptLibrary.confirmDelete') }}</p>
          <p v-if="deleteTarget" class="mt-2 text-sm font-medium line-clamp-2">{{ deleteTarget.prompt }}</p>
        </div>
        <DialogFooter>
          <Button variant="outline" :disabled="deleteBusy" @click="deleteOpen = false">{{ $t('common.cancel') }}</Button>
          <Button variant="destructive" :disabled="deleteBusy" @click="handleDelete">
            <Loader2 v-if="deleteBusy" class="mr-1 h-4 w-4 animate-spin" />
            {{ $t('promptLibrary.delete') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<style scoped>
</style>
