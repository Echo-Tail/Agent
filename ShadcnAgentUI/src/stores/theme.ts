import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { STORAGE_KEY_THEME } from '@/constants'

export const useThemeStore = defineStore('theme', () => {
  const theme = ref<string>(localStorage.getItem(STORAGE_KEY_THEME) || 'light')

  const isDark = computed(() => theme.value === 'dark')

  function setDark(v: boolean) {
    theme.value = v ? 'dark' : 'light'
    localStorage.setItem(STORAGE_KEY_THEME, theme.value)
    applyTheme()
  }

  function toggle() {
    setDark(!isDark.value)
  }

  function applyTheme() {
    if (theme.value === 'dark') {
      document.documentElement.classList.add('dark')
    } else {
      document.documentElement.classList.remove('dark')
    }
  }

  function init() {
    applyTheme()
  }

  return { theme, isDark, setDark, toggle, init }
})
