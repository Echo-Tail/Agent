<script setup lang="ts">
import { ref, watchEffect } from 'vue'
import { Button } from '@/components/ui/button'
import { toast } from 'sonner'
import { analyzeImageExpression } from '@/api/image'
import { Loader2, RefreshCw, Trash2 } from 'lucide-vue-next'

const emit = defineEmits<{
  done: [expressionJson: string]
}>()

const collectedImages = ref<string[]>([])
const selectedImage = ref<string | null>(null)
const expressionJson = ref('')
const analyzing = ref(false)

// Props for parent to pass collected images
const props = defineProps<{
  images?: string[]
  initialExpression?: string
}>()

// Initialize expression from saved step data (back navigation)
if (props.initialExpression) {
  expressionJson.value = props.initialExpression
}

watchEffect(() => {
  if (props.images && props.images.length > 0) {
    collectedImages.value = props.images
  }
})

async function analyzeImage() {
  if (!selectedImage.value) return
  analyzing.value = true
  try {
    const res = await analyzeImageExpression(selectedImage.value)
    expressionJson.value = res
    if (!res || res === '{"error": "analysis failed"}') {
      toast.warning('视觉分析未返回有效结果，使用默认结构')
      expressionJson.value = defaultExpression()
    }
  } finally {
    analyzing.value = false
  }
}

function defaultExpression() {
  return JSON.stringify({
    intended_use: "Amazon US car stereo image (default)",
    image_type: "feature_infographic",
    scene: { background: "clean studio background", environment: "studio", lighting: "bright studio lighting", mood: "professional" },
    subject: { main_subject_role: "car stereo", subject_placement: "center" },
    composition: { framing: "close-up", viewpoint: "front-facing" },
    visual_style: { medium: "photorealistic product image", color_palette: "product-accurate", polish_level: "retail-ready" },
    copy_structure: { headline: "", feature_labels: [], body_copy_pattern: "" },
    risk_notes: [],
    reference_image_roles: [],
  }, null, 2)
}

function clearAnalysis() {
  expressionJson.value = ''
  selectedImage.value = null
}

function confirmDone() {
  if (expressionJson.value.trim()) {
    emit('done', expressionJson.value)
  }
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
    <p class="text-sm text-muted-foreground">从素材图片中选择一张，系统将分析其构图、风格和文案结构。</p>

    <div v-if="collectedImages.length === 0" class="py-8 text-center text-sm text-muted-foreground rounded-md border border-dashed">
      暂无素材图片。请先在「添加素材来源」步骤中添加 ASIN 或图片 URL。
    </div>

    <template v-else>
      <div class="grid gap-3 sm:grid-cols-3 md:grid-cols-4">
        <button
          v-for="(img, i) in collectedImages"
          :key="i"
          :class="[
            'overflow-hidden rounded-md border bg-card transition-colors',
            selectedImage === img ? 'ring-2 ring-primary' : 'hover:bg-accent/50',
          ]"
          @click="selectedImage = img"
        >
          <div class="aspect-square">
            <img :src="imageUrl(img)" alt="素材" class="h-full w-full object-cover" />
          </div>
          <div class="p-1 text-center text-xs text-muted-foreground truncate">#{{ i + 1 }}</div>
        </button>
      </div>

      <div class="flex gap-2">
        <Button :disabled="!selectedImage || analyzing" @click="analyzeImage">
          <Loader2 v-if="analyzing" class="h-4 w-4 mr-1 animate-spin" />
          <RefreshCw v-else class="h-4 w-4 mr-1" />分析图片表达
        </Button>
        <Button v-if="expressionJson" variant="outline" size="sm" @click="clearAnalysis">
          <Trash2 class="h-4 w-4 mr-1" />清除分析结果
        </Button>
      </div>

      <div v-if="expressionJson" class="space-y-3">
        <div class="rounded-md border bg-muted/20 p-3">
          <label class="text-sm font-medium">图片表达结构（只读）</label>
          <p class="text-xs text-muted-foreground mb-2">此结果为 LLM 分析所得，不支持直接编辑。如需调整请在最终 prompt 步骤修改。</p>
          <pre class="rounded bg-muted p-2 text-xs overflow-auto max-h-80">{{ expressionJson }}</pre>
        </div>

        <div class="flex justify-end pt-2">
          <Button @click="confirmDone">确认使用此表达结构</Button>
        </div>
      </div>
    </template>
  </div>
</template>
