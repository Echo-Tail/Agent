<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import { ref } from 'vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { RotateCcw, X } from 'lucide-vue-next'

const props = withDefaults(defineProps<{
  open: boolean
  src: string
  alt?: string
  minZoom?: number
  maxZoom?: number
}>(), {
  alt: 'Image preview',
  minZoom: 0.5,
  maxZoom: 5,
})

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const zoom = ref(1)
const panX = ref(0)
const panY = ref(0)
const dragging = ref(false)
let dragStartX = 0
let dragStartY = 0
let dragOriginX = 0
let dragOriginY = 0

watch(() => [props.open, props.src], () => resetView())

function close() {
  dragging.value = false
  emit('update:open', false)
}

function handleWheel(event: WheelEvent) {
  const delta = event.deltaY > 0 ? -0.1 : 0.1
  zoom.value = Math.max(props.minZoom, Math.min(props.maxZoom, zoom.value + delta))
}

function startDrag(event: PointerEvent) {
  if (event.button !== 0) return
  dragging.value = true
  dragStartX = event.clientX
  dragStartY = event.clientY
  dragOriginX = panX.value
  dragOriginY = panY.value
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}

function moveDrag(event: PointerEvent) {
  if (!dragging.value) return
  panX.value = dragOriginX + event.clientX - dragStartX
  panY.value = dragOriginY + event.clientY - dragStartY
}

function stopDrag(event?: PointerEvent) {
  dragging.value = false
  if (event?.currentTarget instanceof HTMLElement && event.currentTarget.hasPointerCapture(event.pointerId)) {
    event.currentTarget.releasePointerCapture(event.pointerId)
  }
}

function resetView() {
  zoom.value = 1
  panX.value = 0
  panY.value = 0
  dragging.value = false
}

function handleKeydown(event: KeyboardEvent) {
  if (props.open && event.key === 'Escape') close()
}

window.addEventListener('keydown', handleKeydown)
onBeforeUnmount(() => window.removeEventListener('keydown', handleKeydown))
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-[100] flex items-center justify-center overflow-hidden bg-black/95"
      role="dialog"
      aria-modal="true"
      @click="close"
      @wheel.prevent="handleWheel"
    >
      <Button
        variant="ghost"
        size="icon"
        class="absolute right-4 top-4 z-10 rounded-full bg-black/50 text-white hover:bg-black/70"
        title="关闭"
        @click.stop="close"
      >
        <X class="h-5 w-5" />
      </Button>

      <div class="absolute left-4 top-4 z-10 flex items-center gap-2">
        <Badge variant="outline" class="border-white/20 bg-black/50 text-xs text-white">
          {{ Math.round(zoom * 100) }}%
        </Badge>
        <Button
          variant="outline"
          size="icon"
          class="h-7 w-7 border-white/20 bg-black/50 text-white"
          title="重置缩放和位置"
          @click.stop="resetView"
        >
          <RotateCcw class="h-3.5 w-3.5" />
        </Button>
      </div>

      <img
        v-if="src"
        :src="src"
        :alt="alt"
        :class="[
          'max-h-[90vh] max-w-[90vw] select-none touch-none object-contain',
          dragging ? 'cursor-grabbing' : 'cursor-grab transition-transform duration-100',
        ]"
        :style="{ transform: `translate(${panX}px, ${panY}px) scale(${zoom})` }"
        draggable="false"
        @click.stop
        @pointerdown.stop="startDrag"
        @pointermove.stop="moveDrag"
        @pointerup.stop="stopDrag"
        @pointercancel.stop="stopDrag"
      />
    </div>
  </Teleport>
</template>