<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import AspectRatioIcon from '@/components/AspectRatioIcon.vue'
import ImageLightbox from '@/components/ImageLightbox.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'

import {
  Check, Image, Upload, Trash2, RefreshCw, Loader2, AlertCircle, Download,
  ZoomIn,
} from 'lucide-vue-next'
import { generateImage, editImage, createSuperResolutionJob, listSuperResolutionJobs } from '@/api/image'
import { getImageModelsApi } from '@/api/model'
import { listSpaces, listAssets, importFromRecord } from '@/api/assets'
import type { AssetSpace, PublicAsset, PageResponse } from '@/api/assets'
import { toast } from 'vue-sonner'
import { useI18n } from 'vue-i18n'
import type { GeneratedImage, ImageGenerationResult, SuperResolutionJob } from '@/api/image'

const { t } = useI18n()

// ── Model availability ──
const hasModel = ref(false)
const modelLoading = ref(true)
const imageModels = ref<{ id: number; name: string }[]>([])
const selectedModelId = ref<number | undefined>(undefined)

// ── Mode ──

// ── Form ──
const prompt = ref('')
const size = ref('1254x1254')
const quality = ref('high')   // default: high
const imageCount = ref(1)
const editImages = ref<File[]>([])
const editPreviewUrls = ref<string[]>([])
const maskFile = ref<File | undefined>(undefined)
const maskPreviewUrl = ref<string>('')

// ── Generation ──
const generating = ref(false)
const result = ref<ImageGenerationResult | null>(null)
const superResolutionJobs = ref<SuperResolutionJob[]>([])
const hiddenTerminalJobIds = new Set<number>()
const upscaleConfirmOpen = ref(false)
const selectedUpscale = ref<{ image: GeneratedImage; factor: number } | null>(null)
const submittingUpscale = ref(false)
const timerSeconds = ref(0)
let timerInterval: ReturnType<typeof setInterval> | null = null
let queuePollInterval: ReturnType<typeof setInterval> | null = null

onBeforeUnmount(() => {
  stopTimer()
  if (queuePollInterval) clearInterval(queuePollInterval)
})

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
const lightboxOpen = ref(false)
const lightboxUrl = ref('')


// ── Size options with descriptions ──
const sizeOptions = [
  { value: '1024x1024', label: '1024x1024', ratio: '1 / 1', ratioLabel: '1:1' },
  { value: '1254x1254', label: '1254x1254', ratio: '1 / 1', ratioLabel: '1:1' },
  { value: '1672x941', label: '1672x941', ratio: '16 / 9', ratioLabel: '16:9' },
  { value: '1536x1024', label: '1536x1024', ratio: '3 / 2', ratioLabel: '3:2' },
  { value: '1024x1536', label: '1024x1536', ratio: '2 / 3', ratioLabel: '2:3' },
  { value: '1448x1086', label: '1448x1086', ratio: '4 / 3', ratioLabel: '4:3' },
  { value: '1659x948', label: '1659x948', ratio: '7 / 4', ratioLabel: '7:4' },
]
const qualityOptions = [
  { value: 'low', label: 'low' },
  { value: 'medium', label: 'medium' },
  { value: 'high', label: 'high' },
  { value: 'auto', label: 'auto' },
]

const selectedSizeOption = computed(() => sizeOptions.find(opt => opt.value === size.value) ?? sizeOptions[0])
const canGenerate = computed(() => hasModel.value && prompt.value.trim().length > 0)
const hasResult = computed(() => result.value && result.value.urls && result.value.urls.length > 0)
const resultImageUrl = (url: string) => {
  if (!url) return ''
  if (url.startsWith('blob:') || url.startsWith('http://') || url.startsWith('https://')) return url
  // Backend now returns paths starting with /uploads/ — ensure consistency
  return url.replace(/\\/g, '/')
}

// ── Lifecycle ──
const generatedImages = computed<GeneratedImage[]>(() => result.value?.images ?? [])

function canUpscale(image: GeneratedImage): boolean {
  if (!image.width || !image.height) return false
  return Math.max(image.width, image.height) <= 1920 && Math.min(image.width, image.height) <= 1080
}

function availableUpscaleFactors(image: GeneratedImage): number[] {
  return canUpscale(image) ? [2, 3, 4] : []
}

function updateGeneratedDimensions(image: GeneratedImage, event: Event) {
  const element = event.target as HTMLImageElement
  if (!image.width || !image.height) {
    image.width = element.naturalWidth
    image.height = element.naturalHeight
  }
}

function requestUpscale(image: GeneratedImage, factor: number) {
  selectedUpscale.value = { image, factor }
  upscaleConfirmOpen.value = true
}

async function confirmUpscale() {
  if (!selectedUpscale.value || submittingUpscale.value) return
  submittingUpscale.value = true
  try {
    const job = await createSuperResolutionJob(selectedUpscale.value.image.recordId, selectedUpscale.value.factor, 'IMAGE_GENERATION')
    superResolutionJobs.value = [job, ...superResolutionJobs.value.filter(item => item.id !== job.id)]
    upscaleConfirmOpen.value = false
    selectedUpscale.value = null
    toast.success('超分任务已加入队列')
  } finally {
    submittingUpscale.value = false
  }
}

async function loadSuperResolutionJobs() {
  try {
    const jobs = await listSuperResolutionJobs('IMAGE_GENERATION')
    superResolutionJobs.value = jobs.filter(job =>
      !hiddenTerminalJobIds.has(job.id) || job.status === 'PENDING' || job.status === 'RUNNING',
    )
  } catch { /* request interceptor handles errors */ }
}

function hideCompletedUpscaleJobs() {
  superResolutionJobs.value.forEach(job => {
    if (job.status === 'SUCCEEDED' || job.status === 'FAILED') hiddenTerminalJobIds.add(job.id)
  })
  superResolutionJobs.value = superResolutionJobs.value.filter(job =>
    job.status === 'PENDING' || job.status === 'RUNNING',
  )
}
onMounted(async () => {
  await loadSuperResolutionJobs()
  queuePollInterval = setInterval(loadSuperResolutionJobs, 10000)
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
})

// ── Generate ──
async function handleGenerate() {
  if (!canGenerate.value || generating.value) return
  generating.value = true
  hideCompletedUpscaleJobs()
  result.value = null
  startTimer()
  try {
    if (editImages.value.length > 0) {
      result.value = await editImage(prompt.value, editImages.value, size.value, quality.value, maskFile.value, imageCount.value, selectedModelId.value)
    } else {
      result.value = await generateImage(prompt.value, size.value, quality.value, imageCount.value, selectedModelId.value)
    }
    if (result.value.failedCount > 0) {
      toast.success(`${result.value.urls.length} 张生成成功，${result.value.failedCount} 张失败`)
    } else {
      toast.success(t('toast.imageGenerated'))
    }
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
// ── Lightbox ──
function openLightbox(url: string) {
  lightboxUrl.value = resultImageUrl(url)
  lightboxOpen.value = true
}

function formatTime(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
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

    <!-- Two-column layout: params (left) + result (right) -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mt-4">
        <!-- Left: Parameters -->
        <div class="space-y-4">
          <!-- File upload (always visible, provides reference images for edit mode) -->
          <div class="space-y-2">
            <label for="ref-images-upload" class="text-sm font-medium">{{ $t('imageGen.referenceImages') }} ({{ editImages.length }}/4)</label>
            <p class="text-xs text-muted-foreground">未上传参考图则走文生图，上传后自动切换为图生图</p>
            <div class="flex flex-wrap gap-3">
              <div
                v-for="(url, idx) in editPreviewUrls"
                :key="idx"
                class="relative group w-20 h-20 rounded-lg overflow-hidden border border-border"
              >
                <img :src="url" class="w-full h-full object-cover" alt="reference" />
                <Button
                  variant="ghost"
                  size="icon"
                  class="absolute top-0.5 left-0.5 opacity-0 group-hover:opacity-100 bg-black/50 text-white hover:bg-black/70 rounded-full h-6 w-6 transition-opacity"
                  @click.stop="openLightbox(url)"
                >
                  <ZoomIn class="h-3 w-3" />
                </Button>
                <Button
                  variant="ghost"
                  size="icon"
                  class="absolute top-0.5 right-0.5 opacity-0 group-hover:opacity-100 bg-black/50 text-white hover:bg-black/70 rounded-full h-6 w-6 transition-opacity"
                  @click="removeImage(idx)"
                >
                  <Trash2 class="h-3 w-3" />
                </Button>
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

          <!-- Mask upload (optional, only when reference images exist) -->
          <div v-if="editImages.length > 0" class="space-y-2">
            <label class="text-sm font-medium">{{ $t('imageGen.maskImage') }}</label>
            <p class="text-xs text-muted-foreground">{{ $t('imageGen.maskHint') }}</p>
            <div class="flex flex-wrap gap-3 items-center">
              <div v-if="maskPreviewUrl" class="relative group w-20 h-20 rounded-lg overflow-hidden border border-border">
                <img :src="maskPreviewUrl" class="w-full h-full cursor-zoom-in object-cover" alt="mask" @click="openLightbox(maskPreviewUrl)" />
                <Button
                  variant="ghost"
                  size="icon"
                  class="absolute top-0.5 right-0.5 opacity-0 group-hover:opacity-100 bg-black/50 text-white hover:bg-black/70 rounded-full h-6 w-6 transition-opacity"
                  @click="removeMask()"
                >
                  <Trash2 class="h-3 w-3" />
                </Button>
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

          <!-- Prompt -->
          <div class="space-y-2">
            <label for="gen-prompt" class="text-sm font-medium">{{ editImages.length > 0 ? $t('imageGen.editPrompt') : $t('imageGen.prompt') }}</label>
            <Textarea
              id="gen-prompt"
              v-model="prompt"
              :placeholder="editImages.length > 0 ? $t('imageGen.editPromptPlaceholder') : $t('imageGen.promptPlaceholder')"
              class="min-h-[200px]"
            />
          </div>

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
                  <SelectValue>
                    <span class="flex items-center gap-2">
                      {{ selectedSizeOption.label }}
                      <AspectRatioIcon :ratio="selectedSizeOption.ratio" />
                      <span class="text-muted-foreground">{{ selectedSizeOption.ratioLabel }}</span>
                    </span>
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem v-for="opt in sizeOptions" :key="opt.value" :value="opt.value">
                    <span class="flex items-center gap-2">{{ opt.label }}<AspectRatioIcon :ratio="opt.ratio" /><span class="text-muted-foreground">{{ opt.ratioLabel }}</span></span>
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
                        :disabled="!canGenerate || generating"
                        @click="handleGenerate()"
                      >
                        <Loader2 v-if="generating" class="mr-2 h-4 w-4 animate-spin" />
                        <Image v-else class="mr-2 h-4 w-4" />
                        {{ generating ? $t('imageGen.generating') : (editImages.length > 0 ? $t('imageGen.edit') : $t('imageGen.generate')) }}
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

          <div v-if="superResolutionJobs.length > 0" class="space-y-2 border-t border-border pt-4">
            <div class="flex items-center justify-between">
              <h3 class="text-sm font-medium">超分队列</h3>
              <Badge variant="secondary" class="text-xs">{{ superResolutionJobs.length }}</Badge>
            </div>
            <div class="grid gap-2 sm:grid-cols-2">
              <div
                v-for="job in superResolutionJobs"
                :key="job.id"
                class="grid min-h-24 grid-cols-[88px_32px_1fr] items-center gap-3 overflow-hidden rounded-md border border-border bg-muted/20 p-2"
              >
                <button
                  type="button"
                  class="h-20 w-[88px] cursor-zoom-in overflow-hidden rounded bg-muted"
                  title="查看大图"
                  @click="openLightbox(job.status === 'SUCCEEDED' && job.resultPath ? job.resultPath : job.sourcePath)"
                >
                  <img
                    :src="resultImageUrl(job.status === 'SUCCEEDED' && job.resultPath ? job.resultPath : job.sourcePath)"
                    class="h-full w-full object-cover transition-opacity hover:opacity-90"
                    :alt="'超分任务 ' + job.id"
                  />
                </button>
                <Loader2 v-if="job.status === 'PENDING' || job.status === 'RUNNING'" class="h-5 w-5 animate-spin text-primary" />
                <AlertCircle v-else-if="job.status === 'FAILED'" class="h-5 w-5 text-destructive" />
                <Check v-else class="h-5 w-5 text-emerald-600" />
                <div class="min-w-0 text-sm">
                  <p v-if="job.status === 'PENDING' || job.status === 'RUNNING'" class="font-medium">正在超分，请等待...</p>
                  <p v-else-if="job.status === 'FAILED'" class="font-medium text-destructive">超分失败</p>
                  <p v-else class="font-medium">超分完成</p>
                  <p class="mt-1 text-xs text-muted-foreground">放大 x{{ job.upscaleFactor }}</p>
                  <p v-if="job.width && job.height" class="text-xs text-muted-foreground">{{ job.width }}x{{ job.height }}</p>
                  <p v-if="job.errorMessage" class="mt-1 truncate text-xs text-destructive" :title="job.errorMessage">{{ job.errorMessage }}</p>
                  <Button
                    v-if="job.status === 'SUCCEEDED' && job.resultPath"
                    variant="outline"
                    size="icon"
                    class="mt-2 h-7 w-7"
                    as-child
                  >
                    <a
                      :href="resultImageUrl(job.resultPath)"
                      :download="'super-resolution-' + job.id + '.png'"
                      target="_blank"
                      title="下载超分图片"
                    >
                      <Download class="h-3.5 w-3.5" />
                    </a>
                  </Button>                </div>
              </div>
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

              <!-- Image cards -->
              <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div
                  v-for="(image, idx) in generatedImages"
                  :key="image.recordId"
                  class="overflow-hidden rounded-md border border-border bg-background"
                >
                  <button type="button" class="block w-full cursor-zoom-in bg-muted/30" @click="openLightbox(image.url)">
                    <img
                      :src="resultImageUrl(image.url)"
                      class="aspect-square w-full object-contain transition-opacity hover:opacity-90"
                      :alt="'Generated image ' + (idx + 1)"
                      @load="updateGeneratedDimensions(image, $event)"
                    />
                  </button>
                  <div class="space-y-2 border-t border-border p-3">
                    <div class="flex items-center justify-between gap-2 text-xs">
                      <span class="font-medium">可超分倍率</span>
                      <span v-if="image.width && image.height" class="text-muted-foreground">{{ image.width }}x{{ image.height }}</span>
                    </div>
                    <div v-if="availableUpscaleFactors(image).length" class="flex gap-2">
                      <Button
                        v-for="factor in availableUpscaleFactors(image)"
                        :key="factor"
                        variant="outline"
                        size="sm"
                        class="h-7 flex-1"
                        @click="requestUpscale(image, factor)"
                      >
                        x{{ factor }}
                      </Button>
                    </div>
                    <p v-else class="text-xs text-muted-foreground">该图片尺寸不符合超分输入限制</p>
                  </div>
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

    <!-- ═══ Image Lightbox（全屏覆盖层，不依赖 Dialog 组件） ═══ -->
    <ImageLightbox v-model:open="lightboxOpen" :src="lightboxUrl" />

    <!-- ═══ Asset Upload Dialog ═══ -->
    <Dialog :open="upscaleConfirmOpen" @update:open="upscaleConfirmOpen = $event">
      <DialogContent class="sm:max-w-[420px]">
        <DialogHeader>
          <DialogTitle>确认进行超分</DialogTitle>
          <DialogDescription>
            将图片放大 x{{ selectedUpscale?.factor }}，任务提交后会在超分队列中执行。
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" :disabled="submittingUpscale" @click="upscaleConfirmOpen = false">取消</Button>
          <Button :disabled="submittingUpscale" @click="confirmUpscale">
            <Loader2 v-if="submittingUpscale" class="mr-2 h-4 w-4 animate-spin" />
            确认超分
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
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
            @click="openLightbox(imageUrl(pickerPreviewAsset.filePath))"
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
