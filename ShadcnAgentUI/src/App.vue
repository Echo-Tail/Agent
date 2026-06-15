<script setup lang="ts">
import { onMounted } from 'vue'
import { useThemeStore } from '@/stores/theme'
import { Toaster } from '@/components/ui/sonner'

const themeStore = useThemeStore()

onMounted(() => {
  themeStore.init()
})
</script>

<template>
  <Toaster richColors position="top-center" />
  <router-view />
</template>
