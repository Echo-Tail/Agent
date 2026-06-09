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
    port: 5173,
    proxy: {
      '/v1': {
        target: 'http://localhost:8889',
        changeOrigin: true,
      },
      '/chat': {
        target: 'http://localhost:8889',
        changeOrigin: true,
      },
      '/uploads': {
        target: 'http://localhost:8889',
        changeOrigin: true,
      },
    },
  },
})
