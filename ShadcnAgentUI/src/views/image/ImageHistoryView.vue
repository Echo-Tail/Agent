<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { toast } from 'sonner'
import PageHeader from '@/components/PageHeader.vue'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import {
  listImageRecords, deleteImageRecord, type ImageRecord,
} from '@/api/image'
import {
  Loader2, ImageIcon, Download, Copy, Check, ZoomIn, Trash2, X,
} from 'lucide-vue-next'

const records = ref<ImageRecord[]>([])
const loading = ref(true)
const page = ref(0)
const pageSize = 20
const totalElements = ref(0)
const promptFilter = ref('')
const copiedRecordId = ref<number | null>(null)

// Lightbox
const lightboxOpen = ref(false)
const lightboxUrl = ref('')
const zoomLevel = ref(1)
const panX = ref(0)
const panY = ref(0)

const totalPages = computed(() => Math.max(1, Math.ceil(totalElements.value / pageSize)))

const modeLabels: Record<string, string> = {
  GENERATE: '文生图',
  EDIT: '图生图',
}

function imageUrl(path: string): string {
  if (!path) return ''
  if (/^https?:\/\//i.test(path)) return path
  let normalized = path.replace(/\\/g, '/')
  // Remove leading ./ or .\
  normalized = normalized.replace(/^\.\//, '')
  // Remove double /uploads/ prefix if it somehow got doubled
  normalized = normalized.replace(/^\/uploads\/uploads\//, '/uploads/')
  // Ensure single leading /
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

async function handleDelete(record: ImageRecord) {
  if (!window.confirm(`删除此条生成记录？`)) return
  try {
    await deleteImageRecord(record.id)
    toast.success('已删除')
    await loadRecords()
  } catch {
    toast.error('删除失败')
  }
}

function openLightbox(url: string) {
  lightboxUrl.value = imageUrl(url)
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

function setPage(p: number) {
  page.value = p
  loadRecords()
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
              <span class="text-[10px] text-muted-foreground">{{ record.width }}×{{ record.height }}</span>
            </div>
            <p class="text-xs text-muted-foreground line-clamp-2">{{ record.prompt }}</p>
            <div class="flex items-center gap-1 pt-0.5">
              <Button variant="ghost" size="sm" class="h-6 text-[10px] px-1 text-muted-foreground hover:text-foreground"
                @click.stop="downloadImage(firstImagePath(record))">
                <Download class="h-3 w-3 mr-0.5" />
              </Button>
              <Button variant="ghost" size="sm" class="h-6 text-[10px] px-1 text-muted-foreground hover:text-foreground"
                @click.stop="handleCopyPrompt(record.id, record.prompt)">
                <Check v-if="copiedRecordId === record.id" class="h-3 w-3 text-green-500" />
                <Copy v-else class="h-3 w-3" />复制
              </Button>
              <Button variant="ghost" size="sm" class="h-6 text-[10px] px-1 text-muted-foreground hover:text-destructive ml-auto"
                @click.stop="handleDelete(record)">
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

    <!-- Lightbox -->
    <Teleport to="body">
      <div
        v-if="lightboxOpen"
        class="fixed inset-0 z-[100] bg-black/95 flex items-center justify-center"
        @click="closeLightbox"
        @wheel.prevent="handleWheel"
      >
        <button
          class="absolute top-4 right-4 z-10 rounded-full bg-black/50 p-2 text-white hover:bg-black/70 transition-colors"
          @click.stop="closeLightbox"
        >
          <X class="h-5 w-5" />
        </button>

        <div class="absolute bottom-4 left-1/2 -translate-x-1/2 flex items-center gap-2 bg-black/50 rounded-full px-3 py-1.5 text-white text-xs">
          <Button variant="ghost" size="sm" class="h-6 text-white hover:bg-white/20" @click.stop="zoomLevel = Math.max(0.5, zoomLevel - 0.2)">-</Button>
          <span>{{ Math.round(zoomLevel * 100) }}%</span>
          <Button variant="ghost" size="sm" class="h-6 text-white hover:bg-white/20" @click.stop="zoomLevel = Math.min(5, zoomLevel + 0.2)">+</Button>
          <Button variant="ghost" size="sm" class="h-6 text-white hover:bg-white/20 ml-2" @click.stop="zoomLevel = 1; panX = 0; panY = 0">重置</Button>
        </div>

        <img
          v-if="lightboxUrl"
          :src="lightboxUrl"
          class="max-w-[90vw] max-h-[90vh] w-auto h-auto transition-transform duration-100 cursor-grab active:cursor-grabbing select-none"
          :style="{
            transform: `scale(${zoomLevel}) translate(${panX}px, ${panY}px)`,
          }"
          alt="Preview"
          draggable="false"
          @click.stop
        />
      </div>
    </Teleport>
  </div>
</template>
