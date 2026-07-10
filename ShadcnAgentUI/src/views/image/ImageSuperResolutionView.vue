<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import ImageLightbox from '@/components/ImageLightbox.vue'
import { useImageLightbox } from '@/composables/useImageLightbox'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import {
  Check, Download, Expand, History, ImagePlus, Loader2, RefreshCw, Search, Upload, XCircle,
} from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import {
  createSuperResolutionJob,
  listSuperResolutionJobs,
  listSuperResolutionSources,
  retrySuperResolutionJob,
  uploadSuperResolutionJob,
} from '@/api/image'
import type { ImageRecord, SuperResolutionJob } from '@/api/image'

const { lightboxOpen, lightboxUrl, lightboxAlt, openLightbox } = useImageLightbox()

const sourceTab = ref<'upload' | 'history'>('upload')
const uploadFile = ref<File | null>(null)
const uploadPreviewUrl = ref('')
const selectedHistory = ref<ImageRecord | null>(null)
const sourceWidth = ref<number | null>(null)
const sourceHeight = ref<number | null>(null)
const validationError = ref('')
const selectedFactor = ref(2)
const confirmOpen = ref(false)
const submitting = ref(false)

const jobs = ref<SuperResolutionJob[]>([])
const loadingJobs = ref(false)
let pollTimer: ReturnType<typeof setInterval> | null = null
let jobsInitialized = false
let pollingDisposed = false

const historyOpen = ref(false)
const historyRecords = ref<ImageRecord[]>([])
const historyLoading = ref(false)
const historyPage = ref(0)
const historyTotalPages = ref(1)
const historyPrompt = ref('')
const historyStartDate = ref('')
const historyEndDate = ref('')
const pendingHistory = ref<ImageRecord | null>(null)

const activeJobs = computed(() => jobs.value.filter(job => job.status === 'PENDING' || job.status === 'RUNNING'))
const recentJobs = computed(() => jobs.value.filter(job => job.status === 'SUCCEEDED' || job.status === 'FAILED').slice(0, 20))
const hasSource = computed(() => sourceTab.value === 'upload' ? !!uploadFile.value : !!selectedHistory.value)
const canSubmit = computed(() => hasSource.value && !validationError.value && !!sourceWidth.value && !!sourceHeight.value)
const expectedWidth = computed(() => sourceWidth.value ? sourceWidth.value * selectedFactor.value : null)
const expectedHeight = computed(() => sourceHeight.value ? sourceHeight.value * selectedFactor.value : null)
const selectedSourcePath = computed(() => selectedHistory.value?.resultPath ?? uploadPreviewUrl.value)

watch(sourceTab, () => clearSource())

onMounted(() => {
  pollJobs()
})

onBeforeUnmount(() => {
  pollingDisposed = true
  if (pollTimer) clearTimeout(pollTimer)
  revokeUploadPreview()
})

function imageUrl(path: string | null | undefined): string {
  if (!path) return ''
  if (/^(blob:|https?:\/\/)/i.test(path)) return path
  return path.replace(/\\/g, '/')
}

function firstImagePath(record: ImageRecord): string {
  return record.resultPath?.split('\n').find(Boolean) ?? ''
}

function clearSource() {
  uploadFile.value = null
  selectedHistory.value = null
  sourceWidth.value = null
  sourceHeight.value = null
  validationError.value = ''
  selectedFactor.value = 2
  revokeUploadPreview()
}

function revokeUploadPreview() {
  if (uploadPreviewUrl.value) URL.revokeObjectURL(uploadPreviewUrl.value)
  uploadPreviewUrl.value = ''
}

async function handleFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  revokeUploadPreview()
  uploadFile.value = file
  sourceWidth.value = null
  sourceHeight.value = null
  validationError.value = ''
  uploadPreviewUrl.value = URL.createObjectURL(file)

  const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (!['png', 'jpg', 'jpeg', 'bmp'].includes(extension)) {
    validationError.value = '仅支持 PNG、JPG、JPEG 或 BMP 图片'
  } else if (file.size > 10 * 1024 * 1024) {
    validationError.value = '图片文件不能超过 10 MB'
  }

  try {
    const dimensions = await readImageDimensions(uploadPreviewUrl.value)
    sourceWidth.value = dimensions.width
    sourceHeight.value = dimensions.height
    const sizeError = validateDimensions(dimensions.width, dimensions.height)
    if (!validationError.value && sizeError) validationError.value = sizeError
  } catch {
    validationError.value = '无法读取图片，请检查文件是否有效'
  }
}

function readImageDimensions(url: string): Promise<{ width: number; height: number }> {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve({ width: image.naturalWidth, height: image.naturalHeight })
    image.onerror = reject
    image.src = url
  })
}

function validateDimensions(width: number, height: number): string {
  if (Math.max(width, height) > 1920 || Math.min(width, height) > 1080) {
    return `图片尺寸 ${width}x${height} 超出限制：长边不超过 1920，短边不超过 1080`
  }
  return ''
}

function openHistoryPicker() {
  pendingHistory.value = selectedHistory.value
  historyOpen.value = true
  historyPage.value = 0
  loadHistory()
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const result = await listSuperResolutionSources({
      page: historyPage.value,
      size: 12,
      prompt: historyPrompt.value || undefined,
      startDate: historyStartDate.value || undefined,
      endDate: historyEndDate.value || undefined,
    })
    historyRecords.value = result.content ?? []
    historyTotalPages.value = Math.max(1, result.page?.totalPages ?? 1)
  } finally {
    historyLoading.value = false
  }
}

function selectHistoryRecord(record: ImageRecord) {
  pendingHistory.value = record
}

function confirmHistorySelection() {
  if (!pendingHistory.value) return
  selectedHistory.value = pendingHistory.value
  sourceWidth.value = pendingHistory.value.width ?? null
  sourceHeight.value = pendingHistory.value.height ?? null
  validationError.value = sourceWidth.value && sourceHeight.value
    ? validateDimensions(sourceWidth.value, sourceHeight.value)
    : '历史图片缺少尺寸信息'
  historyOpen.value = false
}

function changeHistoryPage(delta: number) {
  historyPage.value = Math.max(0, Math.min(historyTotalPages.value - 1, historyPage.value + delta))
  loadHistory()
}

function openSubmitConfirm() {
  if (canSubmit.value) confirmOpen.value = true
}

async function submitJob() {
  if (!canSubmit.value || submitting.value) return
  submitting.value = true
  try {
    const job = sourceTab.value === 'upload' && uploadFile.value
      ? await uploadSuperResolutionJob(uploadFile.value, selectedFactor.value, 'SUPER_RESOLUTION_PAGE')
      : await createSuperResolutionJob(selectedHistory.value!.id, selectedFactor.value, 'SUPER_RESOLUTION_PAGE')
    jobs.value = [job, ...jobs.value.filter(item => item.id !== job.id)]
    confirmOpen.value = false
    toast.success('超分任务已加入队列')
    clearSource()
  } finally {
    submitting.value = false
  }
}

async function pollJobs() {
  const succeeded = await loadJobs()
  const delay = !succeeded ? 60_000 : activeJobs.value.length > 0 ? 10_000 : 30_000
  if (!pollingDisposed) pollTimer = setTimeout(pollJobs, delay)
}

async function loadJobs(): Promise<boolean> {
  if (!jobs.value.length) loadingJobs.value = true
  try {
    const previous = new Map(jobs.value.map(job => [job.id, job.status]))
    const updated = await listSuperResolutionJobs()
    if (jobsInitialized) {
      updated.forEach(job => {
        const oldStatus = previous.get(job.id)
        if ((oldStatus === 'PENDING' || oldStatus === 'RUNNING') && job.status === 'SUCCEEDED') {
          toast.success(`超分任务 #${job.id} 已完成`)
        } else if ((oldStatus === 'PENDING' || oldStatus === 'RUNNING') && job.status === 'FAILED') {
          toast.error(`超分任务 #${job.id} 失败`)
        }
      })
    }
    jobs.value = updated
    jobsInitialized = true
    return true
  } catch {
    return false
  } finally {
    loadingJobs.value = false
  }
}

async function retryJob(job: SuperResolutionJob) {
  try {
    const retried = await retrySuperResolutionJob(job.id)
    jobs.value = [retried, ...jobs.value]
    toast.success('重试任务已加入队列')
  } catch { /* request interceptor handles errors */ }
}

function download(path: string) {
  const link = document.createElement('a')
  link.href = imageUrl(path)
  link.download = path.split('/').pop() || 'super-resolution.png'
  link.target = '_blank'
  link.click()
}

function formatTime(value: string): string {
  return new Date(value).toLocaleString()
}
</script>

<template>
  <div class="mx-auto max-w-7xl space-y-6">
    <PageHeader title="图像超分" description="从本地文件或生成历史中选择图片并加入超分队列" />

    <section class="grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
      <div class="space-y-5">
        <Tabs v-model="sourceTab">
          <TabsList class="grid w-full max-w-sm grid-cols-2">
            <TabsTrigger value="upload"><Upload class="mr-2 h-4 w-4" />本地上传</TabsTrigger>
            <TabsTrigger value="history"><History class="mr-2 h-4 w-4" />生成历史</TabsTrigger>
          </TabsList>
        </Tabs>

        <div v-if="sourceTab === 'upload' && !uploadFile" class="flex min-h-44 flex-col items-center justify-center rounded-md border-2 border-dashed border-border bg-muted/20">
          <ImagePlus class="mb-3 h-9 w-9 text-muted-foreground" />
          <label class="cursor-pointer">
            <Button as-child variant="outline"><span><Upload class="mr-2 h-4 w-4" />选择图片</span></Button>
            <input class="hidden" type="file" accept=".png,.jpg,.jpeg,.bmp,image/png,image/jpeg,image/bmp" @change="handleFileSelect" />
          </label>
          <p class="mt-3 text-xs text-muted-foreground">PNG / JPG / JPEG / BMP，最大 10 MB</p>
        </div>

        <div v-else-if="sourceTab === 'history' && !selectedHistory" class="flex min-h-44 flex-col items-center justify-center rounded-md border-2 border-dashed border-border bg-muted/20">
          <History class="mb-3 h-9 w-9 text-muted-foreground" />
          <Button variant="outline" @click="openHistoryPicker"><Search class="mr-2 h-4 w-4" />选择历史图片</Button>
        </div>

        <div v-if="hasSource" class="grid gap-5 rounded-md border border-border p-4 md:grid-cols-[minmax(0,1fr)_280px]">
          <div class="flex min-h-72 items-center justify-center overflow-hidden rounded bg-muted/30">
            <img :src="imageUrl(selectedSourcePath)" class="max-h-[520px] w-full cursor-zoom-in object-contain" alt="待超分图片" @click="openLightbox(imageUrl(selectedSourcePath), '待超分图片')" />
          </div>
          <div class="space-y-5">
            <div>
              <p class="text-sm font-medium">输入图片</p>
              <p class="mt-1 text-sm text-muted-foreground">{{ sourceWidth }}x{{ sourceHeight }}</p>
              <p class="text-xs text-muted-foreground">{{ sourceTab === 'upload' ? uploadFile?.name : '生成历史 #' + selectedHistory?.id }}</p>
            </div>

            <div class="space-y-2">
              <p class="text-sm font-medium">放大倍率</p>
              <div class="grid grid-cols-3 gap-2">
                <Button v-for="factor in [2, 3, 4]" :key="factor" :variant="selectedFactor === factor ? 'default' : 'outline'" @click="selectedFactor = factor">x{{ factor }}</Button>
              </div>
            </div>

            <div class="rounded bg-muted/40 p-3 text-sm">
              <p class="text-muted-foreground">预计输出尺寸</p>
              <p class="mt-1 font-medium">{{ expectedWidth }}x{{ expectedHeight }}</p>
            </div>

            <p v-if="validationError" class="text-sm text-destructive">{{ validationError }}</p>
            <div class="flex gap-2">
              <Button class="flex-1" :disabled="!canSubmit" @click="openSubmitConfirm"><Expand class="mr-2 h-4 w-4" />加入超分队列</Button>
              <Button variant="ghost" size="icon" title="清除选择" @click="clearSource"><XCircle class="h-4 w-4" /></Button>
            </div>
          </div>
        </div>
      </div>

      <aside class="space-y-3 border-l-0 border-border lg:border-l lg:pl-6">
        <div class="flex items-center justify-between">
          <h2 class="text-sm font-semibold">处理中</h2>
          <Badge variant="secondary">{{ activeJobs.length }}/3</Badge>
        </div>
        <div v-if="loadingJobs" class="flex h-24 items-center justify-center"><Loader2 class="h-5 w-5 animate-spin" /></div>
        <p v-else-if="!activeJobs.length" class="rounded-md border border-dashed border-border p-6 text-center text-sm text-muted-foreground">暂无处理中任务</p>
        <div v-for="job in activeJobs" :key="job.id" class="grid grid-cols-[72px_1fr] gap-3 rounded-md border border-border p-2">
          <img :src="imageUrl(job.sourcePath)" class="h-[72px] w-[72px] cursor-zoom-in rounded object-cover" alt="任务源图" @click="openLightbox(imageUrl(job.sourcePath), '任务源图')" />
          <div class="min-w-0 py-1">
            <div class="flex items-center gap-2"><Loader2 class="h-4 w-4 animate-spin text-primary" /><span class="text-sm font-medium">正在超分，请等待...</span></div>
            <p class="mt-2 text-xs text-muted-foreground">x{{ job.upscaleFactor }} · {{ job.sourceWidth }}x{{ job.sourceHeight }}</p>
            <p class="mt-1 text-xs text-muted-foreground">{{ job.origin === 'IMAGE_GENERATION' ? '生成页快捷提交' : '超分页提交' }}</p>
          </div>
        </div>
      </aside>
    </section>

    <section class="space-y-3 border-t border-border pt-5">
      <div class="flex items-center justify-between">
        <h2 class="text-sm font-semibold">最近完成</h2>
        <Button variant="ghost" size="sm" @click="loadJobs"><RefreshCw class="mr-2 h-4 w-4" />刷新</Button>
      </div>
      <div v-if="recentJobs.length" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <div v-for="job in recentJobs" :key="job.id" class="overflow-hidden rounded-md border border-border bg-background">
          <img :src="imageUrl(job.status === 'SUCCEEDED' ? job.resultPath : job.sourcePath)" class="aspect-square w-full cursor-zoom-in object-contain bg-muted/30" alt="超分任务图片" @click="openLightbox(imageUrl(job.status === 'SUCCEEDED' ? job.resultPath : job.sourcePath), '超分任务图片')" />
          <div class="space-y-2 border-t border-border p-3">
            <div class="flex items-center justify-between gap-2">
              <span class="flex items-center gap-1.5 text-sm font-medium">
                <Check v-if="job.status === 'SUCCEEDED'" class="h-4 w-4 text-emerald-600" />
                <XCircle v-else class="h-4 w-4 text-destructive" />
                {{ job.status === 'SUCCEEDED' ? '超分完成' : '超分失败' }}
              </span>
              <Badge variant="outline">x{{ job.upscaleFactor }}</Badge>
            </div>
            <p class="text-xs text-muted-foreground">{{ job.width && job.height ? job.width + 'x' + job.height : job.sourceWidth + 'x' + job.sourceHeight }}</p>
            <p class="text-xs text-muted-foreground">{{ formatTime(job.createdAt) }}</p>
            <p v-if="job.errorMessage" class="line-clamp-2 text-xs text-destructive" :title="job.errorMessage">{{ job.errorMessage }}</p>
            <Button v-if="job.status === 'SUCCEEDED' && job.resultPath" variant="outline" size="sm" class="w-full" @click="download(job.resultPath)"><Download class="mr-2 h-4 w-4" />下载</Button>
            <Button v-else variant="outline" size="sm" class="w-full" :disabled="!job.sourceAvailable" @click="retryJob(job)"><RefreshCw class="mr-2 h-4 w-4" />{{ job.sourceAvailable ? '重试' : '源图片已不可用' }}</Button>
          </div>
        </div>
      </div>
      <p v-else class="rounded-md border border-dashed border-border p-8 text-center text-sm text-muted-foreground">暂无已完成任务</p>
    </section>

    <Dialog :open="confirmOpen" @update:open="confirmOpen = $event">
      <DialogContent class="sm:max-w-[440px]">
        <DialogHeader>
          <DialogTitle>确认加入超分队列</DialogTitle>
          <DialogDescription>任务提交后无法取消，请确认输出尺寸。</DialogDescription>
        </DialogHeader>
        <div class="grid grid-cols-[1fr_auto_1fr] items-center gap-3 rounded-md bg-muted/40 p-4 text-center">
          <div><p class="text-xs text-muted-foreground">原始尺寸</p><p class="mt-1 font-medium">{{ sourceWidth }}x{{ sourceHeight }}</p></div>
          <Expand class="h-5 w-5 text-muted-foreground" />
          <div><p class="text-xs text-muted-foreground">预计尺寸</p><p class="mt-1 font-medium">{{ expectedWidth }}x{{ expectedHeight }}</p></div>
        </div>
        <DialogFooter>
          <Button variant="outline" :disabled="submitting" @click="confirmOpen = false">取消</Button>
          <Button :disabled="submitting" @click="submitJob"><Loader2 v-if="submitting" class="mr-2 h-4 w-4 animate-spin" />确认提交</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog :open="historyOpen" @update:open="historyOpen = $event">
      <DialogContent class="max-h-[88vh] overflow-y-auto sm:max-w-5xl">
        <DialogHeader><DialogTitle>选择生成历史图片</DialogTitle><DialogDescription>仅显示尺寸符合要求的文生图和图生图记录。</DialogDescription></DialogHeader>
        <div class="grid gap-2 sm:grid-cols-[1fr_160px_160px_auto]">
          <Input v-model="historyPrompt" placeholder="搜索提示词" @keyup.enter="historyPage = 0; loadHistory()" />
          <Input v-model="historyStartDate" type="date" />
          <Input v-model="historyEndDate" type="date" />
          <Button variant="outline" @click="historyPage = 0; loadHistory()"><Search class="mr-2 h-4 w-4" />筛选</Button>
        </div>
        <div v-if="historyLoading" class="flex h-64 items-center justify-center"><Loader2 class="h-6 w-6 animate-spin" /></div>
        <div v-else class="grid gap-3 sm:grid-cols-3 lg:grid-cols-4">
          <button
            v-for="record in historyRecords"
            :key="record.id"
            type="button"
            :class="['overflow-hidden rounded-md border text-left transition-colors', pendingHistory?.id === record.id ? 'border-primary ring-2 ring-primary/20' : 'border-border hover:border-primary/50']"
            @click="selectHistoryRecord(record)"
          >
            <img :src="imageUrl(firstImagePath(record))" class="aspect-square w-full cursor-zoom-in object-cover bg-muted/30" alt="历史图片" @dblclick.stop="openLightbox(imageUrl(firstImagePath(record)))" />
            <div class="space-y-1 p-2">
              <p class="truncate text-xs font-medium">{{ record.prompt || '无提示词' }}</p>
              <p class="text-xs text-muted-foreground">{{ record.width }}x{{ record.height }} · {{ record.mode === 'GENERATE' ? '文生图' : '图生图' }}</p>
              <p class="text-xs text-muted-foreground">{{ formatTime(record.createdAt) }}</p>
            </div>
          </button>
        </div>
        <div class="flex items-center justify-between">
          <Button variant="outline" size="sm" :disabled="historyPage <= 0" @click="changeHistoryPage(-1)">上一页</Button>
          <span class="text-xs text-muted-foreground">第 {{ historyPage + 1 }} / {{ historyTotalPages }} 页</span>
          <Button variant="outline" size="sm" :disabled="historyPage + 1 >= historyTotalPages" @click="changeHistoryPage(1)">下一页</Button>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="historyOpen = false">取消</Button>
          <Button :disabled="!pendingHistory" @click="confirmHistorySelection">确认选择</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
  <ImageLightbox v-model:open="lightboxOpen" :src="lightboxUrl" :alt="lightboxAlt" />
</template>