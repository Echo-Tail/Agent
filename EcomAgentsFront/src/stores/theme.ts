import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { STORAGE_KEY_THEME } from '../constants'

export const useThemeStore = defineStore('theme', () => {
  const theme = ref<string>(localStorage.getItem(STORAGE_KEY_THEME) || 'light')

  const isDark = computed(() => theme.value === 'dark')

  function setDark(v: boolean) {
    theme.value = v ? 'dark' : 'light'
    localStorage.setItem(STORAGE_KEY_THEME, theme.value)
  }

  function toggle() {
    setDark(!isDark.value)
  }

  return { theme, isDark, setDark, toggle }
})
