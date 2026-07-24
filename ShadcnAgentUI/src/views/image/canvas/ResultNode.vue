<script setup lang="ts">
import { computed, ref } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { Brush, Copy, Download, FolderUp, ImageIcon, RefreshCw, Trash2, Upload, WandSparkles } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import ImageLightbox from '@/components/ImageLightbox.vue'
import type { ImageWorkflowNodeData } from '@/types/image-workflow'
import { validateWorkflowUpscale } from '@/utils/imageWorkflow'

const props = defineProps<{
  data: ImageWorkflowNodeData
  upscaling?: boolean
  canRegenerate?: boolean
}>()
const emit = defineEmits<{
  continue: []
  regenerate: []
  mask: []
  copyPrompt: []
  download: []
  saveAsset: []
  uploadPrompt: []
  upscale: [factor: number]
  dimensions: [width: number, height: number]
  delete: []
}>()
const upscaleError = computed(() => validateWorkflowUpscale(props.data, 2))
const previewOpen = ref(false)

function reportDimensions(event: Event) {
  const image = event.target as HTMLImageElement
  if (image.naturalWidth > 0 && image.naturalHeight > 0
    && (props.data.width !== image.naturalWidth || props.data.height !== image.naturalHeight)) {
    emit('dimensions', image.naturalWidth, image.naturalHeight)
  }
}
</script>

<template>
  <article class="relative w-[280px] overflow-hidden rounded-xl border bg-card shadow-sm">
    <Handle type="target" :position="Position.Left" class="!h-3 !w-3 !border-2 !border-background !bg-primary" />
    <Button
      type="button"
      variant="secondary"
      size="icon"
      class="nodrag absolute right-2 top-2 z-10 size-8 bg-background text-muted-foreground shadow-sm hover:text-destructive"
      aria-label="删除结果卡片"
      @click.stop="emit('delete')"
    >
      <Trash2 class="h-4 w-4" />
    </Button>
    <div class="grid aspect-square place-items-center border-b bg-muted/50">
      <img
        v-if="data.imageUrl"
        :src="data.imageUrl"
        :alt="data.title"
        class="h-full w-full cursor-zoom-in object-contain"
        @load="reportDimensions"
        @click.stop="previewOpen = true"
      />
      <ImageIcon v-else class="h-10 w-10 text-muted-foreground/40" />
    </div>
    <div class="p-4">
      <div class="flex min-w-0 items-center gap-1.5">
        <strong class="mr-auto min-w-0 truncate text-sm">{{ data.title }}</strong>
        <Badge
          v-if="data.width && data.height"
          variant="secondary"
          class="shrink-0 px-1.5 py-0 text-[10px] font-medium tabular-nums"
        >
          {{ data.width }}x{{ data.height }}
        </Badge>
        <Badge
          v-if="data.elapsedSeconds !== undefined"
          variant="outline"
          class="shrink-0 px-1.5 py-0 text-[10px] font-medium tabular-nums"
        >
          {{ data.elapsedSeconds }}s
        </Badge>
      </div>
      <p v-if="data.upscaleFactor" class="mt-1 text-xs text-muted-foreground">高清放大 {{ data.upscaleFactor }}×</p>
      <div class="nodrag mt-3 grid grid-cols-2 gap-2">
        <Button size="sm" @click="emit('continue')">
          <WandSparkles class="mr-1 h-3.5 w-3.5" />继续创作
        </Button>
        <Button size="sm" variant="outline" @click="emit('mask')">
          <Brush class="mr-1 h-3.5 w-3.5" />局部修改
        </Button>
        <Button size="sm" variant="ghost" class="text-muted-foreground" @click="emit('copyPrompt')">
          <Copy class="mr-1 h-3.5 w-3.5" />复制提示词
        </Button>
        <Button size="sm" variant="ghost" class="text-muted-foreground" @click="emit('saveAsset')">
          <FolderUp class="mr-1 h-3.5 w-3.5" />保存素材
        </Button>
        <Button size="sm" variant="ghost" class="text-muted-foreground" @click="emit('download')">
          <Download class="mr-1 h-3.5 w-3.5" />下载
        </Button>
        <Button size="sm" variant="ghost" class="text-muted-foreground" @click="emit('uploadPrompt')">
          <Upload class="mr-1 h-3.5 w-3.5" />上传提示词
        </Button>
        <Button v-if="canRegenerate" size="sm" variant="ghost" class="col-span-2" @click="emit('regenerate')">
          <RefreshCw class="mr-1 h-3.5 w-3.5" />重新生成
        </Button>
      </div>
      <div class="nodrag mt-3 flex items-center gap-1 border-t pt-3">
        <span class="mr-auto text-xs text-muted-foreground">高清放大</span>
        <Button
          v-for="factor in [2, 3, 4]"
          :key="factor"
          size="sm"
          variant="ghost"
          class="h-7 px-2 text-xs"
          :disabled="Boolean(upscaleError) || upscaling"
          :title="upscaleError || `放大 ${factor} 倍`"
          @click="emit('upscale', factor)"
        >
          {{ factor }}×
        </Button>
      </div>
    </div>
    <ImageLightbox v-model:open="previewOpen" :src="data.imageUrl ?? ''" :alt="data.title" />
    <Handle type="source" :position="Position.Right" class="!h-3 !w-3 !border-2 !border-background !bg-primary" />
  </article>
</template>
