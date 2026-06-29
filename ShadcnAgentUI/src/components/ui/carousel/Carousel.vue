<script setup lang="ts">
import { ref, provide } from 'vue'
import type { EmblaCarouselType, EmblaOptionsType, EmblaPluginType } from 'embla-carousel'
import useEmblaCarousel from 'embla-carousel-vue'
import { cn } from '@/lib/utils'

export type CarouselApi = EmblaCarouselType

interface CarouselProps {
  opts?: EmblaOptionsType
  plugins?: EmblaPluginType[]
  orientation?: 'horizontal' | 'vertical'
  class?: string
}

const props = withDefaults(defineProps<CarouselProps>(), {
  orientation: 'horizontal',
})

const emits = defineEmits<{
  (e: 'init', api: CarouselApi): void
}>()

const [emblaNode, emblaApi] = useEmblaCarousel(props.opts, props.plugins)

const canScrollPrev = ref(false)
const canScrollNext = ref(false)

function onSelect(api: CarouselApi) {
  canScrollPrev.value = api.canScrollPrev()
  canScrollNext.value = api.canScrollNext()
}

function onInit(api: CarouselApi) {
  canScrollPrev.value = api.canScrollPrev()
  canScrollNext.value = api.canScrollNext()
  emits('init', api)
}

provide('carousel', {
  emblaApi,
  emblaNode,
  canScrollPrev,
  canScrollNext,
  orientation: props.orientation,
  onSelect,
  onInit,
})
</script>

<template>
  <div
    :class="cn('relative', props.class)"
    role="region"
    aria-roledescription="carousel"
    :data-orientation="orientation"
  >
    <slot />
  </div>
</template>
