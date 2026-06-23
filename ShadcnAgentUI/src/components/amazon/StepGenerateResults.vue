<script setup lang="ts">
import { ref } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import { Loader2, WandSparkles, CheckCircle, XCircle, ImageIcon } from 'lucide-vue-next'

const emit = defineEmits<{
  generate: [params: GenerateParams]
  markResult: [data: { resultId: number; status: string }]
}>()

export interface GenerateParams {
  size: string
  quality: string
  n: number
  modelId?: number
  referenceImages: File[]
}

interface ResultImage {
  id: number
  url: string
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED'
}

const props = defineProps<{
  generating?: boolean
  results?: ResultImage[]
}>()

const sizes = ['1024x1024', '1536x1024', '1024x1536', '3840x2160']
const qualities = ['low', 'medium', 'high', 'auto']

const size = ref('1024x1024')
const quality = ref('auto')
const count = ref(4)
const files = ref<File[]>([])

function handleFiles(event: Event) {
  const input = event.target as HTMLInputElement
  files.value = Array.from(input.files || [])
}

function handleGenerate() {
  emit('generate', {
    size: size.value,
    quality: quality.value,
    n: count.value,
    referenceImages: files.value,
  })
}

function imageUrl(path: string): string {
  if (!path) return ''
  if (/^https?:\/\//i.test(path) || path.startsWith('/uploads/')) return path
  const normalized = path.replace(/\\/g, '/').replace(/^\.\//, '')
  return '/uploads/' + normalized
}
</script>

<template>
  <div class="space-y-4">
    <div class="rounded-md border bg-muted/20 p-4 space-y-3">
      <label class="text-sm font-medium">生成参数</label>
      <div class="grid gap-3 md:grid-cols-4">
        <div>
          <label class="text-xs text-muted-foreground">尺寸</label>
          <Select v-model="size">
            <SelectTrigger><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem v-for="s in sizes" :key="s" :value="s">{{ s }}</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div>
          <label class="text-xs text-muted-foreground">质量</label>
          <Select v-model="quality">
            <SelectTrigger><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem v-for="q in qualities" :key="q" :value="q">{{ q }}</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div>
          <label class="text-xs text-muted-foreground">生成张数</label>
          <Input v-model.number="count" type="number" min="1" max="10" />
        </div>
        <div class="flex items-end">
          <Button :disabled="generating" @click="handleGenerate">
            <Loader2 v-if="generating" class="h-4 w-4 mr-1 animate-spin" />
            <WandSparkles v-else class="h-4 w-4 mr-1" />生成
          </Button>
        </div>
      </div>
      <div>
        <label class="text-xs font-medium">参考图（可选，上传后走图生图）</label>
        <Input class="mt-1" type="file" accept="image/*" multiple @change="handleFiles" />
      </div>
    </div>

    <div v-if="results && results.length > 0" class="space-y-3">
      <div class="text-sm font-medium">生成结果</div>
      <div class="grid gap-3 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-4">
        <div v-for="img in results" :key="img.id" class="group relative overflow-hidden rounded-md border bg-card">
          <div class="aspect-square">
            <img :src="imageUrl(img.url)" alt="生成图" class="h-full w-full object-cover" />
          </div>
          <div class="absolute top-2 right-2 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
            <Button
              variant="secondary"
              size="icon"
              :class="['h-7 w-7', img.status === 'ACCEPTED' ? 'ring-2 ring-green-500' : '']"
              :disabled="img.status === 'ACCEPTED'"
              @click="$emit('markResult', { resultId: img.id, status: 'ACCEPTED' })"
            >
              <CheckCircle class="h-4 w-4 text-green-600" />
            </Button>
            <Button
              variant="secondary"
              size="icon"
              :class="['h-7 w-7', img.status === 'REJECTED' ? 'ring-2 ring-red-500' : '']"
              :disabled="img.status === 'REJECTED'"
              @click="$emit('markResult', { resultId: img.id, status: 'REJECTED' })"
            >
              <XCircle class="h-4 w-4 text-red-600" />
            </Button>
          </div>
          <div class="absolute bottom-2 left-2">
            <Badge
              :class="{
                'bg-green-100 text-green-800': img.status === 'ACCEPTED',
                'bg-red-100 text-red-800': img.status === 'REJECTED',
                'bg-gray-100 text-gray-800': img.status === 'PENDING',
              }"
            >
              {{ img.status === 'ACCEPTED' ? '可用' : img.status === 'REJECTED' ? '弃用' : '未处理' }}
            </Badge>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="py-12 text-center text-sm text-muted-foreground rounded-md border border-dashed">
      <ImageIcon class="h-8 w-8 mx-auto mb-2 text-muted-foreground/50" />
      <p>暂无生成结果</p>
    </div>
  </div>
</template>
