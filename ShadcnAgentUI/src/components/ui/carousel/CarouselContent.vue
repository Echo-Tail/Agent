<script setup lang="ts">
import { inject, onMounted, type Ref } from 'vue'
import { cn } from '@/lib/utils'
import type { CarouselApi } from './Carousel.vue'

interface CarouselContentProps {
  class?: string
}

const props = defineProps<CarouselContentProps>()

const carousel = inject<{
  emblaApi: ReturnType<typeof import('embla-carousel-vue').default>[1]
  emblaNode: ReturnType<typeof import('embla-carousel-vue').default>[0]
  canScrollPrev: Ref<boolean>
  canScrollNext: Ref<boolean>
  orientation: string
  onSelect: (api: CarouselApi) => void
  onInit: (api: CarouselApi) => void
}>('carousel')

if (!carousel) throw new Error('CarouselContent must be used within a Carousel')

onMounted(() => {
  if (carousel.emblaApi.value) {
    carousel.onInit(carousel.emblaApi.value)
    carousel.emblaApi.value.on('select', carousel.onSelect)
    carousel.emblaApi.value.on('reInit', carousel.onSelect)
  }
})
</script>

<template>
  <div
    ref="carousel.emblaNode"
    :class="cn('overflow-hidden', props.class)"
  >
    <div
      class="flex"
      :class="carousel.orientation === 'vertical' ? 'flex-col' : ''"
    >
      <slot />
    </div>
  </div>
</template>
