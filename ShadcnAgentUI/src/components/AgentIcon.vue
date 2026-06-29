<script setup lang="ts">
import { computed } from 'vue'
import { AGENT_ICON_MAP } from '@/constants/agentIcons'
import { Bot } from 'lucide-vue-next'

const props = defineProps<{
  icon?: string
  avatar?: string
  class?: string
}>()

const IconComponent = computed(() => (props.icon && AGENT_ICON_MAP[props.icon]) ? AGENT_ICON_MAP[props.icon] : Bot)
</script>

<template>
  <img
    v-if="avatar"
    :src="avatar"
    alt="agent avatar"
    class="h-full w-full rounded-full object-cover"
  />
  <component :is="IconComponent" v-else :class="$props.class" />
</template>
