<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'

import {
  Check, Copy, Download, Image, Upload, Trash2, RefreshCw, Loader2, AlertCircle,
  Search, ChevronLeft, ChevronRight, ZoomIn, X,
} from 'lucide-vue-next'
import { generateImage, editImage, listImageRecords, deleteImageRecord } from '@/api/image'
import { getImageModelsApi } from '@/api/model'
import { listSpaces, listAssets, importFromRecord } from '@/api/assets'
import type { AssetSpace, PublicAsset, PageResponse } from '@/api/assets'
import { toast } from 'vue-sonner'
import { useI18n } from 'vue-i18n'
import type { ImageRecord, ImageGenerationResult } from '@/api/image'

const { t } = useI18n()

// ── Model availability ──
const hasModel = ref(false)
const modelLoading = ref(true)
const imageModels = ref<{ id: number; name: string }[]>([])
const selectedModelId = ref<number | undefined>(undefined)

// ── Mode ──
const activeMode = ref<'generate' | 'edit'>('generate')

// ── Form ──
const prompt = ref('')
const size = ref('1024x1024')
const quality = ref('high')   // default: high
const imageCount = ref(1)
const editImages = ref<File[]>([])
const editPreviewUrls = ref<string[]>([])
const maskFile = ref<File | undefined>(undefined)
const maskPreviewUrl = ref<string>('')

// ── Generation ──
const generating = ref(false)
const result = ref<ImageGenerationResult | null>(null)
const timerSeconds = ref(0)
let timerInterval: ReturnType<typeof setInterval> | null = null

onBeforeUnmount(() => stopTimer())

function startTimer() {
  timerSeconds.value = 0
  timerInterval = setInterval(() => { timerSeconds.value++ }, 1000)
}

function stopTimer() {
  if (timerInterval) {
    clearInterval(timerInterval)
    timerInterval = null
  }
}

// ── Image viewer lightbox ──
const copiedRecordId = ref<number | null>(null)
const lightboxOpen = ref(false)
const lightboxUrl = ref('')
const zoomLevel = ref(1)
const panX = ref(0)
const panY = ref(0)

// ── History ──
const records = ref<ImageRecord[]>([])
const totalPages = ref(0)
const totalElements = ref(0)
const currentPage = ref(0)
const historyLoading = ref(false)
const historyRef = ref<HTMLElement | null>(null)
// eslint-disable-next-line @typescript-eslint/no-unused-vars
void historyRef
const filterPrompt = ref('')
const filterStartDate = ref('')
const filterEndDate = ref('')

// ── Size options with descriptions ──
const sizeOptions = [
  { value: '1024x1024', label: '1024 × 1024 — 方形' },
  { value: '1504x1504', label: '1504 × 1504 — 方形' },
  { value: '1536x1024', label: '1536 × 1024 — 横向' },
  { value: '1024x1536', label: '1024 × 1536 — 纵向' },
  { value: '1600x1600', label: '1600 × 1600 — 方形' },
  { value: '2048x2048', label: '2048 × 2048 — 方形 2K' },
  { value: '2048x1152', label: '2048 × 1152 — 横向 2K' },
  { value: '3840x2160', label: '3840 × 2160 — 横向 4K' },
  { value: '2160x3840', label: '2160 × 3840 — 纵向 4K' },
  { value: 'auto', label: 'auto — 自动' },
]

const qualityOptions = [
  { value: 'low', label: 'low' },
  { value: 'medium', label: 'medium' },
  { value: 'high', label: 'high' },
  { value: 'auto', label: 'auto' },
]

const canGenerate = computed(() => hasModel.value && prompt.value.trim().length > 0)
const hasResult = computed(() => result.value && result.value.urls && result.value.urls.length > 0)

const visiblePages = computed(() => {
  const total = totalPages.value
  const current = currentPage.value + 1
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1)
  const pages: (number | '...')[] = [1]
  if (current > 3) pages.push('...')
  const start = Math.max(2, current - 1)
  const end = Math.min(total - 1, current + 1)
  for (let i = start; i <= end; i++) pages.push(i)
  if (current < total - 2) pages.push('...')
  if (total > 1) pages.push(total)
  return pages
})
const resultImageUrl = (url: string) => {
  if (!url) return ''
  if (url.startsWith('blob:') || url.startsWith('http://') || url.startsWith('https://')) return url
  // Backend now returns paths starting with /uploads/ — ensure consistency
  return url.replace(/\\/g, '/')
}

/** 取 resultPath 的第一张图（多图时按 \n 分隔） */
function firstResultPath(record: { resultPath: string }): string {
  return record.resultPath?.split('\n').filter(Boolean)[0] || ''
}

// ── Lifecycle ──
onMounted(async () => {
  try {
    const models = await getImageModelsApi()
    hasModel.value = models.length > 0
    imageModels.value = models
    if (models.length > 0 && !selectedModelId.value) {
      selectedModelId.value = models[0].id
    }
  } catch {
    hasModel.value = false
  } finally {
    modelLoading.value = false
  }
  await fetchHistory()
})

// ── History fetch ──
async function fetchHistory(page = 0) {
  if (records.value.length === 0) historyLoading.value = true
  try {
    const res: PageResponse<ImageRecord> = await listImageRecords({
      page,
      size: 5,
      startDate: filterStartDate.value || undefined,
      endDate: filterEndDate.value || undefined,
      prompt: filterPrompt.value || undefined,
    })
    records.value = res.content ?? []
    totalPages.value = res.page?.totalPages ?? 0
    totalElements.value = res.page?.totalElements ?? 0
    currentPage.value = res.page?.number ?? 0
    if (page !== 0) {
      await nextTick()
    }
  } catch (e) {
    console.error('fetchHistory error:', e)
    records.value = []
    totalPages.value = 0
    totalElements.value = 0
  } finally {
    historyLoading.value = false
  }
}

function goToPage(page: number) {
  if (page < 0 || page >= totalPages.value) return
  // 保存滚动位置，翻页后恢复
  const scrollY = window.scrollY
  fetchHistory(page).then(() => {
    window.scrollTo({ top: scrollY, behavior: 'instant' as ScrollBehavior })
  })
}

function handleSearch() {
  fetchHistory(0)
}

// ── Generate ──
async function handleGenerate() {
  if (!canGenerate.value || generating.value) return
  generating.value = true
  result.value = null
  startTimer()
  try {
    result.value = await generateImage(prompt.value, size.value, quality.value, imageCount.value, selectedModelId.value)
    if (result.value.failedCount > 0) {
      toast.success(`${result.value.urls.length} 张生成成功，${result.value.failedCount} 张失败`)
    } else {
      toast.success(t('toast.imageGenerated'))
    }
    await fetchHistory(0)
  } catch {
    // 错误提示由 request.ts 拦截器统一处理
  } finally {
    stopTimer()
    generating.value = false
  }
}

async function handleEdit() {
  if (!canGenerate.value || generating.value || editImages.value.length === 0) return
  generating.value = true
  result.value = null
  startTimer()
  try {
    result.value = await editImage(prompt.value, editImages.value, size.value, quality.value, maskFile.value, imageCount.value, selectedModelId.value)
    if (result.value.failedCount > 0) {
      toast.success(`${result.value.urls.length} 张生成成功，${result.value.failedCount} 张失败`)
    } else {
      toast.success(t('toast.imageGenerated'))
    }
    await fetchHistory(0)
  } catch {
    // 错误提示由 request.ts 拦截器统一处理
  } finally {
    stopTimer()
    generating.value = false
  }
}

// ── File upload ──
function handleFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  if (!input.files) return
  const remaining = 4 - editImages.value.length
  const newFiles = Array.from(input.files).slice(0, remaining)
  newFiles.forEach(file => {
    editImages.value.push(file)
    editPreviewUrls.value.push(URL.createObjectURL(file))
  })
  input.value = ''
}

function removeImage(index: number) {
  URL.revokeObjectURL(editPreviewUrls.value[index])
  editImages.value.splice(index, 1)
  editPreviewUrls.value.splice(index, 1)
}

// ── Mask upload ──
function handleMaskSelect(event: Event) {
  const input = event.target as HTMLInputElement
  if (!input.files || input.files.length === 0) return
  const file = input.files[0]
  if (maskPreviewUrl.value) URL.revokeObjectURL(maskPreviewUrl.value)
  maskFile.value = file
  maskPreviewUrl.value = URL.createObjectURL(file)
  input.value = ''
}

function removeMask() {
  if (maskPreviewUrl.value) URL.revokeObjectURL(maskPreviewUrl.value)
  maskFile.value = undefined
  maskPreviewUrl.value = ''
}

// ── Asset upload dialog (replace publish) ──
const assetUploadOpen = ref(false)
const assetUploadRecordId = ref<number | null>(null)
const assetUploadSpaceId = ref<number | undefined>(undefined)
const uploadingAsset = ref(false)
const assetSpaces = ref<AssetSpace[]>([])

function openAssetUpload(record: ImageRecord) {
  assetUploadRecordId.value = record.id
  assetUploadSpaceId.value = undefined
  assetUploadOpen.value = true
  loadAssetSpaces()
}

async function loadAssetSpaces() {
  try {
    assetSpaces.value = await listSpaces()
  } catch { /* ignore */ }
}

async function submitAssetUpload() {
  if (!assetUploadRecordId.value) return
  uploadingAsset.value = true
  try {
    await importFromRecord(assetUploadRecordId.value, assetUploadSpaceId.value)
    assetUploadOpen.value = false
    setTimeout(() => toast.success(t('assetLibrary.uploadSuccess')), 150)
  } catch {
    assetUploadOpen.value = false
  } finally {
    uploadingAsset.value = false
  }
}

// ── Asset picker (select reference images from library) ──
const assetPickerOpen = ref(false)
const pickerAssets = ref<PublicAsset[]>([])
const pickerLoading = ref(false)
const pickerSpaceId = ref<number | null>(null)  // null = 未分类
const pickerKeyword = ref('')
const pickerSpaces = ref<AssetSpace[]>([])

async function openAssetPicker() {
  assetPickerOpen.value = true
  pickerKeyword.value = ''
  try {
    pickerSpaces.value = await listSpaces()
    // 默认选中"未分类"空间
    const defaultSpace = pickerSpaces.value.find(s => s.name === '未分类')
    pickerSpaceId.value = defaultSpace?.id ?? null
  } catch {}
  await loadPickerAssets()
}

async function loadPickerAssets() {
  pickerLoading.value = true
  try {
    const res: PageResponse<PublicAsset> = await listAssets({
      spaceId: pickerSpaceId.value ?? undefined,
      keyword: pickerKeyword.value || undefined,
      page: 0,
      size: 50,
    })
    pickerAssets.value = res.content ?? []
  } catch { /* ignore */ }
  finally { pickerLoading.value = false }
}

async function pickAsset(asset: PublicAsset) {
  if (editImages.value.length >= 4) {
    toast.error(t('imageGen.maxImages') || '最多 4 张参考图')
    return
  }
  try {
    const resp = await fetch(imageUrl(asset.filePath))
    const blob = await resp.blob()
    const file = new File([blob], asset.fileName, { type: blob.type })
    editImages.value.push(file)
    editPreviewUrls.value.push(URL.createObjectURL(blob))
    toast.success(t('assetLibrary.uploadSuccess') || '已添加参考图')
  } catch {
    toast.error(t('imageGen.loadError') || '加载图片失败')
  }
}

// ── Asset picker preview ──
const pickerPreviewAsset = ref<PublicAsset | null>(null)
const pickerPreviewOpen = ref(false)

function showPickerPreview(asset: PublicAsset) {
  pickerPreviewAsset.value = asset
  pickerPreviewOpen.value = true
}

function selectPickerPreview() {
  if (pickerPreviewAsset.value) pickAsset(pickerPreviewAsset.value)
  pickerPreviewOpen.value = false
  pickerPreviewAsset.value = null
}

function imageUrl(path: string): string {
  if (!path) return ''
  if (path.startsWith('blob:') || path.startsWith('http://') || path.startsWith('https://')) return path
  let normalized = path.replace(/\\/g, '/').replace(/^\.\//, '')
  if (!normalized.startsWith('/uploads/')) normalized = '/uploads/' + normalized
  return normalized
}

// ── Delete record ──
async function handleDeleteRecord(id: number) {
  try {
    await deleteImageRecord(id)
    records.value = records.value.filter(r => r.id !== id)
    toast.success(t('toast.deleteSuccess'))
  } catch {
    toast.error(t('error.networkError'))
  }
}

async function handleCopyPrompt(recordId: number, text: string) {
  try {
    await navigator.clipboard.writeText(text)
    copiedRecordId.value = recordId
    setTimeout(() => { copiedRecordId.value = null }, 1500)
  } catch {
    toast.error(t('error.operationFailed'))
  }
}

// ── Lightbox ──
function openLightbox(url: string) {
  lightboxUrl.value = resultImageUrl(url)
  zoomLevel.value = 1
  panX.value = 0
  panY.value = 0
  lightboxOpen.value = true
}

function closeLightbox() {
  lightboxOpen.value = false
  lightboxUrl.value = ''
}

function handleWheel(e: WheelEvent) {
  e.preventDefault()
  const delta = e.deltaY > 0 ? -0.1 : 0.1
  zoomLevel.value = Math.max(0.5, Math.min(5, zoomLevel.value + delta))
}

function resetZoom() {
  zoomLevel.value = 1
  panX.value = 0
  panY.value = 0
}

function formatTime(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function getModeLabel(mode: string): string {
  return mode === 'GENERATE' ? t('imageGen.modeGenerate') : t('imageGen.modeEdit')
}

function formatDateTime(dateStr: string): string {
  const d = new Date(dateStr)
  return d.toLocaleString()
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader :title="$t('pageTitle.imageGeneration')" :description="$t('pageTitle.imageGenDesc')" />

    <!-- No model warning -->
    <div v-if="!modelLoading && !hasModel" class="flex items-center gap-2 p-4 bg-amber-50 dark:bg-amber-950/20 border border-amber-200 dark:border-amber-800 rounded-lg text-amber-700 dark:text-amber-400 text-sm">
      <AlertCircle class="h-4 w-4 shrink-0" />
      <span>{{ $t('imageGen.noModel') }}</span>
    </div>

    <!-- Mode Tabs -->
    <Tabs v-model="activeMode" class="w-full">
      <TabsList>
        <TabsTrigger value="generate">{{ $t('imageGen.modeGenerate') }}</TabsTrigger>
        <TabsTrigger value="edit">{{ $t('imageGen.modeEdit') }}</TabsTrigger>
      </TabsList>

      <!-- Two-column layout: params (left) + result (right) -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mt-4">
        <!-- Left: Parameters -->
        <div class="space-y-4">
          <TabsContent value="generate" class="space-y-4 mt-0">
            <div class="space-y-2">
              <label for="gen-prompt" class="text-sm font-medium">{{ $t('imageGen.prompt') }}</label>
              <Textarea
                id="gen-prompt"
                v-model="prompt"
                :placeholder="$t('imageGen.promptPlaceholder')"
                class="min-h-[200px]"
              />
            </div>
          </TabsContent>

          <TabsContent value="edit" class="space-y-4 mt-0">
            <!-- File upload -->
            <div class="space-y-2">
              <label for="ref-images-upload" class="text-sm font-medium">{{ $t('imageGen.referenceImages') }} ({{ editImages.length }}/4)</label>
              <div class="flex flex-wrap gap-3">
                <div
                  v-for="(url, idx) in editPreviewUrls"
                  :key="idx"
                  class="relative group w-20 h-20 rounded-lg overflow-hidden border border-border"
                >
                  <img :src="url" class="w-full h-full object-cover" alt="reference" />
                  <button
                    class="absolute top-0.5 left-0.5 opacity-0 group-hover:opacity-100 bg-black/50 rounded-full p-1 text-white transition-opacity"
                    @click.stop="openLightbox(url)"
                  >
                    <ZoomIn class="h-3 w-3" />
                  </button>
                  <button
                    class="absolute top-0.5 right-0.5 opacity-0 group-hover:opacity-100 bg-black/50 rounded-full p-1 text-white transition-opacity"
                    @click="removeImage(idx)"
                  >
                    <Trash2 class="h-3 w-3" />
                  </button>
                </div>
                <label
                  v-if="editImages.length < 4"
                  class="flex items-center justify-center w-20 h-20 rounded-lg border-2 border-dashed border-border cursor-pointer hover:border-primary/50 transition-colors"
                >
                  <Upload class="h-5 w-5 text-muted-foreground" />
                  <input id="ref-images-upload" type="file" accept="image/*" multiple class="hidden" @change="handleFileSelect" />
                </label>
              </div>
              <Button v-if="editImages.length < 4" variant="outline" size="sm" class="mt-1" @click="openAssetPicker">
                <Image class="h-4 w-4 mr-1" />
                选择素材
              </Button>
            </div>

            <!-- Mask upload (optional, local repaint) -->
            <div class="space-y-2">
              <label class="text-sm font-medium">{{ $t('imageGen.maskImage') }}</label>
              <p class="text-xs text-muted-foreground">{{ $t('imageGen.maskHint') }}</p>
              <div class="flex flex-wrap gap-3 items-center">
                <div v-if="maskPreviewUrl" class="relative group w-20 h-20 rounded-lg overflow-hidden border border-border">
                  <img :src="maskPreviewUrl" class="w-full h-full object-cover" alt="mask" />
                  <button
                    class="absolute top-0.5 right-0.5 opacity-0 group-hover:opacity-100 bg-black/50 rounded-full p-1 text-white transition-opacity"
                    @click="removeMask()"
                  >
                    <Trash2 class="h-3 w-3" />
                  </button>
                </div>
                <label
                  v-if="!maskPreviewUrl"
                  class="flex items-center justify-center w-20 h-20 rounded-lg border-2 border-dashed border-border cursor-pointer hover:border-primary/50 transition-colors"
                >
                  <Image class="h-5 w-5 text-muted-foreground" />
                  <input id="mask-upload" type="file" accept="image/png" class="hidden" @change="handleMaskSelect" />
                </label>
              </div>
            </div>

            <div class="space-y-2">
              <label for="edit-prompt" class="text-sm font-medium">{{ $t('imageGen.editPrompt') }}</label>
              <Textarea
                id="edit-prompt"
                v-model="prompt"
                :placeholder="$t('imageGen.editPromptPlaceholder')"
                class="min-h-[200px]"
              />
            </div>
          </TabsContent>

          <!-- Common params: model + size + quality + count + button -->
          <div class="flex items-end gap-1">
            <div v-if="imageModels.length > 1" class="space-y-2">
              <span class="text-sm font-medium">生图模型</span>
              <Select v-model="selectedModelId">
                <SelectTrigger class="min-w-[140px]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem v-for="m in imageModels" :key="m.id" :value="m.id">{{ m.name }}</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div class="space-y-2">
              <span class="text-sm font-medium">{{ $t('imageGen.size') }}</span>
              <Select v-model="size">
                <SelectTrigger class="min-w-[160px]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem v-for="opt in sizeOptions" :key="opt.value" :value="opt.value">
                    {{ opt.label }}
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div class="space-y-2">
              <span class="text-sm font-medium">{{ $t('imageGen.quality') }}</span>
              <Select v-model="quality">
                <SelectTrigger class="min-w-[110px]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem v-for="opt in qualityOptions" :key="opt.value" :value="opt.value">
                    {{ opt.label }}
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div class="space-y-2">
              <span class="text-sm font-medium">{{ $t('imageGen.count') }}</span>
              <div class="flex items-center border border-border rounded-md h-8">
                <Button variant="ghost" size="sm" class="h-full w-6 rounded-none px-0 text-sm" :disabled="imageCount <= 1" @click="imageCount = Math.max(1, imageCount - 1)">-</Button>
                <Input
                  v-model.number="imageCount"
                  type="number"
                  min="1"
                  max="10"
                  class="w-9 h-full text-center border-0 rounded-none text-sm [appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none"
                  @blur="imageCount = Math.max(1, Math.min(10, imageCount || 1))"
                />
                <Button variant="ghost" size="sm" class="h-full w-6 rounded-none px-0 text-sm" :disabled="imageCount >= 10" @click="imageCount = Math.min(10, imageCount + 1)">+</Button>
              </div>
            </div>
            <div class="self-end flex items-center gap-2">
              <TooltipProvider>
                <Tooltip>
                  <TooltipTrigger as-child>
                    <span tabindex="0">
                      <Button
                        :disabled="!canGenerate || (activeMode === 'edit' && editImages.length === 0) || generating"
                        @click="activeMode === 'generate' ? handleGenerate() : handleEdit()"
                      >
                        <Loader2 v-if="generating" class="mr-2 h-4 w-4 animate-spin" />
                        <Image v-else class="mr-2 h-4 w-4" />
                        {{ generating ? $t('imageGen.generating') : (activeMode === 'generate' ? $t('imageGen.generate') : $t('imageGen.edit')) }}
                      </Button>
                    </span>
                  </TooltipTrigger>
                  <TooltipContent v-if="!hasModel">
                    {{ $t('imageGen.noModelTip') }}
                  </TooltipContent>
                </Tooltip>
              </TooltipProvider>
              <span v-if="generating" class="text-xs text-muted-foreground whitespace-nowrap">
                {{ $t('imageGen.timer', { seconds: timerSeconds }) }}
              </span>
            </div>
          </div>
        </div>

        <!-- Right: Result -->
        <div>
          <Card v-if="hasResult" class="overflow-hidden h-full">
            <CardContent class="p-4 space-y-3">
              <div class="flex items-center justify-between">
                <h3 class="font-semibold text-sm">{{ $t('imageGen.result') }}（{{ result!.urls.length }} 张）</h3>
                <Badge variant="secondary" class="text-xs">
                  {{ formatTime(result!.timeCostMs) }}
                </Badge>
              </div>

              <!-- Image grid -->
              <div class="grid grid-cols-2 gap-3">
                <div
                  v-for="(imgUrl, idx) in result!.urls"
                  :key="idx"
                  class="rounded-lg overflow-hidden border border-border bg-muted/30 cursor-zoom-in"
                  @click="openLightbox(imgUrl)"
                >
                  <img
                    :src="resultImageUrl(imgUrl)"
                    class="w-full h-auto object-contain hover:opacity-90 transition-opacity"
                    :alt="'Generated image ' + (idx + 1)"
                  />
                </div>
              </div>

              <div v-if="result!.revisedPrompt" class="text-xs text-muted-foreground bg-muted/30 rounded-md p-3">
                <span class="font-medium">{{ $t('imageGen.revisedPrompt') }}:</span>
                {{ result!.revisedPrompt }}
              </div>

              <div class="flex flex-wrap gap-2">
                <Button variant="outline" size="sm" @click="result = null; prompt = ''">
                  <RefreshCw class="mr-1 h-3 w-3" />
                  {{ $t('imageGen.generateAgain') }}
                </Button>
                <Button v-for="(imgUrl, idx) in result!.urls" :key="'dl-' + idx" variant="outline" size="sm" as-child>
                  <a :href="resultImageUrl(imgUrl)" :download="'image-' + (idx + 1) + '.png'" target="_blank">
                    <Image class="mr-1 h-3 w-3" />
                    {{ $t('imageGen.download') }}{{ result!.urls.length > 1 ? ' #' + (idx + 1) : '' }}
                  </a>
                </Button>
              </div>
            </CardContent>
          </Card>

          <!-- Empty state before any generation -->
          <div v-else class="flex flex-col items-center justify-center h-full min-h-[300px] rounded-lg border-2 border-dashed border-border text-muted-foreground">
            <Image class="h-12 w-12 mb-2 opacity-40" />
            <p class="text-sm">{{ generating ? $t('imageGen.generating') + '...' : $t('imageGen.noResult') }}</p>
          </div>
        </div>
      </div>
    </Tabs>

    <!-- ═══ History Section ═══ -->
    <div class="space-y-4 pt-4 border-t border-border">
      <h3 class="text-lg font-semibold">{{ $t('imageGen.history') }}</h3>

      <!-- Filters -->
      <div class="flex flex-wrap items-center gap-3">
        <div class="flex items-center gap-2">
          <label for="filter-start-date" class="text-xs text-muted-foreground shrink-0">{{ $t('imageGen.filterStartDate') }}</label>
          <input
            id="filter-start-date"
            type="date"
            v-model="filterStartDate"
            class="h-9 rounded-md border border-input bg-background px-3 text-sm"
          />
        </div>
        <div class="flex items-center gap-2">
          <label for="filter-end-date" class="text-xs text-muted-foreground shrink-0">{{ $t('imageGen.filterEndDate') }}</label>
          <input
            id="filter-end-date"
            type="date"
            v-model="filterEndDate"
            class="h-9 rounded-md border border-input bg-background px-3 text-sm"
          />
        </div>
        <div class="flex-1 min-w-[200px] flex items-center gap-2">
          <label for="filter-prompt" class="text-xs text-muted-foreground shrink-0">{{ $t('imageGen.filterPrompt') }}</label>
          <Input
            id="filter-prompt"
            v-model="filterPrompt"
            :placeholder="$t('imageGen.filterPromptPlaceholder')"
            class="h-9"
            @keyup.enter="handleSearch"
          />
        </div>
        <Button variant="outline" size="sm" class="h-9" @click="handleSearch">
          <Search class="mr-1 h-3 w-3" />
          {{ $t('imageGen.query') }}
        </Button>
      </div>

      <!-- Records -->
      <div v-if="historyLoading" class="grid gap-4 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
        <Skeleton v-for="i in 5" :key="i" class="h-44 w-full rounded-lg" />
      </div>

      <EmptyState
        v-else-if="records.length === 0"
        :icon="Image"
        :title="$t('imageGen.noHistory')"
        :description="$t('imageGen.noHistoryDesc')"
      />

      <div v-else class="grid gap-4 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
        <Card
          v-for="record in records"
          :key="record.id"
          class="overflow-hidden group cursor-pointer hover:shadow-md transition-shadow rounded-t-none"
          @click="openLightbox(firstResultPath(record))"
        >
          <div class="relative aspect-[4/3] bg-muted/30 -mt-4">
            <img
              :src="resultImageUrl(firstResultPath(record))"
              class="w-full h-full object-cover"
              :alt="record.prompt?.slice(0, 60) || 'generated image'"
              loading="lazy"
            />
          </div>
          <CardContent class="p-3 space-y-1.5">
            <div class="flex items-center gap-2 flex-wrap">
              <Badge variant="outline" class="text-xs">{{ getModeLabel(record.mode) }}</Badge>
              <Badge v-if="record.width && record.height" variant="outline" class="text-xs text-muted-foreground">{{ record.width }}×{{ record.height }}</Badge>
              <Badge variant="secondary" class="text-xs">{{ formatTime(record.timeCostMs) }}</Badge>
            </div>
            <p class="text-xs text-muted-foreground line-clamp-2">{{ record.prompt }}</p>
            <p class="text-xs text-muted-foreground/60">{{ formatDateTime(record.createdAt) }}</p>
            <div class="flex items-center gap-2 pt-1">
              <Button
                variant="ghost"
                size="sm"
                class="h-6 text-xs text-muted-foreground hover:text-foreground gap-1 px-1.5"
                as-child
              >
                <a :href="resultImageUrl(firstResultPath(record))" download target="_blank" @click.stop>
                  <Download class="h-3 w-3" />
                  {{ $t('imageGen.download') }}
                </a>
              </Button>
              <Button
                variant="ghost"
                size="sm"
                class="h-6 text-xs text-muted-foreground hover:text-foreground gap-1 px-1.5"
                @click.stop="handleCopyPrompt(record.id, record.prompt)"
              >
                <Check v-if="copiedRecordId === record.id" class="h-3 w-3 text-green-500" />
                <Copy v-else class="h-3 w-3" />
                {{ $t('imageGen.copyPrompt') }}
              </Button>
              <Button
                variant="ghost"
                size="sm"
                class="h-6 text-xs text-muted-foreground hover:text-primary gap-1 px-1.5"
                @click.stop="openAssetUpload(record)"
              >
                <Upload class="h-3 w-3" />
                {{ $t('assetLibrary.upload') }}
              </Button>
              <Button
                variant="ghost"
                size="sm"
                class="h-6 text-xs text-muted-foreground hover:text-destructive gap-1 px-1.5"
                @click.stop="handleDeleteRecord(record.id)"
              >
                <Trash2 class="h-3 w-3" />
                {{ $t('common.delete') }}
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>

      <!-- Pagination -->
      <div class="flex items-center justify-between pt-2">
        <p class="text-xs text-muted-foreground">
          {{ $t('imageGen.totalRecords', { count: totalElements }) }}
          <template v-if="totalPages > 0">
            · {{ $t('imageGen.pageInfo', { current: currentPage + 1, total: totalPages }) }}
          </template>
        </p>
        <div v-if="totalPages > 0" class="flex items-center gap-1">
          <Button
            variant="outline"
            size="sm"
            :disabled="currentPage <= 0"
            @click="goToPage(currentPage - 1)"
          >
            <ChevronLeft class="h-3 w-3" />
          </Button>

          <template v-for="(p, pi) in visiblePages" :key="typeof p === 'number' ? p : 'ellipsis-' + pi">
            <span v-if="p === '...'" class="px-1 text-xs text-muted-foreground">...</span>
            <Button
              v-else
              variant="outline"
              size="sm"
              :class="p === currentPage + 1 ? 'bg-primary text-primary-foreground' : ''"
              @click="goToPage(p - 1)"
            >
              {{ p }}
            </Button>
          </template>

          <Button
            variant="outline"
            size="sm"
            :disabled="currentPage >= totalPages - 1"
            @click="goToPage(currentPage + 1)"
          >
            <ChevronRight class="h-3 w-3" />
          </Button>
        </div>
      </div>
    </div>

    <!-- ═══ Image Lightbox（全屏覆盖层，不依赖 Dialog 组件） ═══ -->
    <Teleport to="body">
      <div
        v-if="lightboxOpen"
        class="fixed inset-0 z-[100] bg-black/95 flex items-center justify-center"
        @click="closeLightbox"
        @wheel.prevent="handleWheel"
      >
        <h2 class="sr-only">{{ $t('imageGen.preview') }}</h2>

        <!-- Close button -->
        <button
          class="absolute top-4 right-4 z-10 rounded-full bg-black/50 p-2 text-white hover:bg-black/70 transition-colors"
          @click.stop="closeLightbox"
        >
          <X class="h-5 w-5" />
        </button>

        <!-- Zoom info -->
        <div class="absolute top-4 left-4 z-10 flex items-center gap-2">
          <Badge variant="outline" class="bg-black/50 text-white border-white/20 text-xs">
            {{ Math.round(zoomLevel * 100) }}%
          </Badge>
          <Button
            variant="outline"
            size="sm"
            class="bg-black/50 text-white border-white/20 h-7 text-xs"
            @click.stop="resetZoom"
          >
            <ZoomIn class="h-3 w-3 mr-1" />
            {{ $t('imageGen.resetZoom') }}
          </Button>
        </div>

        <!-- Zoomable image — 保持原始宽高比 -->
        <img
          v-if="lightboxUrl"
          :src="lightboxUrl"
          class="max-w-[90vw] max-h-[90vh] w-auto h-auto transition-transform duration-100"
          :style="{
            transform: `scale(${zoomLevel}) translate(${panX}px, ${panY}px)`,
          }"
          alt="Preview"
          draggable="false"
          @click.stop
        />
      </div>
    </Teleport>

    <!-- ═══ Asset Upload Dialog ═══ -->
    <Dialog :open="assetUploadOpen" @update:open="assetUploadOpen = $event">
      <DialogContent class="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>{{ $t('assetLibrary.uploadTo') }}</DialogTitle>
          <DialogDescription>{{ $t('imageGen.publishSelectImage') }}</DialogDescription>
        </DialogHeader>
        <div class="space-y-4 py-2">
          <div class="space-y-1.5">
            <Label for="asset-space" class="text-sm">{{ $t('assetLibrary.selectSpace') }}</Label>
            <Select v-model="assetUploadSpaceId">
              <SelectTrigger class="min-w-[200px]"><SelectValue :placeholder="$t('assetLibrary.noSpace')" /></SelectTrigger>
              <SelectContent>
                <SelectItem v-for="sp in assetSpaces" :key="sp.id" :value="sp.id">{{ sp.name }}</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="assetUploadOpen = false">{{ $t('common.cancel') }}</Button>
          <Button :disabled="uploadingAsset" @click="submitAssetUpload">
            <Loader2 v-if="uploadingAsset" class="w-4 h-4 mr-2 animate-spin" />
            {{ $t('assetLibrary.upload') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- ═══ Asset Picker Dialog ═══ -->
    <Dialog :open="assetPickerOpen" @update:open="assetPickerOpen = $event">
      <DialogContent class="sm:max-w-[1200px] max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>选择素材</DialogTitle>
        </DialogHeader>
        <div class="space-y-4">
          <div class="flex items-center gap-3 flex-wrap">
            <Select v-model="pickerSpaceId" @update:model-value="loadPickerAssets">
              <SelectTrigger class="min-w-[160px]">
                <SelectValue :placeholder="$t('assetLibrary.allSpaces')" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem v-for="sp in pickerSpaces" :key="sp.id" :value="sp.id">{{ sp.name }}</SelectItem>
              </SelectContent>
            </Select>
            <Input v-model="pickerKeyword" :placeholder="$t('assetLibrary.searchPlaceholder')" class="h-8 text-sm flex-1 min-w-[200px]" @keyup.enter="loadPickerAssets" />
            <Button variant="outline" size="sm" @click="loadPickerAssets">{{ $t('assetLibrary.search') }}</Button>
          </div>
          <div v-if="pickerLoading" class="flex justify-center py-16">
            <Loader2 class="h-8 w-8 animate-spin text-muted-foreground" />
          </div>
          <div v-else-if="pickerAssets.length === 0" class="flex flex-col items-center py-16 text-muted-foreground">
            <Image class="h-12 w-12 mb-2 opacity-40" />
            <p class="text-sm">{{ $t('assetLibrary.noAssets') }}</p>
          </div>
          <div v-else class="grid grid-cols-6 gap-3">
            <div
              v-for="asset in pickerAssets"
              :key="asset.id"
              class="relative aspect-square rounded-md overflow-hidden bg-muted/30 cursor-pointer border border-border hover:border-primary/50 transition-colors group"
              @click="showPickerPreview(asset)"
            >
              <img :src="imageUrl(asset.filePath)" class="w-full h-full object-cover" alt="" loading="lazy" />
              <div class="absolute inset-0 bg-black/0 group-hover:bg-black/20 transition-colors flex items-center justify-center">
                <ZoomIn class="h-6 w-6 text-white opacity-0 group-hover:opacity-100 transition-opacity" />
              </div>
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>

    <!-- ═══ Picker Preview Dialog ═══ -->
    <Dialog :open="pickerPreviewOpen" @update:open="pickerPreviewOpen = $event">
      <DialogContent class="sm:max-w-[50vw] max-h-[85vh] p-0 bg-background/95 backdrop-blur-sm">
        <div class="relative flex items-center justify-center min-h-[50vh] p-8">
          <img
            v-if="pickerPreviewAsset"
            :src="imageUrl(pickerPreviewAsset.filePath)"
            class="max-h-[70vh] max-w-full object-contain rounded-lg"
            :alt="pickerPreviewAsset.fileName"
          />
          <div class="absolute bottom-4 left-1/2 -translate-x-1/2 flex gap-3">
            <Button size="sm" @click="selectPickerPreview">
              <Check class="h-4 w-4 mr-1" />
              选择这张图片
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>

  </div>
</template>
