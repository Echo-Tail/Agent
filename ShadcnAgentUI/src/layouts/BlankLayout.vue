<script setup lang="ts">
import { useThemeStore } from '@/stores/theme'
const themeStore = useThemeStore()
</script>

<template>
  <div :class="['min-h-screen flex items-center justify-center bg-gradient-to-br from-neutral-900 via-neutral-800 to-neutral-950', { 'dark': themeStore.isDark }]">
    <router-view />
  </div>
</template>
