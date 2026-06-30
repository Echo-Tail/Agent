<script setup lang="ts">
import type { Component } from 'vue'
import { FileArchive } from 'lucide-vue-next'

defineProps<{
  icon?: Component
  title: string
  description?: string
}>()
</script>

<template>
  <div class="text-center py-12">
    <component :is="icon || FileArchive" class="mx-auto h-12 w-12 text-muted-foreground/50" />
    <h3 class="mt-4 text-lg font-semibold">{{ title }}</h3>
    <p v-if="description" class="text-sm text-muted-foreground mt-1">{{ description }}</p>
    <div v-if="$slots.default" class="mt-4">
      <slot />
    </div>
  </div>
</template>
