<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  ratio: string
}>()

const iconStyle = computed(() => {
  const [widthPart, heightPart] = props.ratio.split('/').map(part => Number(part.trim()))
  const ratio = widthPart > 0 && heightPart > 0 ? widthPart / heightPart : 1
  const maxWidth = 20
  const maxHeight = 16
  let width = maxWidth
  let height = maxWidth / ratio

  if (height > maxHeight) {
    height = maxHeight
    width = maxHeight * ratio
  }

  return {
    width: `${width}px`,
    height: `${height}px`,
  }
})
</script>

<template>
  <span class="aspect-ratio-icon" :style="iconStyle" aria-hidden="true" />
</template>

<style scoped>
.aspect-ratio-icon {
  display: inline-block;
  border: 1.75px solid currentColor;
  border-radius: 2px;
  opacity: 0.8;
}
</style>