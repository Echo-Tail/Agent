<script setup lang="ts">
import { inject } from 'vue'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'

interface CarouselPreviousProps {
  class?: string
  variant?: 'default' | 'destructive' | 'outline' | 'secondary' | 'ghost' | 'link'
  size?: 'default' | 'sm' | 'lg' | 'icon'
}

const props = withDefaults(defineProps<CarouselPreviousProps>(), {
  variant: 'outline',
  size: 'icon',
})

const carousel = inject<{
  emblaApi: { value: { scrollPrev: () => void } | null }
  canScrollPrev: { value: boolean }
  orientation: string
}>('carousel')
</script>

<template>
  <Button
    :disabled="!carousel?.canScrollPrev.value"
    :variant="variant"
    :size="size"
    :class="cn(
      'absolute h-8 w-8 rounded-full',
      carousel?.orientation === 'horizontal'
        ? '-left-3 top-1/2 -translate-y-1/2'
        : '-top-3 left-1/2 -translate-x-1/2 rotate-90',
      props.class,
    )"
    @click="carousel?.emblaApi.value?.scrollPrev()"
  >
    <slot>
      <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="lucide lucide-chevron-left"><path d="m15 18-6-6 6-6"/></svg>
      <span class="sr-only">Previous slide</span>
    </slot>
  </Button>
</template>
