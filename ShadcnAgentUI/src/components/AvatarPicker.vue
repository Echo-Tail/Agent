<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { AGENT_ICONS } from '@/constants/agentIcons'
import AgentIcon from '@/components/AgentIcon.vue'
import { Button } from '@/components/ui/button'
import { uploadFileApi } from '@/api/file'
import { toast } from 'sonner'
import { ImagePlus, Loader2, Check, Upload } from 'lucide-vue-next'

const { t } = useI18n()

const modelValue = defineModel<string>({ default: '' })

type Mode = 'icon' | 'upload'
const mode = ref<Mode>(!modelValue.value || modelValue.value.startsWith('/') ? 'upload' : 'icon')

const previewUrl = ref<string | null>(null)
const uploading = ref(false)
const uploadedUrl = ref<string | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)

const MAX_FILE_SIZE = 10 * 1024 * 1024

const hasValue = computed(() => !!modelValue.value)
const isIconMode = computed(() => mode.value === 'icon')

function switchMode(m: Mode) {
  mode.value = m
  if (m === 'icon' && modelValue.value && !modelValue.value.startsWith('/')) {
    // keep current icon key
  } else if (m === 'upload' && modelValue.value && modelValue.value.startsWith('/')) {
    uploadedUrl.value = modelValue.value
    previewUrl.value = null
  }
}

function selectIcon(key: string) {
  modelValue.value = key
}

function handleFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  if (file.size > MAX_FILE_SIZE) {
    toast.error(t('chat.fileTooLarge', { size: '10MB' }))
    input.value = ''
    return
  }

  if (!file.type.startsWith('image/')) {
    toast.error('请选择图片文件')
    input.value = ''
    return
  }

  const reader = new FileReader()
  reader.onload = () => {
    previewUrl.value = reader.result as string
    uploadedUrl.value = null
  }
  reader.readAsDataURL(file)
  input.value = ''
}

async function confirmUpload() {
  const img = new Image()
  img.onload = async () => {
    // Center-square crop then circle clip
    const size = Math.min(img.width, img.height)
    const sx = (img.width - size) / 2
    const sy = (img.height - size) / 2

    const outSize = 200
    const canvas = document.createElement('canvas')
    canvas.width = outSize
    canvas.height = outSize
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    ctx.beginPath()
    ctx.arc(outSize / 2, outSize / 2, outSize / 2, 0, Math.PI * 2)
    ctx.closePath()
    ctx.clip()
    ctx.drawImage(img, sx, sy, size, size, 0, 0, outSize, outSize)

    uploading.value = true
    try {
      const blob = await new Promise<Blob | null>((resolve) =>
        canvas.toBlob(resolve, 'image/png')
      )
      if (!blob) throw new Error('Canvas toBlob failed')

      const file = new File([blob], 'avatar.png', { type: 'image/png' })
      const record = await uploadFileApi(file)
      const url = `/v1/files/${record.id}/download`
      modelValue.value = url
      uploadedUrl.value = url
      previewUrl.value = null
      toast.success(t('toast.uploadSuccess'))
    } catch {
      toast.error(t('error.uploadFailed'))
    } finally {
      uploading.value = false
    }
  }
  if (previewUrl.value) {
    img.src = previewUrl.value
  }
}

function clearUpload() {
  previewUrl.value = null
  uploadedUrl.value = null
  modelValue.value = ''
}
</script>

<template>
  <div class="space-y-3">
    <!-- Mode toggle -->
    <div class="flex gap-2">
      <Button
        variant="outline"
        size="sm"
        :class="mode === 'icon' ? 'bg-primary text-primary-foreground hover:bg-primary/90' : ''"
        @click="switchMode('icon')"
      >
        {{ $t('agent.icon') }}
      </Button>
      <Button
        variant="outline"
        size="sm"
        :class="mode === 'upload' ? 'bg-primary text-primary-foreground hover:bg-primary/90' : ''"
        @click="switchMode('upload')"
      >
        <ImagePlus class="mr-1 h-3.5 w-3.5" /> 上传图片
      </Button>
    </div>

    <!-- Icon picker grid -->
    <div v-if="isIconMode" class="grid grid-cols-6 gap-2">
      <button
        v-for="icon in AGENT_ICONS"
        :key="icon.key"
        type="button"
        class="flex items-center justify-center h-10 w-10 rounded-full border-2 transition-all"
        :class="modelValue === icon.key
          ? 'border-primary bg-primary/10 text-primary'
          : 'border-border hover:border-muted-foreground/40 text-muted-foreground hover:text-foreground'"
        :title="icon.label"
        @click="selectIcon(icon.key)"
      >
        <AgentIcon :icon="icon.key" class="h-5 w-5" />
      </button>
    </div>

    <!-- Upload mode -->
    <div v-else class="space-y-3">
      <!-- Preview area -->
      <div class="flex items-center gap-4">
        <div
          class="flex h-20 w-20 items-center justify-center rounded-full bg-muted overflow-hidden shrink-0 border-2 border-dashed border-border"
        >
          <img
            v-if="uploadedUrl || previewUrl"
            :src="uploadedUrl || previewUrl!"
            class="h-full w-full object-cover"
          />
          <ImagePlus v-else class="h-8 w-8 text-muted-foreground/50" />
        </div>
        <div class="space-y-1.5">
          <input
            ref="fileInput"
            type="file"
            accept="image/png,image/jpeg,image/gif,image/webp"
            class="hidden"
            @change="handleFileSelect"
          />
          <Button
            v-if="!previewUrl && !uploadedUrl"
            variant="outline"
            size="sm"
            @click="fileInput?.click()"
          >
            <Upload class="mr-1 h-3.5 w-3.5" /> 选择图片
          </Button>
          <div v-else-if="previewUrl" class="flex gap-2">
            <Button size="sm" :disabled="uploading" @click="confirmUpload">
              <Loader2 v-if="uploading" class="mr-1 h-3.5 w-3.5 animate-spin" />
              <Check v-else class="mr-1 h-3.5 w-3.5" />
              确认
            </Button>
            <Button variant="outline" size="sm" @click="clearUpload">取消</Button>
          </div>
          <div v-else class="flex gap-2">
            <Button variant="outline" size="sm" @click="fileInput?.click()">更换图片</Button>
            <Button variant="outline" size="sm" @click="clearUpload">移除</Button>
          </div>
          <p class="text-xs text-muted-foreground">支持 PNG/JPG/GIF/WebP，最大 10MB，自动裁剪为圆形</p>
        </div>
      </div>
    </div>
  </div>
</template>
