<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useI18n } from 'vue-i18n'
import { toast } from 'sonner'
import { getGalleryItems, publishToGallery, unpublishFromGallery, adminRemoveGalleryItem, getPublishableRecords } from '@/api/gallery'
import type { GalleryItem } from '@/api/gallery'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import PageHeader from '@/components/PageHeader.vue'
import {
  Loader2,
  Copy,
  Download,
  ImagePlus,
  X,
  Trash2,
  ShieldX,
  ZoomIn,
  Tag,
} from 'lucide-vue-next'

const auth = useAuthStore()
const { t } = useI18n()

// ── Data ──
const items = ref<GalleryItem[]>([])
const loading = ref(true)
const page = ref(0)
const totalPages = ref(0)
const loadingMore = ref(false)

// ── Lightbox ──
const lightboxOpen = ref(false)
const lightboxUrl = ref('')
const zoomLevel = ref(1)
const panX = ref(0)
const panY = ref(0)

// ── Publish Dialog ──
const publishDialogOpen = ref(false)
const publishableRecords = ref<any[]>([])
const selectedRecordId = ref<number | null>(null)
const publishTitle = ref('')
const publishCategoryTags = ref('')
const publishStyleTags = ref('')
const publishNegativePrompt = ref('')
const publishing = ref(false)
const loadingRecords = ref(false)

// ── Confirm Delete Dialog ──
const confirmDialogOpen = ref(false)
const confirmAction = ref<'unpublish' | 'adminRemove' | null>(null)
const confirmTargetId = ref<number | null>(null)

// ── Computed ──
const isAdmin = computed(() => auth.isAdmin)

// ── Load gallery items ──
async function loadItems(reset = false) {
  if (reset) {
    page.value = 0
    loading.value = true
  }
  try {
    const res = await getGalleryItems(page.value, 20)
    if (reset) {
      items.value = res.content
    } else {
      items.value = [...items.value, ...res.content]
    }
    totalPages.value = res.page.totalPages
  } catch {
    // toast already handled by interceptor
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

// ── Infinite scroll ──
function onScroll(event: Event) {
  const el = event.target as HTMLElement
  if (loadingMore.value || page.value >= totalPages.value - 1) return
  if (el.scrollHeight - el.scrollTop - el.clientHeight < 300) {
    loadingMore.value = true
    page.value++
    loadItems()
  }
}

// ── Lightbox ──
function openLightbox(url: string) {
  lightboxUrl.value = url
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

// ── Copy prompt ──
function copyPrompt(text: string) {
  navigator.clipboard.writeText(text).then(() => {
    toast.success(t('gallery.promptCopied') || 'Prompt 已复制')
  })
}

// ── Download image ──
function downloadImage(url: string) {
  const a = document.createElement('a')
  a.href = url
  a.download = url.split('/').pop() || 'image.png'
  a.click()
}

// ── Open publish dialog ──
async function openPublishDialog() {
  publishDialogOpen.value = true
  loadingRecords.value = true
  try {
    publishableRecords.value = await getPublishableRecords()
  } catch {
    publishableRecords.value = []
  } finally {
    loadingRecords.value = false
  }
}

// ── Submit publish ──
async function submitPublish() {
  if (!selectedRecordId.value) {
    toast.error(t('gallery.selectRecordFirst') || '请选择要发布的图片')
    return
  }
  publishing.value = true
  try {
    await publishToGallery({
      recordId: selectedRecordId.value,
      title: publishTitle.value || undefined,
      categoryTags: publishCategoryTags.value || undefined,
      styleTags: publishStyleTags.value || undefined,
      negativePrompt: publishNegativePrompt.value || undefined,
    })
    toast.success(t('gallery.publishSuccess') || '发布成功')
    publishDialogOpen.value = false
    resetPublishForm()
    await loadItems(true)
  } catch {
    // toast handled by interceptor
  } finally {
    publishing.value = false
  }
}

function resetPublishForm() {
  selectedRecordId.value = null
  publishTitle.value = ''
  publishCategoryTags.value = ''
  publishStyleTags.value = ''
  publishNegativePrompt.value = ''
}

// ── Confirm action dialog ──
function showConfirm(action: 'unpublish' | 'adminRemove', id: number) {
  confirmAction.value = action
  confirmTargetId.value = id
  confirmDialogOpen.value = true
}

async function executeConfirm() {
  if (!confirmTargetId.value || !confirmAction.value) return
  try {
    if (confirmAction.value === 'unpublish') {
      await unpublishFromGallery(confirmTargetId.value)
      toast.success(t('gallery.unpublishSuccess') || '已取消发布')
    } else {
      await adminRemoveGalleryItem(confirmTargetId.value)
      toast.success(t('gallery.adminRemoveSuccess') || '已下架')
    }
    confirmDialogOpen.value = false
    items.value = items.value.filter(i => i.id !== confirmTargetId.value)
    if (lightboxUrl.value && confirmTargetId.value) {
      closeLightbox()
    }
  } catch {
    // toast handled by interceptor
  }
}

// ── Helpers ──
function getImageUrl(rec: { resultPath: string }) {
  return '/' + rec.resultPath.replace(/\\\\/g, '/').replace(/^\.\//, '')
}

// ── Lifecycle ──
onMounted(() => {
  loadItems(true)
})
</script>

<template>
  <div class="h-full flex flex-col">
    <PageHeader :title="$t('pageTitle.gallery')" :description="$t('gallery.desc') || '浏览团队精选作品'" />

    <!-- Top bar -->
    <div class="flex items-center justify-between px-6 py-3 border-b">
      <div class="text-sm text-muted-foreground">
        {{ items.length > 0 ? `${$t('gallery.totalItems', { count: items.length })}` : '' }}
      </div>
      <Button @click="openPublishDialog">
        <ImagePlus class="w-4 h-4 mr-2" />
        {{ $t('gallery.publishWork') || '发布作品' }}
      </Button>
    </div>

    <!-- Masonry grid -->
    <div class="flex-1 overflow-y-auto px-6 py-4" @scroll="onScroll">
      <!-- Loading skeleton -->
      <div v-if="loading" class="columns-2 md:columns-3 lg:columns-4 gap-4">
        <div v-for="n in 8" :key="n" class="break-inside-avoid mb-4">
          <Skeleton class="w-full rounded-lg" :class="n % 3 === 0 ? 'h-72' : n % 3 === 1 ? 'h-56' : 'h-80'" />
          <div class="mt-2 space-y-1">
            <Skeleton class="h-4 w-3/4" />
            <Skeleton class="h-3 w-1/2" />
          </div>
        </div>
      </div>

      <!-- Empty state -->
      <div v-else-if="items.length === 0" class="flex flex-col items-center justify-center py-24 text-muted-foreground">
        <ImagePlus class="w-16 h-16 mb-4 opacity-30" />
        <p class="text-lg font-medium">{{ $t('gallery.emptyTitle') || '画廊还没有作品' }}</p>
        <p class="text-sm mt-1">{{ $t('gallery.emptyDesc') || '发布你的第一个精选作品吧' }}</p>
        <Button class="mt-4" variant="outline" @click="openPublishDialog">
          {{ $t('gallery.publishWork') || '发布作品' }}
        </Button>
      </div>

      <!-- Masonry -->
      <div v-else class="columns-2 md:columns-3 lg:columns-4 gap-4">
        <div
          v-for="item in items"
          :key="item.id"
          class="break-inside-avoid mb-4 rounded-lg overflow-hidden border bg-card hover:shadow-lg transition-shadow"
        >
          <!-- Clickable Image → lightbox -->
          <div class="relative overflow-hidden cursor-pointer" @click="openLightbox(item.imageUrl)">
            <img
              :src="item.imageUrl"
              :alt="item.title"
              class="w-full h-auto"
              loading="lazy"
            />
          </div>

          <!-- Info bar -->
          <div class="p-3">
            <div class="flex items-start justify-between">
              <div class="flex-1 min-w-0">
                <p class="text-sm font-medium truncate">{{ item.title }}</p>
                <p class="text-xs text-muted-foreground mt-0.5">{{ item.authorName }}</p>
                <div class="flex flex-wrap items-center gap-1 mt-1">
                  <Badge variant="outline" class="text-[10px] px-1.5 py-0">{{ item.size }}</Badge>
                  <Badge v-if="item.categoryTags" variant="secondary" class="text-[10px] px-1.5 py-0">
                    <Tag class="w-2.5 h-2.5 mr-0.5 inline" />
                    {{ item.categoryTags }}
                  </Badge>
                  <Badge v-if="item.styleTags" variant="default" class="text-[10px] px-1.5 py-0">
                    <Tag class="w-2.5 h-2.5 mr-0.5 inline" />
                    {{ item.styleTags }}
                  </Badge>
                </div>
              </div>
            </div>
            <!-- Action buttons -->
            <div class="flex items-center gap-2 pt-2 border-t mt-2">
              <Button
                variant="ghost"
                size="sm"
                class="h-7 text-xs text-muted-foreground hover:text-foreground gap-1 px-2"
                @click.stop="copyPrompt(item.prompt)"
              >
                <Copy class="h-3 w-3" />
                {{ $t('gallery.copyPrompt') || '提示词' }}
              </Button>
              <Button
                variant="ghost"
                size="sm"
                class="h-7 text-xs text-muted-foreground hover:text-foreground gap-1 px-2"
                @click.stop="downloadImage(item.imageUrl)"
              >
                <Download class="h-3 w-3" />
                {{ $t('gallery.download') || '下载' }}
              </Button>
              <!-- Owner: unpublish -->
              <Button
                v-if="item.userId === auth.currentUser?.id"
                variant="ghost"
                size="sm"
                class="h-7 text-xs text-muted-foreground hover:text-destructive gap-1 px-2"
                @click.stop="showConfirm('unpublish', item.id)"
              >
                <X class="h-3 w-3" />
                {{ $t('gallery.unpublish') || '取消发布' }}
              </Button>
              <!-- Admin: remove -->
              <Button
                v-if="isAdmin && item.userId !== auth.currentUser?.id"
                variant="ghost"
                size="sm"
                class="h-7 text-xs text-muted-foreground hover:text-destructive gap-1 px-2"
                @click.stop="showConfirm('adminRemove', item.id)"
              >
                <ShieldX class="h-3 w-3" />
                {{ $t('gallery.adminRemove') || '下架' }}
              </Button>
            </div>
          </div>
        </div>
      </div>

      <!-- Loading more -->
      <div v-if="loadingMore" class="flex justify-center py-6">
        <Loader2 class="w-6 h-6 animate-spin text-muted-foreground" />
      </div>
    </div>

    <!-- ═══ Image Lightbox ═══ -->
    <Teleport to="body">
      <div
        v-if="lightboxOpen"
        class="fixed inset-0 z-[100] bg-black/95 flex items-center justify-center"
        @click="closeLightbox"
        @wheel.prevent="handleWheel"
      >
        <h2 class="sr-only">{{ $t('gallery.desc') }}</h2>

        <Button
          variant="ghost"
          size="icon"
          class="absolute top-4 right-4 z-10 rounded-full bg-black/50 text-white hover:bg-black/70"
          @click.stop="closeLightbox"
        >
          <X class="h-5 w-5" />
        </Button>

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
            重置缩放
          </Button>
        </div>

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

    <!-- ── Publish Dialog ── -->
    <Dialog :open="publishDialogOpen" @update:open="publishDialogOpen = $event">
      <DialogContent class="max-w-lg">
        <DialogHeader>
          <DialogTitle>{{ $t('gallery.publishWork') || '发布作品' }}</DialogTitle>
          <DialogDescription>{{ $t('gallery.publishDesc') || '从你的历史记录中选择要发布的图片' }}</DialogDescription>
        </DialogHeader>

        <div class="space-y-5">
          <div class="space-y-2">
            <Label class="text-sm font-medium">{{ $t('gallery.selectImage') || '选择图片' }}</Label>
            <div v-if="loadingRecords" class="flex justify-center py-6">
              <Loader2 class="w-5 h-5 animate-spin text-muted-foreground" />
            </div>
            <div v-else-if="publishableRecords.length === 0" class="text-sm text-muted-foreground py-4 text-center">
              {{ $t('gallery.noPublishableRecords') || '没有可发布的记录，请先生成图片' }}
            </div>
            <div v-else class="grid grid-cols-3 gap-3 max-h-48 overflow-y-auto rounded-lg border p-2">
              <div
                v-for="rec in publishableRecords"
                :key="rec.id"
                class="relative cursor-pointer rounded-lg overflow-hidden ring-2 transition-all"
                :class="selectedRecordId === rec.id ? 'ring-primary' : 'ring-transparent hover:ring-muted-foreground/30'"
                @click="selectedRecordId = rec.id"
              >
                <img :src="getImageUrl(rec)" :alt="rec.prompt?.slice(0, 30) || 'gallery image'" class="w-full aspect-[4/3] object-cover" />
                <div class="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/70 to-transparent px-1.5 py-1">
                  <p class="text-[11px] text-white truncate leading-tight">{{ rec.prompt?.slice(0, 28) }}</p>
                </div>
              </div>
            </div>
          </div>

          <div class="space-y-4">
            <div class="space-y-1.5">
              <Label for="publish-title" class="text-sm">{{ $t('gallery.title') || '作品标题' }} <span class="text-muted-foreground text-xs font-normal">(选填)</span></Label>
              <Input id="publish-title" v-model="publishTitle" :placeholder="$t('gallery.title') ? '默认：未命名作品' : 'Untitled'" class="h-9" />
            </div>
            <div class="space-y-1.5">
              <Label for="publish-category" class="text-sm">{{ $t('gallery.categoryTags') || '品类标签' }} <span class="text-muted-foreground text-xs font-normal">(选填，逗号分隔)</span></Label>
              <Input id="publish-category" v-model="publishCategoryTags" placeholder="如：汽车用品,车载音频" class="h-9" />
            </div>
            <div class="space-y-1.5">
              <Label for="publish-style" class="text-sm">{{ $t('gallery.styleTags') || '风格标签' }} <span class="text-muted-foreground text-xs font-normal">(选填，逗号分隔)</span></Label>
              <Input id="publish-style" v-model="publishStyleTags" placeholder="如：科技风,写实" class="h-9" />
            </div>
            <div class="space-y-1.5">
              <Label for="publish-negative" class="text-sm">{{ $t('gallery.negativePrompt') || '负面提示词' }} <span class="text-muted-foreground text-xs font-normal">(选填)</span></Label>
              <Textarea id="publish-negative" v-model="publishNegativePrompt" placeholder="可补充负面提示词" rows="2" class="min-h-[60px]" />
            </div>
          </div>
        </div>

        <DialogFooter class="mt-2">
          <Button variant="outline" @click="publishDialogOpen = false">{{ $t('gallery.cancel') || '取消' }}</Button>
          <Button :disabled="!selectedRecordId || publishing" @click="submitPublish">
            <Loader2 v-if="publishing" class="w-4 h-4 mr-2 animate-spin" />
            {{ $t('gallery.publish') || '发布' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- ── Confirm Dialog ── -->
    <Dialog :open="confirmDialogOpen" @update:open="confirmDialogOpen = $event">
      <DialogContent class="max-w-sm">
        <DialogHeader>
          <DialogTitle>
            <template v-if="confirmAction === 'unpublish'">{{ $t('gallery.confirmUnpublish') || '确认取消发布？' }}</template>
            <template v-else>{{ $t('gallery.confirmAdminRemove') || '确认下架？' }}</template>
          </DialogTitle>
          <DialogDescription>
            <template v-if="confirmAction === 'unpublish'">{{ $t('gallery.confirmUnpublishDesc') || '该作品将从画廊移除，但不会删除你的历史记录。' }}</template>
            <template v-else>{{ $t('gallery.confirmAdminRemoveDesc') || '该作品将从画廊下架，用户的个人历史记录不受影响。' }}</template>
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" @click="confirmDialogOpen = false">{{ $t('gallery.cancel') || '取消' }}</Button>
          <Button variant="destructive" @click="executeConfirm">
            <Trash2 class="w-4 h-4 mr-1" />
            {{ confirmAction === 'unpublish' ? ($t('gallery.unpublish') || '取消发布') : ($t('gallery.adminRemove') || '下架') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>