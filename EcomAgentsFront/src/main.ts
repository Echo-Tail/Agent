import { createApp } from 'vue'
import { createPinia } from 'pinia'
import NaiveUI from 'naive-ui'
import router from './router'
import App from './App.vue'
import http from './api/request'
import { InMemoryStorage, IndexedDBStorage } from './utils/logging/storage'
import { initLogger } from './utils/logging/writer'
import { setupLogCollector } from './utils/logging/collector'

const app = createApp(App)

const pinia = createPinia()
app.use(pinia)
app.use(router)
app.use(NaiveUI)

// Always start with InMemoryStorage (can't hang), upgrade to IndexedDB in background
initLogger(new InMemoryStorage())
setupLogCollector(app, router, http)

// Try IndexedDB for persistence — if probe fails, stay with in-memory
;(async () => {
  try {
    const idb = new IndexedDBStorage()
    await idb.probe()
    initLogger(idb)
  } catch {
    // stay with in-memory, logs won't persist across refresh
  }
})()

// Auto-log Pinia actions via $onAction
pinia.use(({ store }) => {
  store.$onAction(({ name, store: storeInstance, args, onError }) => {
    const logAction = () => {
      try {
        const fn = app.config.globalProperties.$piniaStoreAction as unknown as
          (actionName: string, storeName: string, args: unknown[]) => void
        fn(name, storeInstance.$id, args)
      } catch { /* ignore */ }
    }
    logAction()
    onError((error) => {
      try {
        const fn = app.config.globalProperties.$piniaStoreAction as unknown as
          (actionName: string, storeName: string, args: unknown[], error: unknown) => void
        fn(name, storeInstance.$id, args, error)
      } catch { /* ignore */ }
    })
  })
})

app.mount('#app')
