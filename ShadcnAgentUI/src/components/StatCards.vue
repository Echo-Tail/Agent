<script setup lang="ts">
import type { Component } from 'vue'
import { Card, CardContent } from '@/components/ui/card'

interface StatItem {
  label: string
  value: string | number
  icon?: Component
}

defineProps<{
  items: StatItem[]
  columns?: number
}>()
</script>

<template>
  <div
    class="grid gap-3"
    :style="columns ? { gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` } : {}"
    :class="{ 'md:grid-cols-4': !columns, 'md:grid-cols-5': columns === 5 }"
  >
    <Card v-for="item in items" :key="item.label">
      <CardContent class="p-4 flex items-center justify-between">
        <div>
          <p class="text-xs text-muted-foreground">{{ item.label }}</p>
          <p class="text-xl font-bold">{{ item.value }}</p>
        </div>
        <component :is="item.icon" v-if="item.icon" class="h-5 w-5 text-muted-foreground/50" />
      </CardContent>
    </Card>
  </div>
</template>
