import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN.json'
import en from './en.json'

const STORAGE_KEY = 'locale'

function getDefaultLocale(): string {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored) return stored
  const navLang = navigator.language
  if (navLang.startsWith('zh')) return 'zh-CN'
  return 'en'
}

const i18n = createI18n({
  legacy: false,
  locale: getDefaultLocale(),
  fallbackLocale: 'zh-CN',
  messages: { 'zh-CN': zhCN, en },
})

export function setLocale(locale: string) {
  i18n.global.locale.value = locale as 'zh-CN' | 'en'
  localStorage.setItem(STORAGE_KEY, locale)
}

export function getCurrentLocale(): string {
  return i18n.global.locale.value
}

export default i18n
