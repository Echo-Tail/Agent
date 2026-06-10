<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import PageHeader from '@/components/PageHeader.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Card, CardContent } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Upload, Trash2, Search, Image, Folder, FolderPlus, Loader2, ZoomIn, ChevronLeft, ChevronRight, ArrowLeft } from 'lucide-vue-next'
import { VisuallyHidden } from 'reka-ui'
import { listSpaces, createSpace, listAssets, uploadAsset, deleteAsset } from '@/api/assets'
import { toast } from 'sonner'
import type { AssetSpace, PublicAsset, PageResponse } from '@/api/assets'

const { t } = useI18n()
const auth = useAuthStore()

// Navigation
const spaces = ref<AssetSpace[]>([])
const selectedSpace = ref<AssetSpace | null>(null)

// Assets
const assets = ref<PublicAsset[]>([])
const keyword = ref('')
const page = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const startDate = ref('')
const endDate = ref('')
const isSearching = ref(false)

// Upload dialog
const uploadOpen = ref(false)
const uploadFiles = ref<File[]>([])
const uploadSpaceId = ref<number | undefined>(undefined)
const uploading = ref(false)
const newSpaceInputVisible = ref(false)
const newSpaceName = ref('')
const newSpaceCreating = ref(false)
const uploadProgress = ref<{ current: number; total: number; pct: number } | null>(null)

// Upload preview (local files before upload)
const previewOpen = ref(false)
const previewIndex = ref(0)
const previewUrls = ref<string[]>([])
let previewKey = 0
const maxVisible = 3

// Create space dialog
const createSpaceOpen = ref(false)
const createSpaceName = ref('')
const createSpaceBusy = ref(false)

async function handleCreateSpace() {
  if (!createSpaceName.value.trim() || createSpaceBusy.value) return
  createSpaceBusy.value = true
  try {
    await createSpace(createSpaceName.value.trim())
    toast.success(t('assetLibrary.spaceCreated'))
    createSpaceOpen.value = false
    createSpaceName.value = ''
    await loadSpaces()
  } catch { /* toast handled */ }
  finally { createSpaceBusy.value = false }
}

// Asset preview (server images in a space)
const assetPreviewOpen = ref(false)
const assetPreviewIndex = ref(0)
const assetPreviewUrls = ref<string[]>([])
let assetPreviewKey = 0

// ── Space navigation ──

function selectSpace(space: AssetSpace) {
  selectedSpace.value = space
  keyword.value = ''
  startDate.value = ''
  endDate.value = ''
  isSearching.value = false
  page.value = 0
  loadAssets()
}

function goBackToSpaces() {
  selectedSpace.value = null
  assets.value = []
  keyword.value = ''
  startDate.value = ''
  endDate.value = ''
  isSearching.value = false
  page.value = 0
  totalPages.value = 0
}

// ── File selection (upload) ──

function handleFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  if (!input.files?.length) return
  const newFiles = Array.from(input.files)
  const newUrls = newFiles.map(f => URL.createObjectURL(f))
  uploadFiles.value = [...uploadFiles.value, ...newFiles]
  previewUrls.value = [...previewUrls.value, ...newUrls]
  input.value = ''
}

function openUploadDialog() {
  uploadFiles.value = []
  previewUrls.value.forEach(u => URL.revokeObjectURL(u))
  previewUrls.value = []
  // When inside a space, default to that space
  uploadSpaceId.value = selectedSpace.value?.id ?? undefined
  newSpaceInputVisible.value = false
  newSpaceName.value = ''
  uploadOpen.value = true
}

async function submitUpload() {
  if (!uploadFiles.value.length) return
  uploading.value = true
  uploadProgress.value = { current: 0, total: uploadFiles.value.length, pct: 0 }
  try {
    for (let i = 0; i < uploadFiles.value.length; i++) {
      const file = uploadFiles.value[i]
      uploadProgress.value = { current: i, total: uploadFiles.value.length, pct: 0 }
      await uploadAsset(file, uploadSpaceId.value, (e) => {
        if (e.total) {
          uploadProgress.value = { current: i, total: uploadFiles.value.length, pct: Math.round((e.loaded / e.total) * 100) }
        }
      })
    }
    toast.success(t('assetLibrary.uploadSuccess'))
    uploadOpen.value = false
    uploadFiles.value = []
    await loadSpaces()
    if (selectedSpace.value) await loadAssets()
  } catch { /* toast handled */ }
  finally {
    uploading.value = false
    uploadProgress.value = null
  }
}

async function createNewSpaceAndSelect() {
  if (!newSpaceName.value.trim()) return
  newSpaceCreating.value = true
  try {
    const res = await createSpace(newSpaceName.value.trim())
    uploadSpaceId.value = res.id
    newSpaceInputVisible.value = false
    newSpaceName.value = ''
    await loadSpaces()
    toast.success(t('assetLibrary.spaceCreated'))
  } catch { /* toast handled */ }
  finally { newSpaceCreating.value = false }
}

// ── Upload preview (local) ──

function openPreview(index: number) {
  previewIndex.value = index
  previewKey++
  previewOpen.value = true
}

function prevPreview() {
  previewIndex.value = (previewIndex.value - 1 + previewUrls.value.length) % previewUrls.value.length
}

function nextPreview() {
  previewIndex.value = (previewIndex.value + 1) % previewUrls.value.length
}

// ── Asset preview (server) ──

function openAssetPreview(index: number) {
  assetPreviewIndex.value = index
  assetPreviewKey++
  assetPreviewOpen.value = true
}

function prevAssetPreview() {
  assetPreviewIndex.value = (assetPreviewIndex.value - 1 + assetPreviewUrls.value.length) % assetPreviewUrls.value.length
}

function nextAssetPreview() {
  assetPreviewIndex.value = (assetPreviewIndex.value + 1) % assetPreviewUrls.value.length
}

// ── Permissions ──

const isAdmin = computed(() => auth.isAdmin)
const currentUserId = computed(() => auth.currentUser?.id)

// ── Data loading ──

onMounted(async () => {
  await loadSpaces()
})

async function loadSpaces() {
  try {
    spaces.value = await listSpaces()
  } catch { /* toast handled */ }
}

async function loadAssets() {
  loading.value = true
  try {
    const spaceId = selectedSpace.value?.id
    const res: PageResponse<PublicAsset> = await listAssets({
      spaceId,
      keyword: keyword.value || undefined,
      startDate: startDate.value || undefined,
      endDate: endDate.value || undefined,
      page: page.value,
      size: 20,
    })
    assets.value = res.content ?? []
    totalPages.value = res.page?.totalPages ?? 0
    // Build preview URLs for server assets
    assetPreviewUrls.value = assets.value.map(a => imageUrl(a.filePath))
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function search() {
  if (!selectedSpace.value) isSearching.value = true
  page.value = 0
  loadAssets()
}

function clearSearch() {
  keyword.value = ''
  startDate.value = ''
  endDate.value = ''
  isSearching.value = false
  assets.value = []
  page.value = 0
  totalPages.value = 0
}

function goToPage(p: number) {
  if (p < 0 || p >= totalPages.value) return
  page.value = p
  loadAssets()
}

async function handleDeleteAsset(id: number) {
  try {
    await deleteAsset(id)
    toast.success(t('assetLibrary.deleteSuccess'))
    assets.value = assets.value.filter(a => a.id !== id)
    assetPreviewUrls.value = assets.value.map(a => imageUrl(a.filePath))
  } catch { /* toast handled */ }
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / (1024 * 1024)).toFixed(1) + 'MB'
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleString()
}

function imageUrl(path: string): string {
  if (!path) return ''
  const n = path.replace(/\\/g, '/').replace(/^\.\//, '')
  return '/uploads/' + n
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader :title="$t('pageTitle.publicAssets')" :description="$t('pageTitle.publicAssetsDesc')" />

    <!-- ══════════ SPACE LIST VIEW ══════════ -->
    <template v-if="!selectedSpace">
      <!-- Search toolbar (global) -->
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex items-center gap-2 flex-wrap">
          <div class="relative">
            <Search class="absolute left-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input v-model="keyword" :placeholder="$t('assetLibrary.searchPlaceholder')" class="pl-8 w-40" @keyup.enter="search" />
          </div>
          <input v-model="startDate" type="date" class="h-8 rounded-md border border-border bg-background px-2 text-xs" />
          <span class="text-xs text-muted-foreground">~</span>
          <input v-model="endDate" type="date" class="h-8 rounded-md border border-border bg-background px-2 text-xs" />
          <Button variant="outline" size="sm" @click="search">{{ $t('assetLibrary.search') }}</Button>
        </div>
        <div class="flex items-center gap-6">
          <Button size="sm" @click="openUploadDialog()">
            <Upload class="mr-1 h-4 w-4" />
            {{ $t('assetLibrary.upload') }}
          </Button>
          <Button variant="outline" size="sm" @click="createSpaceOpen = true">
            <FolderPlus class="mr-1 h-4 w-4" />
            {{ $t('assetLibrary.newSpace') }}
          </Button>
        </div>
      </div>

      <!-- Search results view -->
      <template v-if="isSearching">
        <div class="flex items-center gap-1.5">
          <Button variant="ghost" size="sm" class="h-7 px-2 text-muted-foreground" @click="clearSearch">
            <ArrowLeft class="h-4 w-4 mr-1" />
            <span class="text-sm">{{ $t('pageTitle.publicAssets') }}</span>
          </Button>
          <ChevronRight class="h-3 w-3 text-muted-foreground shrink-0" />
          <span class="text-sm font-medium">{{ $t('assetLibrary.searchResults') }}</span>
        </div>
        <div v-if="loading" class="flex justify-center py-12">
          <Loader2 class="h-8 w-8 animate-spin text-muted-foreground" />
        </div>
        <div v-else-if="assets.length === 0" class="flex flex-col items-center py-12 text-muted-foreground">
          <Image class="h-12 w-12 mb-2 opacity-40" />
          <p>{{ $t('assetLibrary.noAssets') }}</p>
        </div>
        <div v-else class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-5 gap-4">
          <Card v-for="(asset, idx) in assets" :key="asset.id" class="overflow-hidden group cursor-pointer" @click="openAssetPreview(idx)">
            <div class="relative aspect-square bg-muted/30">
              <img :src="imageUrl(asset.filePath)" class="w-full h-full object-cover" :alt="asset.fileName" loading="lazy" />
              <div class="absolute inset-0 bg-black/0 group-hover:bg-black/10 transition-colors flex items-center justify-center">
                <ZoomIn class="h-6 w-6 text-white opacity-0 group-hover:opacity-100 transition-opacity" />
              </div>
            </div>
            <CardContent class="p-2 space-y-1">
              <p class="text-xs truncate font-medium">{{ asset.fileName }}</p>
              <p class="text-xs text-muted-foreground">{{ formatSize(asset.fileSize) }} · {{ formatDate(asset.createdAt) }}</p>
            </CardContent>
          </Card>
        </div>
        <!-- Pagination -->
        <div v-if="totalPages > 1" class="flex justify-center gap-2">
          <Button variant="outline" size="sm" :disabled="page <= 0" @click="goToPage(page - 1)">{{ $t('common.prev') }}</Button>
          <span class="flex items-center text-sm text-muted-foreground">{{ page + 1 }} / {{ totalPages }}</span>
          <Button variant="outline" size="sm" :disabled="page >= totalPages - 1" @click="goToPage(page + 1)">{{ $t('common.next') }}</Button>
        </div>
      </template>

      <!-- Space cards (only when not searching) -->
      <template v-if="!isSearching">
        <div v-if="spaces.length === 0" class="flex flex-col items-center py-16 text-muted-foreground">
          <Folder class="h-16 w-16 mb-3 opacity-30" />
          <p class="text-lg">{{ $t('assetLibrary.noSpaces') }}</p>
          <Button variant="outline" size="sm" class="mt-3" @click="openUploadDialog()">
            <Upload class="mr-1 h-4 w-4" />
            {{ $t('assetLibrary.upload') }}
          </Button>
        </div>
        <div v-else class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4">
          <Card
            v-for="space in spaces"
            :key="space.id"
            class="cursor-pointer hover:border-primary/50 hover:shadow-sm transition-all"
            @click="selectSpace(space)"
          >
            <CardContent class="flex flex-col items-center justify-center py-10 gap-3">
              <Folder class="h-14 w-14 text-muted-foreground/60" />
              <p class="font-medium text-center text-sm truncate max-w-full">{{ space.name }}</p>
            </CardContent>
          </Card>
        </div>
      </template>
    </template>

    <!-- ══════════ ASSET LIST VIEW (inside a space) ══════════ -->
    <template v-else>
      <!-- Breadcrumb + Toolbar -->
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex items-center gap-1.5 min-w-0">
          <Button variant="ghost" size="sm" class="h-7 px-2 text-muted-foreground" @click="goBackToSpaces()">
            <ArrowLeft class="h-4 w-4 mr-1" />
            <span class="text-sm">{{ $t('pageTitle.publicAssets') }}</span>
          </Button>
          <ChevronRight class="h-3 w-3 text-muted-foreground shrink-0" />
          <span class="text-sm font-medium truncate">{{ selectedSpace.name }}</span>
        </div>
        <div class="flex items-center gap-2 flex-wrap">
          <div class="relative">
            <Search class="absolute left-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input v-model="keyword" :placeholder="$t('assetLibrary.searchPlaceholder')" class="pl-8 w-40" @keyup.enter="search" />
          </div>
          <input v-model="startDate" type="date" class="h-8 rounded-md border border-border bg-background px-2 text-xs" />
          <span class="text-xs text-muted-foreground">~</span>
          <input v-model="endDate" type="date" class="h-8 rounded-md border border-border bg-background px-2 text-xs" />
          <Button variant="outline" size="sm" @click="search">{{ $t('assetLibrary.search') }}</Button>
          <Button size="sm" @click="openUploadDialog()">
            <Upload class="mr-1 h-4 w-4" />
            {{ $t('assetLibrary.upload') }}
          </Button>
        </div>
      </div>

      <!-- Asset grid -->
      <div v-if="loading" class="flex justify-center py-12">
        <Loader2 class="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
      <div v-else-if="assets.length === 0" class="flex flex-col items-center py-12 text-muted-foreground">
        <Image class="h-12 w-12 mb-2 opacity-40" />
        <p>{{ $t('assetLibrary.noAssets') }}</p>
      </div>
      <div v-else class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-5 gap-4">
        <Card v-for="(asset, idx) in assets" :key="asset.id" class="overflow-hidden group cursor-pointer" @click="openAssetPreview(idx)">
          <div class="relative aspect-square bg-muted/30">
            <img :src="imageUrl(asset.filePath)" class="w-full h-full object-cover" :alt="asset.fileName" loading="lazy" />
            <div class="absolute inset-0 bg-black/0 group-hover:bg-black/10 transition-colors flex items-center justify-center">
              <ZoomIn class="h-6 w-6 text-white opacity-0 group-hover:opacity-100 transition-opacity" />
            </div>
          </div>
          <CardContent class="p-2 space-y-1">
            <p class="text-xs truncate font-medium">{{ asset.fileName }}</p>
            <p class="text-xs text-muted-foreground">{{ formatSize(asset.fileSize) }} · {{ formatDate(asset.createdAt) }}</p>
            <div class="flex gap-1 pt-1">
              <Button v-if="asset.uploadedBy === currentUserId || isAdmin" variant="ghost" size="sm" class="h-6 w-6 p-0 text-muted-foreground hover:text-destructive" @click.stop="handleDeleteAsset(asset.id)">
                <Trash2 class="h-3 w-3" />
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>

      <!-- Pagination -->
      <div v-if="totalPages > 1" class="flex justify-center gap-2">
        <Button variant="outline" size="sm" :disabled="page <= 0" @click="goToPage(page - 1)">{{ $t('common.prev') }}</Button>
        <span class="flex items-center text-sm text-muted-foreground">{{ page + 1 }} / {{ totalPages }}</span>
        <Button variant="outline" size="sm" :disabled="page >= totalPages - 1" @click="goToPage(page + 1)">{{ $t('common.next') }}</Button>
      </div>
    </template>

    <!-- ══════════ UPLOAD DIALOG ══════════ -->
    <Dialog v-model:open="uploadOpen">
      <DialogContent class="sm:max-w-[500px]" aria-describedby="upload-dialog-desc">
        <DialogHeader><DialogTitle>{{ $t('assetLibrary.uploadTo') }}</DialogTitle></DialogHeader>
        <VisuallyHidden><div id="upload-dialog-desc">{{ $t('assetLibrary.uploadTo') }}</div></VisuallyHidden>
        <div class="space-y-4 py-2">

          <!-- File picker (empty state) -->
          <div v-if="!uploadFiles.length">
            <label class="flex flex-col items-center justify-center w-full h-28 rounded-lg border-2 border-dashed border-border cursor-pointer hover:border-primary/50 transition-colors bg-muted/20">
              <Upload class="h-6 w-6 text-muted-foreground mb-1" />
              <span class="text-sm text-muted-foreground">{{ $t('assetLibrary.clickToSelect') }}</span>
              <input type="file" accept="image/jpeg,image/png,image/webp" class="hidden" @change="handleFileSelect" multiple />
            </label>
          </div>

          <!-- Thumbnail previews (files selected) -->
          <div v-else class="space-y-2">
            <div class="flex items-center justify-between">
              <span class="text-sm font-medium">{{ uploadFiles.length }} {{ $t('assetLibrary.files') }}</span>
              <label class="cursor-pointer text-xs text-primary hover:underline">
                {{ $t('assetLibrary.clickToSelect') }}
                <input type="file" accept="image/jpeg,image/png,image/webp" class="hidden" @change="handleFileSelect" multiple />
              </label>
            </div>
            <div class="grid grid-cols-4 gap-2">
              <div
                v-for="(url, idx) in previewUrls.slice(0, maxVisible)"
                :key="idx"
                class="relative aspect-square rounded-md overflow-hidden bg-muted/30 group cursor-pointer"
                @click="openPreview(idx)"
              >
                <img :src="url" class="w-full h-full object-cover" alt="" />
                <div class="absolute inset-0 bg-black/0 group-hover:bg-black/20 transition-colors flex items-center justify-center">
                  <ZoomIn class="h-5 w-5 text-white opacity-0 group-hover:opacity-100 transition-opacity" />
                </div>
              </div>
              <div
                v-if="previewUrls.length > maxVisible"
                class="relative aspect-square rounded-md overflow-hidden bg-muted flex items-center justify-center cursor-pointer"
                @click="openPreview(maxVisible)"
              >
                <span class="text-lg font-semibold text-muted-foreground">+{{ previewUrls.length - maxVisible }}</span>
              </div>
            </div>
          </div>

          <!-- Space select + create -->
          <div class="space-y-2">
            <div class="flex items-center gap-2">
              <div class="flex-1">
                <Select v-model="uploadSpaceId">
                  <SelectTrigger class="min-w-[185px]"><SelectValue :placeholder="$t('assetLibrary.selectSpace')" /></SelectTrigger>
                  <SelectContent>
                    <SelectItem v-for="sp in spaces" :key="sp.id" :value="sp.id">{{ sp.name }}</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <Button variant="outline" size="sm" @click="newSpaceInputVisible = !newSpaceInputVisible">
                <FolderPlus class="h-4 w-4" />
              </Button>
            </div>
            <div v-if="newSpaceInputVisible" class="flex items-center gap-2">
              <Input v-model="newSpaceName" :placeholder="$t('assetLibrary.spaceNamePlaceholder')" class="flex-1" />
              <Button size="sm" :disabled="!newSpaceName.trim() || newSpaceCreating" @click="createNewSpaceAndSelect">
                <Loader2 v-if="newSpaceCreating" class="h-3 w-3 mr-1 animate-spin" />
                {{ $t('assetLibrary.create') }}
              </Button>
            </div>
          </div>

        </div>
        <DialogFooter class="flex-col items-stretch gap-2 sm:flex-col">
          <div v-if="uploadProgress" class="space-y-1">
            <div class="flex items-center justify-between text-xs text-muted-foreground">
              <span>{{ uploadProgress.current + 1 }} / {{ uploadProgress.total }} {{ $t('assetLibrary.files') }}</span>
              <span>{{ uploadProgress.pct }}%</span>
            </div>
            <div class="h-2 rounded-full bg-muted overflow-hidden">
              <div class="h-full rounded-full bg-primary transition-all duration-300" :style="{ width: uploadProgress.pct + '%' }"></div>
            </div>
          </div>
          <div class="flex justify-end gap-2">
            <Button variant="outline" :disabled="uploading" @click="uploadOpen = false">{{ $t('common.cancel') }}</Button>
            <Button :disabled="!uploadFiles.length || uploading" @click="submitUpload">
              <Loader2 v-if="uploading" class="mr-1 h-4 w-4 animate-spin" />
              {{ uploading ? $t('assetLibrary.upload') : $t('assetLibrary.upload') }}
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- ══════════ ASSET PREVIEW OVERLAY (server images) ══════════ -->
    <Dialog v-model:open="assetPreviewOpen">
      <DialogContent class="sm:max-w-[60vw] max-h-[90vh] p-0 bg-background/95 backdrop-blur-sm" aria-describedby="asset-preview-desc">
        <VisuallyHidden>
          <DialogHeader><DialogTitle>{{ selectedSpace?.name ?? '' }}</DialogTitle></DialogHeader>
        </VisuallyHidden>
        <VisuallyHidden><div id="asset-preview-desc">{{ assetPreviewIndex + 1 }} / {{ assetPreviewUrls.length }}</div></VisuallyHidden>
        <div class="relative flex items-center justify-center min-h-[60vh]">
          <img
            :key="'asset-preview-' + assetPreviewKey"
            :src="assetPreviewUrls[assetPreviewIndex]"
            class="max-h-[75vh] max-w-full object-contain rounded-lg p-6"
            :alt="assets[assetPreviewIndex]?.fileName ?? ''"
          />
          <button
            v-if="assetPreviewUrls.length > 1"
            class="absolute left-2 top-1/2 -translate-y-1/2 z-10 flex items-center justify-center h-12 w-12 rounded-full bg-black/40 text-white hover:bg-black/60 cursor-pointer transition-colors"
            @click="prevAssetPreview"
          >
            <ChevronLeft class="h-6 w-6" />
            <span class="sr-only">Previous</span>
          </button>
          <button
            v-if="assetPreviewUrls.length > 1"
            class="absolute right-2 top-1/2 -translate-y-1/2 z-10 flex items-center justify-center h-12 w-12 rounded-full bg-black/40 text-white hover:bg-black/60 cursor-pointer transition-colors"
            @click="nextAssetPreview"
          >
            <ChevronRight class="h-6 w-6" />
            <span class="sr-only">Next</span>
          </button>
          <div class="absolute bottom-3 left-1/2 -translate-x-1/2 text-xs text-muted-foreground bg-background/80 px-2.5 py-1 rounded-full">
            {{ assetPreviewIndex + 1 }} / {{ assetPreviewUrls.length }}
          </div>
        </div>
      </DialogContent>
    </Dialog>

    <!-- ══════════ CREATE SPACE DIALOG ══════════ -->
    <Dialog v-model:open="createSpaceOpen">
      <DialogContent class="sm:max-w-sm" aria-describedby="create-space-desc">
        <DialogHeader><DialogTitle>{{ $t('assetLibrary.newSpace') }}</DialogTitle></DialogHeader>
        <VisuallyHidden><div id="create-space-desc">{{ $t('assetLibrary.newSpace') }}</div></VisuallyHidden>
        <div class="py-2">
          <Input
            v-model="createSpaceName"
            :placeholder="$t('assetLibrary.spaceNamePlaceholder')"
            @keyup.enter="handleCreateSpace"
          />
        </div>
        <DialogFooter>
          <Button variant="outline" @click="createSpaceOpen = false">{{ $t('common.cancel') }}</Button>
          <Button :disabled="!createSpaceName.trim() || createSpaceBusy" @click="handleCreateSpace">
            <Loader2 v-if="createSpaceBusy" class="mr-1 h-4 w-4 animate-spin" />
            {{ $t('assetLibrary.create') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- ══════════ UPLOAD PREVIEW OVERLAY (local files) ══════════ -->
    <Dialog v-model:open="previewOpen">
      <DialogContent class="sm:max-w-[60vw] max-h-[90vh] p-0 bg-background/95 backdrop-blur-sm" aria-describedby="preview-desc">
        <VisuallyHidden>
          <DialogHeader><DialogTitle>{{ $t('assetLibrary.uploadTo') }}</DialogTitle></DialogHeader>
        </VisuallyHidden>
        <VisuallyHidden><div id="preview-desc">{{ previewIndex + 1 }} / {{ previewUrls.length }}</div></VisuallyHidden>
        <div class="relative flex items-center justify-center min-h-[60vh]">
          <img
            :key="'preview-img-' + previewKey"
            :src="previewUrls[previewIndex]"
            class="max-h-[75vh] max-w-full object-contain rounded-lg p-6"
            :alt="uploadFiles[previewIndex]?.name ?? ''"
          />
          <button
            v-if="previewUrls.length > 1"
            class="absolute left-2 top-1/2 -translate-y-1/2 z-10 flex items-center justify-center h-12 w-12 rounded-full bg-black/40 text-white hover:bg-black/60 cursor-pointer transition-colors"
            @click="prevPreview"
          >
            <ChevronLeft class="h-6 w-6" />
            <span class="sr-only">Previous</span>
          </button>
          <button
            v-if="previewUrls.length > 1"
            class="absolute right-2 top-1/2 -translate-y-1/2 z-10 flex items-center justify-center h-12 w-12 rounded-full bg-black/40 text-white hover:bg-black/60 cursor-pointer transition-colors"
            @click="nextPreview"
          >
            <ChevronRight class="h-6 w-6" />
            <span class="sr-only">Next</span>
          </button>
          <div class="absolute bottom-3 left-1/2 -translate-x-1/2 text-xs text-muted-foreground bg-background/80 px-2.5 py-1 rounded-full">
            {{ previewIndex + 1 }} / {{ previewUrls.length }}
          </div>
        </div>
      </DialogContent>
    </Dialog>

  </div>
</template>
