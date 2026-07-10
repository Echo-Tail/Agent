<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { toast } from 'sonner'
import PageHeader from '@/components/PageHeader.vue'
import AspectRatioIcon from '@/components/AspectRatioIcon.vue'
import ImageLightbox from '@/components/ImageLightbox.vue'
import { useImageLightbox } from '@/composables/useImageLightbox'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import {
  generateImage, editImage, type ImageGenerationResult,
  collectAsinImages, analyzeExpressionCached,
} from '@/api/image'
import { listSpaces, listAssets } from '@/api/assets'
import type { AssetSpace, PublicAsset } from '@/api/assets'
import {
  Plus, Loader2, Trash2, WandSparkles, Search, ImagePlus, X,
  Download, Copy, Check, ZoomIn,
} from 'lucide-vue-next'

// --- Image sources ---
interface ImageItem {
  url: string
  file?: File  // for local uploads
}
const images = ref<ImageItem[]>([])
const selectedImageUrl = ref('')
const expressionPrompt = ref('')
const generating = ref(false)
const analyzing = ref(false)
const collectingAsin = ref(false)
const showAddDialog = ref(false)

// --- Add dialog ---
const addMethod = ref<'url' | 'file' | 'asin'>('url')
const addUrlInput = ref('')
const addAsinInput = ref('')
const addedFileNames = ref<string[]>([])

// Reset file names list when dialog closes
watch(showAddDialog, (v) => { if (!v) addedFileNames.value = [] })

// --- Generate params ---
const genPrompt = ref('')
const genSize = ref('1254x1254')
const genQuality = ref('auto')
const genCount = ref(1)
const genModelId = ref<number | undefined>()
const sizeOptions = [
  { value: '1024x1024', label: '1024x1024', ratio: '1 / 1', ratioLabel: '1:1' },
  { value: '1254x1254', label: '1254x1254', ratio: '1 / 1', ratioLabel: '1:1' },
  { value: '1672x941', label: '1672x941', ratio: '16 / 9', ratioLabel: '16:9' },
  { value: '1536x1024', label: '1536x1024', ratio: '3 / 2', ratioLabel: '3:2' },
  { value: '1024x1536', label: '1024x1536', ratio: '2 / 3', ratioLabel: '2:3' },
  { value: '1448x1086', label: '1448x1086', ratio: '4 / 3', ratioLabel: '4:3' },
  { value: '1659x948', label: '1659x948', ratio: '7 / 4', ratioLabel: '7:4' },
]
const selectedSizeOption = computed(() => sizeOptions.find(opt => opt.value === genSize.value) ?? sizeOptions[0])
const genReferenceFiles = ref<File[]>([])
const genResults = ref<string[]>([])
const genRevisedPrompt = ref('')

// Lightbox
const { lightboxOpen, lightboxUrl, lightboxAlt, openLightbox: showLightbox } = useImageLightbox()
const copiedResultIdx = ref<number | null>(null)

function imageUrl(path: string): string {
  if (!path) return ''
  if (/^blob:/i.test(path) || /^https?:\/\//i.test(path)) return path
  let normalized = path.replace(/\\/g, '/').replace(/^\.\//, '')
  // Backend now returns paths starting with /uploads/ — ensure this
  if (!normalized.startsWith('/')) normalized = '/' + normalized
  return normalized
}

// --- Add sources ---
function addUrlSource() {
  const u = addUrlInput.value.trim()
  if (u && !images.value.some(i => i.url === u)) {
    images.value.push({ url: u })
  }
  addUrlInput.value = ''
}

function handleFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files) {
    for (const f of Array.from(input.files)) {
      const url = URL.createObjectURL(f)
      images.value.push({ url, file: f })
      addedFileNames.value.push(f.name)
    }
  }
  input.value = ''
}

async function collectAsin() {
  const asin = addAsinInput.value.trim()
  if (!asin) { toast.error('请输入 ASIN'); return }
  collectingAsin.value = true
  try {
    const urls = await collectAsinImages(asin)
    if (urls.length === 0) { toast.warning('未找到图片'); return }
    for (const url of urls) {
      if (!images.value.some(i => i.url === url)) {
        images.value.push({ url })
      }
    }
    toast.success(`已添加 ${urls.length} 张图片`)
    addAsinInput.value = ''
  } catch (e: any) {
    toast.error(e?.response?.data?.message || '采集失败')
  } finally {
    collectingAsin.value = false
  }
}

function removeImage(index: number) {
  const item = images.value[index]
  if (item.file) URL.revokeObjectURL(item.url)
  images.value.splice(index, 1)
  if (selectedImageUrl.value === item.url) selectedImageUrl.value = ''
}

// --- Analyze expression ---
async function analyzeExpression() {
  if (!selectedImageUrl.value) { toast.error('请先点击选择一张图片'); return }
  analyzing.value = true
  try {
    const result = await analyzeExpressionCached(selectedImageUrl.value)
    expressionPrompt.value = result
    genPrompt.value = result
    toast.success('分析完成')
  } catch (e: any) {
    toast.error(e?.response?.data?.message || '分析失败')
  } finally {
    analyzing.value = false
  }
}

// --- Generate ---
function openLightbox(url: string) {
  showLightbox(imageUrl(url))
}

function downloadResult(url: string) {
  const a = document.createElement('a')
  a.href = imageUrl(url)
  a.download = url.split('/').pop() || 'image.png'
  a.click()
}

async function copyResultPrompt(text: string, idx: number) {
  try {
    await navigator.clipboard.writeText(text)
    copiedResultIdx.value = idx
    setTimeout(() => { copiedResultIdx.value = null }, 1500)
  } catch { toast.error('复制失败') }
}

function deleteResult(idx: number) {
  genResults.value.splice(idx, 1)
}

async function handleGenerate() {
  if (!genPrompt.value.trim()) { toast.error('请填写提示词'); return }
  generating.value = true
  try {
    let result: ImageGenerationResult
    if (genReferenceFiles.value.length > 0) {
      result = await editImage(
        genPrompt.value, genReferenceFiles.value, genSize.value, genQuality.value,
        undefined, genCount.value, undefined,
      )
    } else {
      result = await generateImage(
        genPrompt.value, genSize.value, genQuality.value, genCount.value, undefined,
      )
    }
    genResults.value = result.urls || []
    genRevisedPrompt.value = result.revisedPrompt || ''
    toast.success(`已生成 ${result.urls?.length || 0} 张图片`)
  } catch (e: any) {
    toast.error(e?.response?.data?.message || '生成失败')
  } finally {
    generating.value = false
  }
}

function handleRefFiles(e: Event) {
  const input = e.target as HTMLInputElement
  genReferenceFiles.value = Array.from(input.files || [])
}

// --- Model management integration ---
import { getImageModelsApi } from '@/api/model'
import type { AiModel } from '@/types/api'
const models = ref<AiModel[]>([])
getImageModelsApi().then(m => { models.value = m; if (m.length > 0) genModelId.value = m[0].id }).catch(() => {})

// --- Asset picker ---
const assetPickerOpen = ref(false)
const pickerAssets = ref<PublicAsset[]>([])
const pickerLoading = ref(false)
const pickerSpaceId = ref<number | null>(null)
const pickerKeyword = ref('')
const pickerSpaces = ref<AssetSpace[]>([])
const pickerPreviewAsset = ref<PublicAsset | null>(null)
const pickerPreviewOpen = ref(false)

async function openAssetPicker() {
  assetPickerOpen.value = true
  try {
    const spaces = await listSpaces()
    pickerSpaces.value = spaces
  } catch {}
  await loadPickerAssets()
}

async function loadPickerAssets() {
  pickerLoading.value = true
  try {
    const res = await listAssets({
      spaceId: pickerSpaceId.value ?? undefined,
      keyword: pickerKeyword.value || undefined,
      page: 0, size: 50,
    })
    pickerAssets.value = res.content ?? []
  } catch { /* ignore */ }
  finally { pickerLoading.value = false }
}

function showPickerPreview(asset: PublicAsset) {
  pickerPreviewAsset.value = asset
  pickerPreviewOpen.value = true
}

function selectPickerPreview() {
  if (pickerPreviewAsset.value) pickAsset(pickerPreviewAsset.value)
  pickerPreviewOpen.value = false
  pickerPreviewAsset.value = null
}

async function pickAsset(asset: PublicAsset) {
  try {
    const resp = await fetch(imageUrl(asset.filePath))
    const blob = await resp.blob()
    const file = new File([blob], asset.fileName, { type: blob.type })
    genReferenceFiles.value.push(file)
    toast.success('已添加参考图')
    assetPickerOpen.value = false
  } catch {
    toast.error('加载图片失败')
  }
}
</script>

<template>
  <div class="space-y-5">
    <PageHeader title="Amazon 图像工作台" description="批量采集图片→分析表达→生成商品图" />

    <div class="grid gap-4 xl:grid-cols-[320px_minmax(0,1fr)]">
      <!-- Left panel: Image sources -->
      <Card>
        <CardHeader class="pb-3">
          <div class="flex items-center justify-between">
            <CardTitle class="text-base">素材图片</CardTitle>
            <Button size="sm" @click="showAddDialog = true"><Plus class="h-4 w-4 mr-1" />添加素材</Button>
          </div>
        </CardHeader>
        <CardContent>
          <div v-if="images.length === 0" class="py-12 text-center text-sm text-muted-foreground">
            <ImagePlus class="h-8 w-8 mx-auto mb-2 text-muted-foreground/50" />
            <p>点击「添加素材」添加图片</p>
          </div>
          <div v-else class="grid grid-cols-2 gap-2">
            <div
              v-for="(img, i) in images"
              :key="i"
              :class="['overflow-hidden rounded-md border bg-card transition-colors group',
                selectedImageUrl === img.url ? 'ring-2 ring-primary' : '']"
            >
              <!-- 点击图片选择素材 -->
              <div class="aspect-square cursor-pointer" @click="selectedImageUrl = img.url">
                <img :src="imageUrl(img.url)" class="h-full w-full object-cover" alt="" />
              </div>
              <!-- 底部操作栏 -->
              <div class="flex items-center justify-center gap-1 border-t bg-muted/20 p-1">
                <Button variant="ghost" size="icon" class="h-7 w-7" @click.stop="openLightbox(img.url)"
                  title="放大查看">
                  <ZoomIn class="h-3.5 w-3.5" />
                </Button>
                <Button variant="ghost" size="icon" class="h-7 w-7 text-destructive hover:text-destructive" @click.stop="removeImage(i)"
                  title="删除">
                  <Trash2 class="h-3.5 w-3.5" />
                </Button>
              </div>
            </div>
          </div>
          <div class="mt-3 flex gap-2">
            <Button variant="outline" size="sm" class="flex-1" :disabled="!selectedImageUrl || analyzing"
              @click="analyzeExpression">
              <Loader2 v-if="analyzing" class="h-4 w-4 mr-1 animate-spin" />
              <Search v-else class="h-4 w-4 mr-1" />分析表达
            </Button>
          </div>
        </CardContent>
      </Card>

      <!-- Right panel: Expression + Generate -->
      <div class="space-y-4">
        <Card>
          <CardHeader class="pb-3"><CardTitle class="text-base">提示词</CardTitle></CardHeader>
          <CardContent class="space-y-3">
            <Textarea v-model="genPrompt" class="min-h-[200px]" placeholder="分析图片后自动填充，也可手动输入或修改..." />
            <div v-if="expressionPrompt" class="text-xs text-muted-foreground">
              提示词来源：{{ expressionPrompt === genPrompt ? '图片分析' : '手动编辑' }}
            </div>
            <div v-if="genRevisedPrompt" class="rounded-md bg-blue-50 p-2 text-xs text-blue-700">
              模型改写后的提示词：{{ genRevisedPrompt }}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader class="pb-3"><CardTitle class="text-base">图生图参数</CardTitle></CardHeader>
          <CardContent class="space-y-3">
            <div class="grid gap-3 md:grid-cols-4">
              <div>
                <label class="text-xs text-muted-foreground">尺寸</label>
                <Select v-model="genSize">
                  <SelectTrigger>
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
              <div>
                <label class="text-xs text-muted-foreground">质量</label>
                <select v-model="genQuality" class="w-full rounded-md border border-input bg-background px-3 py-2 text-sm">
                  <option value="low">Low</option>
                  <option value="medium">Medium</option>
                  <option value="high">High</option>
                  <option value="auto">Auto</option>
                </select>
              </div>
              <div>
                <label class="text-xs text-muted-foreground">张数</label>
                <Input v-model.number="genCount" type="number" min="1" max="10" />
              </div>
              <div class="flex items-end">
                <Button :disabled="generating || !genPrompt.trim()" @click="handleGenerate">
                  <Loader2 v-if="generating" class="h-4 w-4 mr-1 animate-spin" />
                  <WandSparkles v-else class="h-4 w-4 mr-1" />生成
                </Button>
              </div>
            </div>
            <div>
              <label class="text-xs font-medium">参考图（可选，上传后走图生图）</label>
              <div class="mt-1 flex gap-2">
                <Input type="file" accept="image/*" multiple @change="handleRefFiles" class="flex-1" />
                <Button variant="outline" size="sm" @click="openAssetPicker">从素材库选择</Button>
              </div>
              <div v-if="genReferenceFiles.length > 0" class="mt-2 flex flex-wrap gap-2">
                <div v-for="(f, i) in genReferenceFiles" :key="i"
                  class="flex items-center gap-1 rounded-md border bg-muted/30 px-2 py-1 text-xs">
                  <span class="max-w-[120px] truncate">{{ f.name }}</span>
                  <button class="text-muted-foreground hover:text-destructive" @click="genReferenceFiles.splice(i, 1)"><X class="h-3 w-3" /></button>
                </div>
              </div>
              <p class="mt-1 text-xs text-muted-foreground">不上传参考图时走文生图</p>
            </div>
          </CardContent>
        </Card>

        <!-- Results -->
        <Card v-if="genResults.length > 0">
          <CardHeader class="pb-3"><CardTitle class="text-base">生成结果（{{ genResults.length }} 张）</CardTitle></CardHeader>
          <CardContent>
            <div class="grid gap-3 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-4">
              <div v-for="(url, i) in genResults" :key="i" class="group relative overflow-hidden rounded-md border bg-card">
                <div class="aspect-square cursor-zoom-in" @click="openLightbox(url)">
                  <img :src="imageUrl(url)" :alt="`gen ${i+1}`" class="h-full w-full object-cover" />
                </div>
                <div class="absolute top-1 right-1 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                  <Button variant="secondary" size="icon" class="h-6 w-6" @click.stop="openLightbox(url)">
                    <ZoomIn class="h-3 w-3" />
                  </Button>
                  <Button variant="secondary" size="icon" class="h-6 w-6" @click.stop="downloadResult(url)">
                    <Download class="h-3 w-3" />
                  </Button>
                  <Button variant="destructive" size="icon" class="h-6 w-6" @click.stop="deleteResult(i)">
                    <Trash2 class="h-3 w-3" />
                  </Button>
                </div>
                <div class="p-1.5 space-y-1">
                  <Button variant="ghost" size="sm" class="h-5 text-[10px] w-full text-muted-foreground hover:text-foreground"
                    @click.stop="copyResultPrompt(genPrompt, i)">
                    <Check v-if="copiedResultIdx === i" class="h-3 w-3 mr-1 text-green-500" />
                    <Copy v-else class="h-3 w-3 mr-1" />{{ copiedResultIdx === i ? '已复制' : '复制提示词' }}
                  </Button>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>

    <!-- Asset picker dialog -->
    <Teleport to="body">
      <div v-if="assetPickerOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50" @click.self="assetPickerOpen = false">
        <div class="w-full max-w-[900px] max-h-[85vh] overflow-y-auto rounded-lg border bg-background p-6 shadow-lg">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-lg font-semibold">选择素材图片</h3>
            <Button variant="ghost" size="icon" @click="assetPickerOpen = false"><X class="h-4 w-4" /></Button>
          </div>
          <div class="flex items-center gap-3 flex-wrap mb-4">
            <select v-model="pickerSpaceId" @change="loadPickerAssets"
              class="rounded-md border border-input bg-background px-3 py-2 text-sm min-w-[160px]">
              <option :value="null" disabled hidden>选择空间</option>
              <option v-for="sp in pickerSpaces" :key="sp.id" :value="sp.id">{{ sp.name }}</option>
            </select>
            <Input v-model="pickerKeyword" placeholder="搜索..." class="h-8 text-sm flex-1 min-w-[200px]" @keyup.enter="loadPickerAssets" />
            <Button variant="outline" size="sm" @click="loadPickerAssets">搜索</Button>
          </div>
          <div v-if="pickerLoading" class="flex justify-center py-16">
            <Loader2 class="h-8 w-8 animate-spin text-muted-foreground" />
          </div>
          <div v-else-if="pickerAssets.length === 0" class="py-16 text-center text-sm text-muted-foreground">
            <ImagePlus class="h-12 w-12 mx-auto mb-2 opacity-40" />
            <p>暂无素材</p>
          </div>
          <div v-else class="grid grid-cols-6 gap-3">
            <div v-for="asset in pickerAssets" :key="asset.id"
              class="relative aspect-square rounded-md overflow-hidden bg-muted/30 cursor-pointer border border-border hover:border-primary/50 transition-colors group"
              @click="showPickerPreview(asset)">
              <img :src="imageUrl(asset.filePath)" class="w-full h-full object-cover" alt="" loading="lazy" />
              <div class="absolute inset-0 bg-black/0 group-hover:bg-black/20 transition-colors flex items-center justify-center">
                <span class="text-white text-xs opacity-0 group-hover:opacity-100 transition-opacity bg-black/50 px-2 py-1 rounded">选择</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Asset preview dialog -->
    <Teleport to="body">
      <div v-if="pickerPreviewOpen && pickerPreviewAsset" class="fixed inset-0 z-[110] flex items-center justify-center bg-black/50" @click.self="pickerPreviewOpen = false">
        <div class="relative max-w-[50vw] max-h-[85vh] rounded-lg overflow-hidden bg-background/95 p-4 shadow-lg">
          <button class="absolute top-2 right-2 z-10 rounded-full bg-black/50 p-1.5 text-white hover:bg-black/70" @click="pickerPreviewOpen = false">
            <X class="h-4 w-4" />
          </button>
          <img :src="imageUrl(pickerPreviewAsset.filePath)" class="max-h-[65vh] w-auto object-contain rounded" alt="" />
          <div class="mt-3 flex justify-center">
            <Button size="sm" @click="selectPickerPreview">选择此图片</Button>
          </div>
        </div>
      </div>
    </Teleport>

    <ImageLightbox v-model:open="lightboxOpen" :src="lightboxUrl" :alt="lightboxAlt" />

    <!-- Add dialog -->
    <Teleport to="body">
      <div v-if="showAddDialog" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50" @click.self="showAddDialog = false">
        <div class="w-full max-w-lg rounded-lg border bg-background p-6 shadow-lg">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-lg font-semibold">添加素材图片</h3>
            <Button variant="ghost" size="icon" @click="showAddDialog = false"><X class="h-4 w-4" /></Button>
          </div>

          <div class="flex gap-1 rounded-md border p-1 bg-muted/30 mb-4">
            <button :class="['flex-1 px-3 py-1.5 text-sm rounded-md transition-colors',
              addMethod==='url'?'bg-background shadow-sm font-medium':'hover:bg-background/50']"
              @click="addMethod='url'">图片 URL</button>
            <button :class="['flex-1 px-3 py-1.5 text-sm rounded-md transition-colors',
              addMethod==='file'?'bg-background shadow-sm font-medium':'hover:bg-background/50']"
              @click="addMethod='file'">本地上传</button>
            <button :class="['flex-1 px-3 py-1.5 text-sm rounded-md transition-colors',
              addMethod==='asin'?'bg-background shadow-sm font-medium':'hover:bg-background/50']"
              @click="addMethod='asin'">ASIN 采集</button>
          </div>

          <div v-if="addMethod==='url'" class="space-y-3">
            <div class="flex gap-2">
              <Input v-model="addUrlInput" placeholder="https://example.com/image.jpg" @keyup.enter="addUrlSource" />
              <Button variant="outline" size="sm" @click="addUrlSource">添加</Button>
            </div>
          </div>

          <div v-if="addMethod==='file'" class="space-y-3">
            <div v-if="addedFileNames.length > 0" class="flex flex-wrap gap-2">
              <div v-for="(name, i) in addedFileNames" :key="i"
                class="flex items-center gap-1 rounded-md border bg-muted/30 px-2 py-1 text-xs">
                <span class="max-w-[180px] truncate">{{ name }}</span>
              </div>
            </div>
            <div v-else class="rounded-md border border-dashed p-4 text-center text-xs text-muted-foreground">
              点击下方按钮选择图片文件
            </div>
            <Input type="file" accept="image/*" multiple @change="handleFileSelect" />
          </div>

          <div v-if="addMethod==='asin'" class="space-y-3">
            <div class="flex gap-2">
              <Input v-model="addAsinInput" placeholder="B0XXXXXXXXXX" />
              <Button :disabled="collectingAsin || !addAsinInput.trim()" @click="collectAsin">
                <Loader2 v-if="collectingAsin" class="h-4 w-4 mr-1 animate-spin" />
                <Search v-else class="h-4 w-4 mr-1" />采集
              </Button>
            </div>
          </div>

          <div class="mt-4 flex justify-end">
            <Button @click="showAddDialog = false">完成</Button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
