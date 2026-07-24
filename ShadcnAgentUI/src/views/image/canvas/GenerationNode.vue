<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { Brush, Check, ImagePlus, Images, Loader2, Search, ShieldPlus, Trash2, WandSparkles } from 'lucide-vue-next'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import ImageLightbox from '@/components/ImageLightbox.vue'
import { listAssets, listSpaces } from '@/api/assets'
import type { AssetSpace, PageResponse, PublicAsset } from '@/api/assets'
import { uploadImageCanvasAsset } from '@/api/image-workflow'
import type { ImageWorkflowNodeData } from '@/types/image-workflow'
import type { AiModel } from '@/types/api'
import { defaultImageSize, imageSizeOptions } from '@/utils/imageSizePolicy'
import { toast } from 'vue-sonner'

const props = defineProps<{
  data: ImageWorkflowNodeData
  textModels: AiModel[]
  imageModels: AiModel[]
  modelLoading: boolean
  busy: boolean
  deletable?: boolean
  sessionId: number
}>()

const emit = defineEmits<{
  submit: [payload: {
    prompt: string
    modelId: number
    images: File[]
    remoteImages: Array<{ url: string; name: string; recordId?: number }>
    maskImage?: { url: string; name: string; assetId?: number }
    size: string
    quality: string
    imageCount: number
  }]
  'draft-change': [patch: Partial<ImageWorkflowNodeData>]
  'edit-mask': []
  delete: []
}>()

const prompt = ref(props.data.prompt ?? '')
const selectedModelId = ref<number | undefined>(props.data.modelId)
const images = ref<File[]>([])
const previews = ref<string[]>([])
const size = ref(props.data.size ?? '1024x1024')
const quality = ref(props.data.quality ?? 'high')
const imageCount = ref(props.data.imageCount ?? 1)
const assetPickerOpen = ref(false)
const assetLoading = ref(false)
const assetSpaces = ref<AssetSpace[]>([])
const assets = ref<PublicAsset[]>([])
const assetSpaceId = ref<number | undefined>()
const assetKeyword = ref('')
const selectedAsset = ref<PublicAsset | null>(null)
const previewOpen = ref(false)
const previewUrl = ref('')
const previewAlt = ref('')
const remoteReferences = ref([...(props.data.referenceImages ?? [])])
const maskImage = ref(props.data.maskImage ? { ...props.data.maskImage } : undefined)
const referenceCount = computed(() => images.value.length + remoteReferences.value.length)
const availableModels = computed(() => referenceCount.value ? props.imageModels : props.textModels)
const selectedModel = computed(() => availableModels.value.find(model => model.id === selectedModelId.value))
const sizeOptions = computed(() => imageSizeOptions(selectedModel.value?.modelName))
const canSubmit = computed(() => prompt.value.trim() && selectedModelId.value && !props.busy)

watch(() => props.data, data => {
  const nextPrompt = data.prompt ?? ''
  const nextSize = data.size ?? '1024x1024'
  const nextQuality = data.quality ?? 'high'
  const nextImageCount = data.imageCount ?? 1
  const nextReferences = data.referenceImages ?? []
  if (prompt.value !== nextPrompt) prompt.value = nextPrompt
  if (selectedModelId.value !== data.modelId) selectedModelId.value = data.modelId
  if (size.value !== nextSize) size.value = nextSize
  if (quality.value !== nextQuality) quality.value = nextQuality
  if (imageCount.value !== nextImageCount) imageCount.value = nextImageCount
  if (JSON.stringify(remoteReferences.value) !== JSON.stringify(nextReferences)) {
    remoteReferences.value = nextReferences.map(reference => ({ ...reference }))
  }
  if (JSON.stringify(maskImage.value) !== JSON.stringify(data.maskImage)) {
    maskImage.value = data.maskImage ? { ...data.maskImage } : undefined
  }
}, { deep: true, immediate: true })

watch([prompt, selectedModelId, size, quality, imageCount, remoteReferences, maskImage], () => {
  emit('draft-change', {
    prompt: prompt.value,
    modelId: selectedModelId.value,
    size: size.value,
    quality: quality.value,
    imageCount: imageCount.value,
    referenceImages: remoteReferences.value.map(reference => ({ ...reference })),
    maskImage: maskImage.value ? { ...maskImage.value } : undefined,
  })
}, { deep: true })

watch(availableModels, models => {
  if (!models.some(model => model.id === selectedModelId.value)) {
    selectedModelId.value = models[0]?.id
  }
}, { immediate: true })

watch(selectedModelId, () => {
  if (!sizeOptions.value.some(option => option.value === size.value)) {
    size.value = defaultImageSize(selectedModel.value?.modelName)
  }
})

async function selectReferences(event: Event) {
  const input = event.target as HTMLInputElement
  const selected = Array.from(input.files ?? []).slice(0, Math.max(0, 4 - referenceCount.value))
  for (const file of selected) {
    images.value.push(file)
    previews.value.push(URL.createObjectURL(file))
    try {
      const asset = await uploadImageCanvasAsset(props.sessionId, file)
      remoteReferences.value.push({
        url: asset.url,
        name: file.name,
        assetId: asset.id,
      })
    } catch {
      toast.error(`${file.name} 上传失败`)
    } finally {
      const index = images.value.indexOf(file)
      if (index >= 0) removeReference(index)
    }
  }
  input.value = ''
}

function removeReference(index: number) {
  URL.revokeObjectURL(previews.value[index])
  images.value.splice(index, 1)
  previews.value.splice(index, 1)
}

function removeRemoteReference(index: number) {
  remoteReferences.value.splice(index, 1)
}

function assetUrl(path: string): string {
  if (!path) return ''
  if (/^(blob:|https?:\/\/)/.test(path)) return path
  const normalized = path.replace(/\\/g, '/').replace(/^\.\//, '')
  return normalized.startsWith('/uploads/') ? normalized : `/uploads/${normalized}`
}

function openPreview(url: string, alt: string) {
  previewUrl.value = url
  previewAlt.value = alt
  previewOpen.value = true
}

async function openAssetPicker() {
  assetPickerOpen.value = true
  selectedAsset.value = null
  try {
    assetSpaces.value = await listSpaces()
  } catch {
    assetSpaces.value = []
  }
  await loadAssets()
}

async function loadAssets() {
  assetLoading.value = true
  try {
    const result: PageResponse<PublicAsset> = await listAssets({
      spaceId: assetSpaceId.value,
      keyword: assetKeyword.value.trim() || undefined,
      page: 0,
      size: 50,
    })
    assets.value = result.content ?? []
  } finally {
    assetLoading.value = false
  }
}

async function addSelectedAsset() {
  if (!selectedAsset.value || referenceCount.value >= 4) return
  remoteReferences.value.push({
    url: assetUrl(selectedAsset.value.filePath),
    name: selectedAsset.value.fileName,
    assetId: selectedAsset.value.id,
  })
  selectedAsset.value = null
  assetPickerOpen.value = false
  toast.success('已添加素材库图片')
}

async function selectMask(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (file.type !== 'image/png' && !file.name.toLowerCase().endsWith('.png')) {
    toast.error('Mask 必须是带透明通道的 PNG 图片')
    return
  }
  if (!referenceCount.value) {
    toast.error('请先添加参考图，Mask 会应用到第一张参考图')
    return
  }
  try {
    const asset = await uploadImageCanvasAsset(props.sessionId, file)
    maskImage.value = { url: asset.url, name: file.name, assetId: asset.id }
    toast.success('Mask 已添加')
  } catch {
    toast.error('Mask 上传失败')
  }
}

function submit() {
  if (!canSubmit.value || !selectedModelId.value) return
  emit('submit', {
    prompt: prompt.value.trim(),
    modelId: selectedModelId.value,
    images: [...images.value],
    remoteImages: remoteReferences.value,
    maskImage: maskImage.value,
    size: size.value,
    quality: quality.value,
    imageCount: imageCount.value,
  })
}

onBeforeUnmount(() => previews.value.forEach(url => URL.revokeObjectURL(url)))
</script>

<template>
  <article class="w-[560px] overflow-hidden rounded-xl border bg-card shadow-sm">
    <header class="flex items-center justify-between border-b px-4 py-3">
      <div class="flex items-center gap-2">
        <span class="grid size-8 place-items-center rounded-lg bg-muted text-muted-foreground">
          <WandSparkles class="size-4" />
        </span>
        <strong class="text-sm font-medium">{{ data.title }}</strong>
      </div>
      <div class="flex items-center gap-1">
        <Badge :variant="data.status === 'failed' ? 'destructive' : 'outline'">{{ data.statusText ?? (data.status === 'draft' ? '待创作' : data.status) }}</Badge>
        <Button
          v-if="deletable"
          type="button"
          variant="ghost"
          size="icon"
          class="nodrag h-8 w-8 text-muted-foreground hover:text-destructive"
          aria-label="删除创作卡片"
          @click.stop="emit('delete')"
        >
          <Trash2 class="h-4 w-4" />
        </Button>
      </div>
    </header>

    <div class="nodrag nowheel space-y-4 p-4">
      <Textarea v-model="prompt" :disabled="busy" class="min-h-24 resize-none bg-background text-sm" placeholder="描述希望生成或修改的图片…" />

      <div v-if="previews.length || remoteReferences.length" class="grid grid-cols-4 gap-2">
        <div v-for="(reference, index) in remoteReferences" :key="reference.url" class="group relative aspect-square overflow-hidden rounded-lg bg-muted">
          <img
            :src="reference.url"
            :alt="reference.name"
            class="h-full w-full cursor-zoom-in object-cover"
            @click.stop="openPreview(reference.url, reference.name)"
          />
          <button type="button" class="absolute right-1 top-1 hidden rounded bg-black/60 p-1 text-white group-hover:block" @click.stop="removeRemoteReference(index)">
            <Trash2 class="h-3 w-3" />
          </button>
        </div>
        <div v-for="(preview, index) in previews" :key="preview" class="group relative aspect-square overflow-hidden rounded-lg bg-muted">
          <img
            :src="preview"
            :alt="images[index]?.name ?? '参考图'"
            class="h-full w-full cursor-zoom-in object-cover"
            @click.stop="openPreview(preview, images[index]?.name ?? '参考图')"
          />
          <button type="button" class="absolute right-1 top-1 hidden rounded bg-black/60 p-1 text-white group-hover:block" @click="removeReference(index)">
            <Trash2 class="h-3 w-3" />
          </button>
        </div>
      </div>

      <label v-if="referenceCount < 4" class="flex cursor-pointer items-center justify-center gap-2 rounded-lg border border-dashed py-2 text-xs text-muted-foreground hover:border-primary/50 hover:text-foreground">
        <ImagePlus class="h-4 w-4" />
        添加参考图（{{ referenceCount }}/4）
        <input type="file" accept="image/*" multiple class="hidden" :disabled="busy" @change="selectReferences" />
      </label>
      <Button
        v-if="referenceCount < 4"
        type="button"
        variant="outline"
        size="sm"
        class="w-full"
        :disabled="busy"
        @click="openAssetPicker"
      >
        <Images class="mr-2 h-4 w-4" />
        从素材库选择
      </Button>

      <div class="grid grid-cols-2 gap-2">
        <Button
          type="button"
          variant="outline"
          size="sm"
          :disabled="busy || !referenceCount"
          title="在第一张参考图上绘制标注或 Mask"
          @click="emit('edit-mask')"
        >
          <Brush class="mr-2 h-4 w-4" />
          局部编辑
        </Button>
        <label
          class="flex h-9 items-center justify-center rounded-md border bg-background px-3 text-xs font-medium"
          :class="busy || !referenceCount ? 'cursor-not-allowed opacity-50' : 'cursor-pointer hover:bg-accent'"
          title="上传与第一张参考图尺寸一致、带透明通道的 PNG"
        >
          <ShieldPlus class="mr-2 h-4 w-4" />
          添加 Mask 遮罩图
          <input type="file" accept="image/png,.png" class="hidden" :disabled="busy || !referenceCount" @change="selectMask" />
        </label>
      </div>

      <div v-if="maskImage" class="flex items-center gap-3 rounded-lg border bg-muted/30 p-2">
        <div class="relative size-14 overflow-hidden rounded-md border bg-[linear-gradient(45deg,#e5e7eb_25%,transparent_25%),linear-gradient(-45deg,#e5e7eb_25%,transparent_25%),linear-gradient(45deg,transparent_75%,#e5e7eb_75%),linear-gradient(-45deg,transparent_75%,#e5e7eb_75%)] bg-[length:12px_12px]">
          <img :src="maskImage.url" :alt="maskImage.name" class="h-full w-full object-contain" />
        </div>
        <div class="min-w-0 flex-1">
          <p class="truncate text-xs font-medium">已添加 Mask</p>
          <p class="truncate text-[11px] text-muted-foreground">{{ maskImage.name }}</p>
        </div>
        <Button type="button" variant="ghost" size="icon" class="h-8 w-8" aria-label="移除 Mask" @click="maskImage = undefined">
          <Trash2 class="h-4 w-4" />
        </Button>
      </div>

      <div class="grid grid-cols-[minmax(0,1.5fr)_minmax(150px,1.4fr)_minmax(82px,.7fr)_minmax(72px,.56fr)] gap-2">
        <select v-model.number="selectedModelId" :disabled="modelLoading || busy" class="h-9 min-w-0 rounded-md border bg-background px-2 text-xs outline-none focus-visible:ring-2 focus-visible:ring-ring">
          <option v-if="modelLoading" :value="undefined">正在加载模型…</option>
          <option v-for="model in availableModels" :key="model.id" :value="model.id">{{ model.name }}</option>
          <option v-if="!modelLoading && !availableModels.length" :value="undefined">没有可用模型</option>
        </select>
        <select v-model="size" :disabled="busy" class="h-9 rounded-md border bg-background px-2 text-xs outline-none focus-visible:ring-2 focus-visible:ring-ring">
          <option v-for="option in sizeOptions" :key="option.value" :value="option.value">
            {{ option.ratioLabel }} · {{ option.label }}
          </option>
        </select>
        <select v-model="quality" :disabled="busy" class="h-9 rounded-md border bg-background px-2 text-xs outline-none focus-visible:ring-2 focus-visible:ring-ring">
          <option value="high">高质量</option>
          <option value="medium">标准</option>
          <option value="low">快速</option>
          <option value="auto">自动</option>
        </select>
        <select v-model.number="imageCount" :disabled="busy" class="h-9 rounded-md border bg-background px-2 text-xs outline-none focus-visible:ring-2 focus-visible:ring-ring">
          <option :value="1">1 张</option>
          <option :value="2">2 张</option>
          <option :value="4">4 张</option>
        </select>
      </div>

      <Button class="w-full" :disabled="!canSubmit" @click="submit">
        <Loader2 v-if="busy" class="mr-2 h-4 w-4 animate-spin" />
        <WandSparkles v-else class="mr-2 h-4 w-4" />
        {{ busy ? '正在生成' : referenceCount ? '基于参考图生成' : '开始生成' }}
      </Button>
    </div>

    <Dialog v-model:open="assetPickerOpen">
      <DialogContent class="nodrag nowheel max-h-[85vh] overflow-y-auto sm:max-w-[960px]">
        <DialogHeader>
          <DialogTitle>从素材库选择参考图</DialogTitle>
        </DialogHeader>
        <div class="flex items-center gap-2">
          <Select v-model="assetSpaceId" @update:model-value="loadAssets">
            <SelectTrigger class="w-44">
              <SelectValue placeholder="全部空间" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem v-for="space in assetSpaces" :key="space.id" :value="space.id">
                {{ space.name }}
              </SelectItem>
            </SelectContent>
          </Select>
          <Input v-model="assetKeyword" class="flex-1" placeholder="搜索素材名称" @keyup.enter="loadAssets" />
          <Button type="button" variant="outline" @click="loadAssets">
            <Search class="mr-2 h-4 w-4" />
            搜索
          </Button>
        </div>

        <div v-if="assetLoading" class="grid min-h-56 place-items-center">
          <Loader2 class="h-7 w-7 animate-spin text-muted-foreground" />
        </div>
        <div v-else-if="!assets.length" class="grid min-h-56 place-items-center text-sm text-muted-foreground">
          暂无可用素材
        </div>
        <div v-else class="grid grid-cols-4 gap-3 sm:grid-cols-6">
          <button
            v-for="asset in assets"
            :key="asset.id"
            type="button"
            class="group relative aspect-square overflow-hidden rounded-lg border-2 bg-muted transition-colors"
            :class="selectedAsset?.id === asset.id ? 'border-primary' : 'border-transparent hover:border-primary/40'"
            @click="selectedAsset = asset"
          >
            <img :src="assetUrl(asset.filePath)" :alt="asset.fileName" class="h-full w-full object-cover" loading="lazy" />
            <span
              v-if="selectedAsset?.id === asset.id"
              class="absolute right-1 top-1 grid h-5 w-5 place-items-center rounded-full bg-primary text-primary-foreground"
            >
              <Check class="h-3.5 w-3.5" />
            </span>
          </button>
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" @click="assetPickerOpen = false">取消</Button>
          <Button type="button" :disabled="!selectedAsset" @click="addSelectedAsset">添加为参考图</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <ImageLightbox v-model:open="previewOpen" :src="previewUrl" :alt="previewAlt" />

    <Handle
      v-if="data.parentNodeId"
      type="target"
      :position="Position.Left"
      class="!h-3 !w-3 !border-2 !border-background !bg-primary"
    />
    <Handle type="source" :position="Position.Right" class="!h-3 !w-3 !border-2 !border-background !bg-primary" />
  </article>
</template>
