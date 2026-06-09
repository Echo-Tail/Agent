<script setup lang="ts">
import { inject } from 'vue'
import { cn } from '@/lib/utils'

interface CarouselItemProps {
  class?: string
}

const props = defineProps<CarouselItemProps>()

const carousel = inject<{
  orientation: string
}>('carousel')
</script>

<template>
  <div
    role="group"
    aria-roledescription="slide"
    :class="cn(
      'min-w-0 shrink-0 grow-0 basis-full',
      carousel?.orientation === 'horizontal' ? 'pl-4' : 'pt-4',
      props.class,
    )"
  >
    <slot />
  </div>
</template>
