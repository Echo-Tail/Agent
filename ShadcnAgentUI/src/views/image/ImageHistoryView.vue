<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { toast } from 'sonner'
import PageHeader from '@/components/PageHeader.vue'
import ImageLightbox from '@/components/ImageLightbox.vue'
import { useImageLightbox } from '@/composables/useImageLightbox'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { VisuallyHidden } from 'reka-ui'
import {
  listImageRecords, deleteImageRecord, getImageRecordApi, type ImageRecord,
} from '@/api/image'
import { importFromRecord, listSpaces } from '@/api/assets'
import type { AssetSpace } from '@/api/assets'
import { createPrompt as createPromptApi, setCoverRef } from '@/api/prompts'
import {
  Loader2, ImageIcon, Download, Copy, Check, ZoomIn, Trash2, Plus, Upload, Eye,
} from 'lucide-vue-next'

const records = ref<ImageRecord[]>([])
const loading = ref(true)
const page = ref(0)
const pageSize = 20
const totalElements = ref(0)
const promptFilter = ref('')
const copiedRecordId = ref<number | null>(null)

const { lightboxOpen, lightboxUrl, lightboxAlt, openLightbox: showLightbox } = useImageLightbox()

const totalPages = computed(() => Math.max(1, Math.ceil(totalElements.value / pageSize)))

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

const modeLabels: Record<string, string> = {
  GENERATE: '文生图',
  EDIT: '图生图',
  SUPER_RESOLUTION: '超分',
}

function imageUrl(path: string): string {
  if (!path) return ''
  if (/^https?:\/\//i.test(path)) return path
  let normalized = path.replace(/\\/g, '/').replace(/^\.\//, '')
  if (!normalized.startsWith('/')) normalized = '/' + normalized
  return normalized
}

async function loadRecords() {
  loading.value = true
  try {
    const res = await listImageRecords({
      page: page.value,
      size: pageSize,
      prompt: promptFilter.value || undefined,
    })
    records.value = res.content ?? []
    totalElements.value = res.page?.totalElements ?? 0
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function firstImagePath(record: ImageRecord): string {
  if (!record.resultPath) return ''
  return record.resultPath.split('\n').filter(Boolean)[0] || ''
}

async function downloadImage(url: string) {
  try {
    const a = document.createElement('a')
    a.href = imageUrl(url)
    a.download = url.split('/').pop() || 'image.png'
    a.click()
  } catch {
    toast.error('下载失败')
  }
}

async function handleCopyPrompt(recordId: number, text: string) {
  try {
    await navigator.clipboard.writeText(text)
    copiedRecordId.value = recordId
    setTimeout(() => { copiedRecordId.value = null }, 1500)
  } catch {
    toast.error('复制失败')
  }
}

// ── 删除确认 ──
const deleteConfirmOpen = ref(false)
const deleteTarget = ref<ImageRecord | null>(null)

function openDeleteConfirm(record: ImageRecord) {
  deleteTarget.value = record
  deleteConfirmOpen.value = true
}

async function handleDelete() {
  if (!deleteTarget.value) return
  try {
    await deleteImageRecord(deleteTarget.value.id)
    toast.success('已删除')
    deleteConfirmOpen.value = false
    deleteTarget.value = null
    await loadRecords()
  } catch {
    toast.error('删除失败')
  }
}

// ── 上传到素材库 ──
const uploadDialogOpen = ref(false)
const uploadTarget = ref<ImageRecord | null>(null)
const spaces = ref<AssetSpace[]>([])
const selectedSpaceId = ref<number | undefined>(undefined)
const uploadBusy = ref(false)

async function openUploadDialog(record: ImageRecord) {
  uploadTarget.value = record
  selectedSpaceId.value = undefined
  uploadBusy.value = false
  try {
    spaces.value = await listSpaces()
  } catch {
    spaces.value = []
  }
  uploadDialogOpen.value = true
}

async function handleUploadToAssets() {
  if (!uploadTarget.value || uploadBusy.value) return
  uploadBusy.value = true
  try {
    await importFromRecord(uploadTarget.value.id, selectedSpaceId.value)
    toast.success('已上传到素材库')
    uploadDialogOpen.value = false
  } catch {
    toast.error('上传失败')
  } finally {
    uploadBusy.value = false
  }
}

function openLightbox(url: string) {
  showLightbox(imageUrl(url))
}

const saveDialogOpen = ref(false)
const saveTarget = ref<ImageRecord | null>(null)
const saveCategory = ref('车载主机')
const saveTags = ref('')
const saveBusy = ref(false)

function openSaveDialog(record: ImageRecord) {
  saveTarget.value = record
  saveCategory.value = '车载主机'
  saveTags.value = ''
  saveBusy.value = false
  saveDialogOpen.value = true
}

/** 取 resultPath 第一张图，清理为相对路径（去掉 /uploads/ 前缀） */
function firstImagePathClean(record: ImageRecord): string {
  const raw = record.resultPath?.split('\n').filter(Boolean)[0] || ''
  return raw.replace(/\\/g, '/')
    .replace(/^\/uploads\//, '')
    .replace(/^\.\/uploads\//, '')
    .replace(/^\.\//, '')
}

async function handleSaveToLibrary() {
  if (!saveTarget.value || !saveCategory.value || saveBusy.value) return
  saveBusy.value = true
  try {
    const record = saveTarget.value
    const fd = new FormData()
    fd.append('prompt', record.prompt)
    fd.append('category', saveCategory.value)
    if (saveTags.value.trim()) fd.append('tags', saveTags.value.trim())

    const created = await createPromptApi(fd)
    if (created?.id) {
      const coverPath = firstImagePathClean(record)
      if (coverPath) {
        await setCoverRef(created.id, coverPath)
      }
    }
    toast.success('已保存到提示词库')
    saveDialogOpen.value = false
  } catch {
    toast.error('保存失败')
  } finally {
    saveBusy.value = false
  }
}

function setPage(p: number) {
  page.value = p
  loadRecords()
}

// ── 查看详情 ──
const detailDialogOpen = ref(false)
const detailRecord = ref<ImageRecord | null>(null)
const detailLoading = ref(false)
const activeTab = ref<'result' | 'reference'>('result')

async function openDetail(record: ImageRecord) {
  detailLoading.value = true
  detailDialogOpen.value = true
  activeTab.value = 'result'
  // 先用列表已有的数据显示
  detailRecord.value = record
  try {
    // 再异步获取完整详情（含参考图/遮罩图路径）
    detailRecord.value = await getImageRecordApi(record.id)
  } catch {
    // 静默失败，列表数据仍可用
  } finally {
    detailLoading.value = false
  }
}

function closeDetail() {
  detailDialogOpen.value = false
  detailRecord.value = null
}

function splitImagePaths(paths: string | null | undefined): string[] {
  if (!paths) return []
  return paths.split('\n').filter(Boolean)
}

function formatDateTime(iso: string): string {
  if (!iso) return '—'
  // 2026-07-07T14:18:41.705533 → 2026-07-07 14:18:41
  return iso.replace('T', ' ').split('.')[0] ?? iso
}

onMounted(loadRecords)
</script>

<template>
  <div class="space-y-5">
    <PageHeader title="生成历史" description="文生图与图生图的历史记录" />

    <div class="flex items-center gap-2">
      <Input v-model="promptFilter" placeholder="搜索提示词..." class="w-56" @keyup.enter="loadRecords" />
      <Button variant="outline" size="icon" :disabled="loading" @click="loadRecords">
        <Loader2 :class="['h-4 w-4', loading ? 'animate-spin' : '']" />
      </Button>
    </div>

    <div v-if="loading" class="py-16 text-center text-sm text-muted-foreground">加载中...</div>

    <template v-else-if="records.length > 0">
      <div class="grid gap-4 grid-cols-2 sm:grid-cols-3 md:grid-cols-4 xl:grid-cols-5">
        <Card
          v-for="record in records"
          :key="record.id"
          class="overflow-hidden group hover:shadow-md transition-shadow"
        >
          <div
            class="relative aspect-[4/3] bg-muted/30 cursor-zoom-in"
            @click="openLightbox(firstImagePath(record))"
          >
            <img
              v-if="firstImagePath(record)"
              :src="imageUrl(firstImagePath(record))"
              class="w-full h-full object-cover"
              alt=""
            />
            <div v-else class="flex items-center justify-center h-full text-muted-foreground">
              <ImageIcon class="h-8 w-8" />
            </div>
            <div class="absolute top-1 right-1 opacity-0 group-hover:opacity-100 transition-opacity flex gap-1">
              <Button variant="secondary" size="icon" class="h-6 w-6" @click.stop="openLightbox(firstImagePath(record))">
                <ZoomIn class="h-3 w-3" />
              </Button>
            </div>
          </div>

          <div class="p-2 space-y-1.5">
            <div class="flex items-center gap-1">
              <Badge variant="secondary" class="text-[10px] px-1 py-0">{{ modeLabels[record.mode] || record.mode }}</Badge>
              <span v-if="record.width && record.height" class="text-[10px] text-muted-foreground">{{ record.width }}×{{ record.height }}</span>
            </div>
            <p class="text-xs text-muted-foreground line-clamp-2">{{ record.prompt }}</p>
            <div class="flex items-center gap-1 pt-0.5">
              <Button variant="ghost" size="sm" class="h-6 text-[10px] px-1 text-muted-foreground hover:text-foreground"
                @click.stop="openDetail(record)">
                <Eye class="h-3 w-3 mr-0.5" />查看
              </Button>
              <Button variant="ghost" size="sm" class="h-6 text-[10px] px-1 text-muted-foreground hover:text-foreground"
                @click.stop="downloadImage(firstImagePath(record))">
                <Download class="h-3 w-3 mr-0.5" />
              </Button>
              <Button variant="ghost" size="sm" class="h-6 text-[10px] px-1 text-muted-foreground hover:text-foreground"
                @click.stop="handleCopyPrompt(record.id, record.prompt)">
                <Check v-if="copiedRecordId === record.id" class="h-3 w-3 text-green-500" />
                <Copy v-else class="h-3 w-3" />复制
              </Button>
              <Button variant="ghost" size="sm" class="h-6 text-[10px] px-1 text-muted-foreground hover:text-primary"
                @click.stop="openSaveDialog(record)">
                <Plus class="h-3 w-3 mr-0.5" />保存
              </Button>
              <Button variant="ghost" size="sm" class="h-6 text-[10px] px-1 text-muted-foreground hover:text-primary"
                @click.stop="openUploadDialog(record)">
                <Upload class="h-3 w-3" />
              </Button>
              <Button variant="ghost" size="sm" class="h-6 text-[10px] px-1 text-muted-foreground hover:text-destructive"
                @click.stop="openDeleteConfirm(record)">
                <Trash2 class="h-3 w-3" />
              </Button>
            </div>
          </div>
        </Card>
      </div>

      <!-- Pagination -->
      <div v-if="totalPages > 1" class="flex items-center justify-center gap-2 pt-4">
        <Button variant="outline" size="sm" :disabled="page === 0" @click="setPage(page - 1)">上一页</Button>
        <template v-for="p in totalPages" :key="p">
          <Button
            v-if="Math.abs(p - 1 - page) <= 2 || p === 1 || p === totalPages"
            variant="outline" size="sm"
            :class="p - 1 === page ? 'bg-primary text-primary-foreground' : ''"
            @click="setPage(p - 1)"
          >{{ p }}</Button>
          <span v-else-if="p === 2 && page > 3" class="text-muted-foreground">...</span>
          <span v-else-if="p === totalPages - 1 && page < totalPages - 4" class="text-muted-foreground">...</span>
        </template>
        <Button variant="outline" size="sm" :disabled="page >= totalPages - 1" @click="setPage(page + 1)">下一页</Button>
      </div>
    </template>

    <div v-else class="py-16 text-center text-sm text-muted-foreground">
      <ImageIcon class="h-8 w-8 mx-auto mb-2 text-muted-foreground/50" />
      <p>暂无生成记录</p>
    </div>

    <ImageLightbox v-model:open="lightboxOpen" :src="lightboxUrl" :alt="lightboxAlt" />
    <!-- ══════════ 保存到提示词库 dialog ══════════ -->
    <Dialog v-model:open="saveDialogOpen">
      <DialogContent class="sm:max-w-[480px]" aria-describedby="save-prompt-desc">
        <DialogHeader><DialogTitle>保存到提示词库</DialogTitle></DialogHeader>
        <VisuallyHidden><div id="save-prompt-desc">将当前生成记录保存为提示词模板</div></VisuallyHidden>
        <div class="space-y-4 py-2">
          <!-- Cover preview -->
          <div v-if="saveTarget" class="flex justify-center">
            <div class="w-40 h-30 rounded-md overflow-hidden bg-muted/30">
              <img
                :src="imageUrl(firstImagePath(saveTarget))"
                class="w-full h-full object-cover" alt=""
              />
            </div>
          </div>

          <!-- Prompt (readonly) -->
          <div class="space-y-1.5">
            <label class="text-xs font-medium">提示词</label>
            <Textarea
              :model-value="saveTarget?.prompt ?? ''"
              readonly
              rows="3"
              class="max-h-[100px] overflow-y-auto text-xs"
            />
          </div>

          <!-- Category -->
          <div class="space-y-1.5">
            <label class="text-xs font-medium">品类</label>
            <Select v-model="saveCategory">
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem v-for="c in categories" :key="c.value" :value="c.value">{{ c.label }}</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <!-- Tags -->
          <div class="space-y-1.5">
            <label class="text-xs font-medium">标签 <span class="text-muted-foreground">（可选）</span></label>
            <Input v-model="saveTags" placeholder="输入标签，多个用逗号分隔" />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" :disabled="saveBusy" @click="saveDialogOpen = false">取消</Button>
          <Button :disabled="!saveCategory || saveBusy" @click="handleSaveToLibrary">
            <Loader2 v-if="saveBusy" class="mr-1 h-4 w-4 animate-spin" />
            确定保存
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- ══════════ 删除确认 dialog ══════════ -->
    <Dialog v-model:open="deleteConfirmOpen">
      <DialogContent class="sm:max-w-[380px]" aria-describedby="delete-confirm-desc">
        <DialogHeader><DialogTitle>确认删除</DialogTitle></DialogHeader>
        <VisuallyHidden><div id="delete-confirm-desc">确认删除此条生成记录</div></VisuallyHidden>
        <p class="text-sm text-muted-foreground">删除此条生成记录？此操作不可恢复。</p>
        <DialogFooter>
          <Button variant="outline" @click="deleteConfirmOpen = false">取消</Button>
          <Button variant="destructive" @click="handleDelete">删除</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- ══════════ 上传到素材库 dialog ══════════ -->
    <Dialog v-model:open="uploadDialogOpen">
      <DialogContent class="sm:max-w-[420px]" aria-describedby="upload-asset-desc">
        <DialogHeader><DialogTitle>上传到素材库</DialogTitle></DialogHeader>
        <VisuallyHidden><div id="upload-asset-desc">将图片上传到指定的素材空间</div></VisuallyHidden>
        <div class="space-y-4 py-2">
          <div v-if="uploadTarget" class="flex justify-center">
            <div class="w-40 h-30 rounded-md overflow-hidden bg-muted/30">
              <img :src="imageUrl(firstImagePath(uploadTarget))" class="w-full h-full object-cover" alt="" />
            </div>
          </div>
          <div class="space-y-1.5">
            <label class="text-xs font-medium">目标素材空间</label>
            <Select v-if="spaces.length > 0" v-model="selectedSpaceId">
              <SelectTrigger><SelectValue placeholder="选择空间（可选）" /></SelectTrigger>
              <SelectContent>
                <SelectItem v-for="s in spaces" :key="s.id" :value="s.id">{{ s.name }}</SelectItem>
              </SelectContent>
            </Select>
            <p v-else class="text-xs text-muted-foreground">暂无可用素材空间，将上传到默认空间</p>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" :disabled="uploadBusy" @click="uploadDialogOpen = false">取消</Button>
          <Button :disabled="uploadBusy" @click="handleUploadToAssets">
            <Loader2 v-if="uploadBusy" class="mr-1 h-4 w-4 animate-spin" />
            确定上传
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- ══════════ 查看详情 dialog ══════════ -->
    <Dialog v-model:open="detailDialogOpen">
      <DialogContent class="sm:max-w-[700px] max-h-[85vh] overflow-y-auto" aria-describedby="detail-desc">
        <DialogHeader>
          <DialogTitle>
            生成详情
            <Badge v-if="detailRecord" variant="secondary" class="ml-2 align-middle">
              {{ modeLabels[detailRecord.mode] || detailRecord.mode }}
            </Badge>
          </DialogTitle>
        </DialogHeader>
        <VisuallyHidden><div id="detail-desc">查看图片生成的完整信息，包括提示词、参考图和遮罩图</div></VisuallyHidden>

        <div v-if="detailLoading" class="py-12 text-center text-sm text-muted-foreground">加载中...</div>

        <template v-else-if="detailRecord">
          <div class="space-y-4 py-2">

            <!-- 生成结果图 -->
            <div>
              <label class="text-xs font-medium mb-1.5 block">生成结果</label>
              <div class="flex items-center justify-center rounded-lg overflow-hidden bg-muted/20 max-h-[320px]">
                <img
                  :src="imageUrl(firstImagePath(detailRecord))"
                  class="max-w-full max-h-[320px] object-contain cursor-zoom-in"
                  alt="生成结果"
                  @click="openLightbox(firstImagePath(detailRecord))"
                />
              </div>
            </div>

            <!-- 提示词 -->
            <div class="space-y-1">
              <label class="text-xs font-medium block">提示词</label>
              <Textarea
                :model-value="detailRecord.prompt"
                readonly
                rows="3"
                class="text-xs max-h-[150px] overflow-y-auto"
              />
            </div>

            <!-- 改写后提示词 -->
            <div v-if="detailRecord.revisedPrompt" class="space-y-1">
              <label class="text-xs font-medium block">API 改写后提示词</label>
              <Textarea
                :model-value="detailRecord.revisedPrompt"
                readonly
                rows="2"
                class="text-xs max-h-[120px] overflow-y-auto text-muted-foreground"
              />
            </div>

            <!-- 参数信息 -->
            <div class="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
              <div>
                <span class="text-muted-foreground">尺寸</span>
                <p class="font-medium">{{ detailRecord.size }}</p>
              </div>
              <div>
                <span class="text-muted-foreground">质量</span>
                <p class="font-medium">{{ detailRecord.quality }}</p>
              </div>
              <div>
                <span class="text-muted-foreground">分辨率</span>
                <p class="font-medium">{{ detailRecord.width && detailRecord.height ? `${detailRecord.width}×${detailRecord.height}` : '—' }}</p>
              </div>
              <div>
                <span class="text-muted-foreground">耗时</span>
                <p class="font-medium">{{ (detailRecord.timeCostMs / 1000).toFixed(1) }}s</p>
              </div>
            </div>

            <!-- 参考图 & 遮罩（仅图生图模式） -->
            <template v-if="detailRecord.mode === 'EDIT'">
              <!-- 参考图 -->
              <div v-if="splitImagePaths(detailRecord.referenceImagePaths).length > 0">
                <label class="text-xs font-medium mb-1.5 block">
                  参考图（{{ splitImagePaths(detailRecord.referenceImagePaths).length }} 张）
                </label>
                <div class="flex flex-wrap gap-2">
                  <div
                    v-for="(refPath, idx) in splitImagePaths(detailRecord.referenceImagePaths)"
                    :key="idx"
                    class="w-28 h-28 rounded-lg overflow-hidden bg-muted/20 border cursor-zoom-in hover:opacity-80 transition-opacity"
                    @click="openLightbox(refPath)"
                  >
                    <img :src="imageUrl(refPath)" class="w-full h-full object-cover" alt="参考图" />
                  </div>
                </div>
              </div>

              <!-- 遮罩图 -->
              <div v-if="detailRecord.maskImagePath">
                <label class="text-xs font-medium mb-1.5 block">遮罩图（Mask）</label>
                <div class="w-28 h-28 rounded-lg overflow-hidden bg-muted/20 border cursor-zoom-in hover:opacity-80 transition-opacity"
                  @click="openLightbox(detailRecord.maskImagePath!)">
                  <img :src="imageUrl(detailRecord.maskImagePath)" class="w-full h-full object-cover" alt="遮罩图" />
                </div>
              </div>
            </template>

            <!-- 时间 -->
            <div class="text-xs text-muted-foreground pt-1">
              创建时间：{{ formatDateTime(detailRecord.createdAt) }}
            </div>
          </div>
        </template>

        <DialogFooter>
          <Button variant="outline" @click="closeDetail">关闭</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
