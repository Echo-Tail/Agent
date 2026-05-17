<script setup lang="ts">
import { useThemeStore } from '../stores/theme'
const themeStore = useThemeStore()
</script>

<template>
  <div class="blank-layout" :class="{ 'blank-layout--dark': themeStore.isDark }">
    <router-view />
  </div>
</template>

<style scoped>
.blank-layout {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #3D3631 0%, #2A2623 50%, #1F1C1A 100%);
}

.blank-layout--dark {
  background: linear-gradient(135deg, #1F1C1A 0%, #161514 50%, #0D0C0B 100%);
}
</style>
