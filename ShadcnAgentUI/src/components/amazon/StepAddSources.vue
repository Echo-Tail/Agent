<script setup lang="ts">
import { ref, onMounted } from 'vue'
import ImageLightbox from '@/components/ImageLightbox.vue'
import { useImageLightbox } from '@/composables/useImageLightbox'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import { toast } from 'sonner'
import { scrapeAsin } from '@/api/bright-data'
import { Loader2, Plus, X, Search } from 'lucide-vue-next'

interface SourceData {
  asin: string
  imageUrls: string[]
  brightDataJson: string
  scrapedImages: string[]
}

const { lightboxOpen, lightboxUrl, lightboxAlt, openLightbox } = useImageLightbox()

const props = defineProps<{
  initial?: SourceData
}>()

const emit = defineEmits<{
  done: [sources: SourceData]
}>()

const asin = ref(props.initial?.asin || '')
const imageUrlInput = ref('')
const imageUrls = ref<string[]>(props.initial?.imageUrls || [])
const brightDataJson = ref(props.initial?.brightDataJson || '')
const scrapedImages = ref<string[]>(props.initial?.scrapedImages || [])
const scraping = ref(false)

onMounted(() => {
  // Auto-scrape if ASIN provided and no scraped images yet
  if (asin.value && scrapedImages.value.length === 0) {
    doScrape()
  }
})

function addImageUrl() {
  const url = imageUrlInput.value.trim()
  if (url && !imageUrls.value.includes(url)) {
    imageUrls.value.push(url)
  }
  imageUrlInput.value = ''
}

function removeUrl(index: number) {
  imageUrls.value.splice(index, 1)
}

async function doScrape() {
  if (!asin.value.trim()) {
    toast.error('请先填写 ASIN')
    return
  }
  scraping.value = true
  try {
    const res = await scrapeAsin(asin.value.trim())
    if (res.recordId && res.message?.includes('in progress')) {
      toast.info('Bright Data 采集已提交，等待结果...')
      return
    }
    // Extract image URLs from response records
    const urls: string[] = []
    if (res.records) {
      for (const r of res.records) {
        if (r.images && Array.isArray(r.images)) {
          for (const img of r.images) {
            if (typeof img === 'string' && !urls.includes(img)) urls.push(img)
          }
        }
        if (r.image_url && !urls.includes(r.image_url)) urls.push(r.image_url)
        if (r.image && !urls.includes(r.image)) urls.push(r.image)
      }
    }
    if (urls.length > 0) {
      scrapedImages.value = urls
      toast.success(`已采集 ${urls.length} 张图片`)
    } else {
      toast.warning('未找到图片，可手动输入图片 URL')
    }
  } catch (e: any) {
    toast.error(e?.response?.data?.message || 'Bright Data 采集失败，可手动添加图片')
  } finally {
    scraping.value = false
  }
}

function confirmDone() {
  emit('done', {
    asin: asin.value.trim(),
    imageUrls: [...imageUrls.value],
    brightDataJson: brightDataJson.value.trim(),
    scrapedImages: [...scrapedImages.value],
  })
}

function imageUrl(path: string): string {
  if (!path) return ''
  if (/^https?:\/\//i.test(path) || path.startsWith('/uploads/')) return path
  return '/uploads/' + path.replace(/\\/g, '/').replace(/^\.\//, '')
}
</script>

<template>
  <div class="space-y-4">
    <p class="text-sm text-muted-foreground">添加素材来源：至少提供一个 ASIN 或一个/多个图片 URL。</p>

    <div class="rounded-md border bg-muted/20 p-4 space-y-3">
      <div class="flex items-end gap-2">
        <div class="flex-1">
          <label class="text-sm font-medium">Amazon ASIN</label>
          <Input v-model="asin" placeholder="B0XXXXXXXXXX" class="mt-1" />
        </div>
        <Button :disabled="!asin.trim() || scraping" @click="doScrape">
          <Loader2 v-if="scraping" class="h-4 w-4 mr-1 animate-spin" />
          <Search v-else class="h-4 w-4 mr-1" />采集图片
        </Button>
      </div>

      <div v-if="scrapedImages.length > 0" class="space-y-2">
        <label class="text-xs font-medium text-muted-foreground">采集到的图片（将自动进入下一步）</label>
        <div class="grid grid-cols-4 sm:grid-cols-6 gap-2">
          <div v-for="(url, i) in scrapedImages.slice(0, 12)" :key="i" class="aspect-square overflow-hidden rounded border">
            <img :src="imageUrl(url)" class="h-full w-full cursor-zoom-in object-cover" :alt="`img ${i+1}`" @click="openLightbox(imageUrl(url))" />
          </div>
          <div v-if="scrapedImages.length > 12" class="flex items-center justify-center text-xs text-muted-foreground border rounded">
            +{{ scrapedImages.length - 12 }}
          </div>
        </div>
      </div>
    </div>

    <div class="rounded-md border bg-muted/20 p-4 space-y-3">
      <label class="text-sm font-medium">图片 URL <span class="text-muted-foreground text-xs">可选，多个</span></label>
      <div class="flex gap-2">
        <Input v-model="imageUrlInput" placeholder="https://images.example.com/..." @keyup.enter="addImageUrl" />
        <Button variant="outline" size="sm" @click="addImageUrl"><Plus class="h-4 w-4" /></Button>
      </div>
      <div v-if="imageUrls.length > 0" class="space-y-1">
        <div v-for="(url, i) in imageUrls" :key="i" class="flex items-center gap-2 text-xs">
          <Badge variant="secondary" class="shrink-0">#{{ i + 1 }}</Badge>
          <span class="truncate flex-1">{{ url }}</span>
          <Button variant="ghost" size="icon" class="h-5 w-5" @click="removeUrl(i)"><X class="h-3 w-3" /></Button>
        </div>
      </div>
    </div>

    <details class="rounded-md border bg-muted/10">
      <summary class="cursor-pointer px-4 py-2 text-sm font-medium text-muted-foreground">Bright Data JSON（调试用）</summary>
      <div class="p-4">
        <Textarea v-model="brightDataJson" class="min-h-[120px] font-mono text-xs" placeholder='[{"asin":"B0...","title":"..."}]' />
      </div>
    </details>

    <div class="flex justify-end pt-2">
      <Button :disabled="!asin.trim() && imageUrls.length === 0" @click="confirmDone">确认素材来源</Button>
    </div>
  </div>
  <ImageLightbox v-model:open="lightboxOpen" :src="lightboxUrl" :alt="lightboxAlt" />
</template>
