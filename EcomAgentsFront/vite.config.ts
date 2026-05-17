import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 8080,
    proxy: {
      '/v1': {
        target: 'http://localhost:8888',
        changeOrigin: true,
      },
      '/chat': {
        target: 'http://localhost:8888',
        changeOrigin: true,
      },
    },
  },
})
