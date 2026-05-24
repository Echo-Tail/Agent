<script setup lang="ts">
import { computed } from 'vue'
import { AGENT_ICON_MAP } from '@/constants/agentIcons'
import { Bot } from 'lucide-vue-next'

const props = defineProps<{
  icon: string
  class?: string
}>()

const isImageUrl = computed(() => props.icon.startsWith('/'))
const IconComponent = computed(() => AGENT_ICON_MAP[props.icon] || Bot)
</script>

<template>
  <img
    v-if="isImageUrl"
    :src="icon"
    class="h-full w-full rounded-full object-cover"
  />
  <component :is="IconComponent" v-else :class="$props.class" />
</template>
