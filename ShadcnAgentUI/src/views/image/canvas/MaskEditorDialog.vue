<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Brush, Eraser, Minus, Redo2, Square, Undo2, X } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'

type Tool = 'paint' | 'erase' | 'rectangle' | 'line'
type Point = { x: number; y: number }

const props = defineProps<{ imageUrl: string }>()
const emit = defineEmits<{ close: []; apply: [image: File, mode: 'annotation' | 'mask'] }>()

const imageRef = ref<HTMLImageElement>()
const canvasRef = ref<HTMLCanvasElement>()
const tool = ref<Tool>('paint')
const brushSize = ref(10)
const drawing = ref(false)
const start = ref<Point>()
const last = ref<Point>()
const gestureBase = ref<ImageData>()
const history = ref<ImageData[]>([])
const historyIndex = ref(-1)
const coverage = ref(0)
const maskMode = ref(false)
const editorTitle = computed(() => maskMode.value ? '局部修改蒙版' : '局部标注编辑')
const editorDescription = computed(() => maskMode.value
  ? '红色区域将转换为透明 Mask，模型只重新生成标记区域'
  : '画笔、矩形和直线会合并到原图，作为新的参考图继续创作')
const coverageLabel = computed(() => maskMode.value ? '蒙版覆盖' : '标注覆盖')
watch(maskMode, enabled => {
  brushSize.value = enabled ? 50 : 10
})
const canUndo = computed(() => historyIndex.value > 0)
const canRedo = computed(() => historyIndex.value < history.value.length - 1)

function configureCanvas() {
  const image = imageRef.value
  const canvas = canvasRef.value
  if (!image || !canvas) return
  canvas.width = image.naturalWidth
  canvas.height = image.naturalHeight
  history.value = [canvas.getContext('2d', { willReadFrequently: true })!.getImageData(0, 0, canvas.width, canvas.height)]
  historyIndex.value = 0
}

function point(event: PointerEvent): Point {
  const canvas = canvasRef.value!
  const rect = canvas.getBoundingClientRect()
  return {
    x: (event.clientX - rect.left) * canvas.width / rect.width,
    y: (event.clientY - rect.top) * canvas.height / rect.height,
  }
}

function configureStroke(context: CanvasRenderingContext2D) {
  context.lineWidth = brushSize.value
  context.lineCap = 'round'
  context.lineJoin = 'round'
  context.strokeStyle = 'rgba(239, 68, 68, .72)'
  context.fillStyle = 'rgba(239, 68, 68, .72)'
  context.globalCompositeOperation = tool.value === 'erase' ? 'destination-out' : 'source-over'
}

function drawDot(at: Point) {
  const context = canvasRef.value!.getContext('2d')!
  context.save()
  configureStroke(context)
  context.beginPath()
  context.arc(at.x, at.y, brushSize.value / 2, 0, Math.PI * 2)
  context.fill()
  context.restore()
}

function drawLine(to: Point) {
  if (!last.value) return
  const context = canvasRef.value!.getContext('2d')!
  context.save()
  configureStroke(context)
  context.beginPath()
  context.moveTo(last.value.x, last.value.y)
  context.lineTo(to.x, to.y)
  context.stroke()
  context.restore()
  last.value = to
}

function drawShape(to: Point, constrain = false) {
  const canvas = canvasRef.value
  if (!canvas || !start.value || !gestureBase.value) return
  const context = canvas.getContext('2d')!
  context.putImageData(gestureBase.value, 0, 0)
  context.save()
  configureStroke(context)
  if (tool.value === 'rectangle') {
    let width = to.x - start.value.x
    let height = to.y - start.value.y
    if (constrain) {
      const side = Math.max(Math.abs(width), Math.abs(height))
      width = Math.sign(width || 1) * side
      height = Math.sign(height || 1) * side
    }
    context.fillRect(start.value.x, start.value.y, width, height)
  } else {
    let end = to
    if (constrain) {
      const dx = to.x - start.value.x
      const dy = to.y - start.value.y
      const length = Math.hypot(dx, dy)
      const angle = Math.round(Math.atan2(dy, dx) / (Math.PI / 4)) * Math.PI / 4
      end = { x: start.value.x + Math.cos(angle) * length, y: start.value.y + Math.sin(angle) * length }
    }
    context.beginPath()
    context.moveTo(start.value.x, start.value.y)
    context.lineTo(end.x, end.y)
    context.stroke()
  }
  context.restore()
}

function pointerDown(event: PointerEvent) {
  const canvas = canvasRef.value!
  drawing.value = true
  start.value = point(event)
  last.value = start.value
  gestureBase.value = canvas.getContext('2d')!.getImageData(0, 0, canvas.width, canvas.height)
  canvas.setPointerCapture(event.pointerId)
  if (tool.value === 'paint' || tool.value === 'erase') drawDot(start.value)
}

function pointerMove(event: PointerEvent) {
  if (!drawing.value) return
  const current = point(event)
  if (tool.value === 'paint' || tool.value === 'erase') drawLine(current)
  else drawShape(current, event.shiftKey)
}

function pointerUp(event: PointerEvent) {
  if (!drawing.value) return
  if (tool.value === 'rectangle' || tool.value === 'line') drawShape(point(event), event.shiftKey)
  drawing.value = false
  start.value = undefined
  last.value = undefined
  gestureBase.value = undefined
  canvasRef.value?.releasePointerCapture(event.pointerId)
  commitHistory()
}

function commitHistory() {
  const canvas = canvasRef.value!
  const next = history.value.slice(0, historyIndex.value + 1)
  next.push(canvas.getContext('2d')!.getImageData(0, 0, canvas.width, canvas.height))
  history.value = next
  historyIndex.value = next.length - 1
  updateCoverage()
}

function restore(index: number) {
  const snapshot = history.value[index]
  const canvas = canvasRef.value
  if (!snapshot || !canvas) return
  const context = canvas.getContext('2d')!
  context.clearRect(0, 0, canvas.width, canvas.height)
  context.putImageData(snapshot, 0, 0)
  historyIndex.value = index
  updateCoverage()
}

function undo() { if (canUndo.value) restore(historyIndex.value - 1) }
function redo() { if (canRedo.value) restore(historyIndex.value + 1) }

function updateCoverage() {
  const canvas = canvasRef.value
  if (!canvas) return
  const pixels = canvas.getContext('2d')!.getImageData(0, 0, canvas.width, canvas.height).data
  let marked = 0
  for (let index = 3; index < pixels.length; index += 16) if (pixels[index] > 0) marked += 1
  coverage.value = Math.round(marked / (pixels.length / 16) * 100)
}

async function apply() {
  const source = canvasRef.value
  if (!source || coverage.value === 0) return
  const output = document.createElement('canvas')
  output.width = source.width
  output.height = source.height
  const context = output.getContext('2d')!
  if (maskMode.value) {
    // GPT Image uses the alpha channel: transparent pixels are regenerated.
    context.fillStyle = '#000'
    context.fillRect(0, 0, output.width, output.height)
    const sourcePixels = source.getContext('2d')!.getImageData(0, 0, source.width, source.height).data
    const result = context.getImageData(0, 0, output.width, output.height)
    for (let index = 0; index < result.data.length; index += 4) {
      if (sourcePixels[index + 3] > 0) result.data[index + 3] = 0
    }
    context.putImageData(result, 0, 0)
  } else {
    const image = imageRef.value
    if (!image) return
    context.drawImage(image, 0, 0, output.width, output.height)
    context.drawImage(source, 0, 0)
  }
  const blob = await new Promise<Blob | null>(resolve => output.toBlob(resolve, 'image/png'))
  if (blob) {
    const mode = maskMode.value ? 'mask' : 'annotation'
    emit('apply', new File([blob], `${mode}.png`, { type: 'image/png' }), mode)
  }
}

function keydown(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'z') {
    event.preventDefault()
    event.shiftKey ? redo() : undo()
  }
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'y') {
    event.preventDefault()
    redo()
  }
}

onMounted(async () => {
  await nextTick()
  window.addEventListener('keydown', keydown)
})
onBeforeUnmount(() => window.removeEventListener('keydown', keydown))
</script>

<template>
  <div class="fixed inset-0 z-[80] flex flex-col bg-background" role="dialog" aria-modal="true" :aria-label="editorTitle">
    <header class="flex min-h-16 flex-wrap items-center justify-between gap-3 border-b px-4 py-2">
      <div>
        <strong class="block text-sm">{{ editorTitle }}</strong>
        <span class="text-xs text-muted-foreground">{{ editorDescription }}；按住 Shift 可约束正方形或直线角度</span>
      </div>
      <div class="flex flex-wrap items-center gap-1">
        <Button v-for="item in [
          { value: 'paint', label: '画笔', icon: Brush },
          { value: 'erase', label: '橡皮擦', icon: Eraser },
          { value: 'rectangle', label: '矩形', icon: Square },
          { value: 'line', label: '直线', icon: Minus },
        ]" :key="item.value" size="sm" :variant="tool === item.value ? 'secondary' : 'ghost'" @click="tool = item.value as Tool">
          <component :is="item.icon" class="mr-1 h-4 w-4" />{{ item.label }}
        </Button>
        <Button size="icon" variant="ghost" :disabled="!canUndo" title="撤销 Ctrl+Z" @click="undo"><Undo2 class="h-4 w-4" /></Button>
        <Button size="icon" variant="ghost" :disabled="!canRedo" title="重做 Ctrl+Y" @click="redo"><Redo2 class="h-4 w-4" /></Button>
        <label class="ml-2 flex items-center gap-2 text-xs">粗细
          <input v-model.number="brushSize" type="range" min="10" max="240" class="w-28" />
          <input v-model.number="brushSize" type="number" min="10" max="240" class="h-8 w-16 rounded border px-2" />px
        </label>
        <label class="ml-2 flex items-center gap-2 rounded-md border px-2 py-1.5 text-xs">
          <Switch v-model="maskMode" />
          作为 Mask
        </label>
      </div>
      <Button size="icon" variant="ghost" aria-label="关闭" @click="emit('close')"><X class="h-5 w-5" /></Button>
    </header>

    <main class="min-h-0 flex-1 overflow-hidden bg-muted/60 p-5">
      <div class="relative mx-auto h-full w-full">
        <div class="absolute inset-0 grid place-items-center">
          <div class="relative max-h-full max-w-full overflow-hidden rounded-lg shadow-xl ring-1 ring-black/10">
            <img ref="imageRef" :src="props.imageUrl" alt="蒙版基础图片" class="block max-h-[calc(100vh-9rem)] max-w-[calc(100vw-3rem)] object-contain" @load="configureCanvas" />
            <canvas ref="canvasRef" class="absolute inset-0 h-full w-full touch-none cursor-crosshair" @pointerdown="pointerDown" @pointermove="pointerMove" @pointerup="pointerUp" @pointercancel="pointerUp" />
          </div>
        </div>
      </div>
    </main>

    <footer class="flex min-h-16 items-center justify-between border-t px-5">
      <span class="text-xs text-muted-foreground">{{ coverageLabel }}约 {{ coverage }}%</span>
      <div class="flex gap-2">
        <Button variant="outline" @click="emit('close')">取消</Button>
        <Button :disabled="coverage === 0" @click="apply">应用并继续创作</Button>
      </div>
    </footer>
  </div>
</template>
