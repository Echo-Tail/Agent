import path from 'node:path'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5174,
    // Windows 下收窄文件监听范围，避免句柄泄漏导致 dev server 跑久了硬崩溃 (0xC0000409)
    watch: {
      ignored: ['**/node_modules/**', '**/dist/**', '**/.git/**'],
    },
    proxy: {
      '/v1': {
        target: 'http://localhost:8888',
        changeOrigin: true,
      },
      '/chat': {
        target: 'http://localhost:8888',
        changeOrigin: true,
      },
      '/uploads': {
        target: 'http://localhost:8888',
        changeOrigin: true,
      },
    },
  },
})
