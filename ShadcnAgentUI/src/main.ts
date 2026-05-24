import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { toast } from 'sonner'
import App from './App.vue'
import router from './router'
import i18n from './locales'
import './style.css'

const app = createApp(App)
const { t } = i18n.global

// Global error handler: catch Vue render / event errors
app.config.errorHandler = (err) => {
  toast.error(err instanceof Error ? err.message : t('error.unknownError'))
}

// Unhandled promise rejections (from code that doesn't have catch)
window.addEventListener('unhandledrejection', (event) => {
  const msg = event.reason instanceof Error ? event.reason.message : t('error.networkError')
  toast.error(msg)
})

app.use(createPinia())
app.use(router)
app.use(i18n)
app.mount('#app')
