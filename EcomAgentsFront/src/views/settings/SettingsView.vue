<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useMessage, useDialog } from 'naive-ui'
import { useAuthStore } from '../../stores/auth'
import { useThemeStore } from '../../stores/theme'

const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const themeStore = useThemeStore()

function handleLogout() {
  dialog.warning({
    title: '确认退出',
    content: '确定要退出登录吗？',
    positiveText: '退出',
    negativeText: '取消',
    onPositiveClick: () => {
      auth.logout()
      message.success('已退出')
      router.push({ name: 'Login' })
    },
  })
}
</script>

<template>
  <n-space vertical size="large">
    <!-- Profile -->
    <n-card title="个人信息" :bordered="true" style="max-width: 640px;">
      <n-descriptions label-placement="left" :column="1">
        <n-descriptions-item label="用户名">
          {{ auth.currentUser?.username || '-' }}
        </n-descriptions-item>
        <n-descriptions-item label="邮箱">
          {{ auth.currentUser?.email || '-' }}
        </n-descriptions-item>
        <n-descriptions-item label="角色">
          <n-tag :type="auth.isAdmin ? 'warning' : 'default'" :bordered="false" size="small">
            {{ auth.isAdmin ? '管理员' : '普通用户' }}
          </n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="状态">
          <n-tag :type="auth.currentUser?.status === 'active' ? 'success' : 'error'" :bordered="false" size="small">
            {{ auth.currentUser?.status === 'active' ? '正常' : '已禁用' }}
          </n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="注册时间">
          {{ auth.currentUser?.createdAt ? new Date(auth.currentUser.createdAt).toLocaleDateString('zh-CN') : '-' }}
        </n-descriptions-item>
      </n-descriptions>
    </n-card>

    <!-- Appearance -->
    <n-card title="外观" :bordered="true" style="max-width: 640px;">
      <n-space align="center" justify="space-between">
        <div>
          <n-text>深色模式</n-text>
          <br>
          <n-text depth="3" style="font-size: 13px;">切换界面主题色</n-text>
        </div>
        <n-switch
          :value="themeStore.isDark"
          @update:value="themeStore.setDark"
        >
          <template #checked>
            <n-icon>
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" width="14" height="14">
                <path d="M12 3a9 9 0 109 9c0-.46-.04-.92-.1-1.36a5.389 5.389 0 01-4.4 2.26 5.403 5.403 0 01-3.14-9.8c-.44-.06-.9-.1-1.36-.1z"/>
              </svg>
            </n-icon>
          </template>
          <template #unchecked>
            <n-icon>
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" width="14" height="14">
                <path d="M12 7c-2.76 0-5 2.24-5 5s2.24 5 5 5 5-2.24 5-5-2.24-5-5-5zM2 13h2c.55 0 1-.45 1-1s-.45-1-1-1H2c-.55 0-1 .45-1 1s.45 1 1 1zm18 0h2c.55 0 1-.45 1-1s-.45-1-1-1h-2c-.55 0-1 .45-1 1s.45 1 1 1zM11 2v2c0 .55.45 1 1 1s1-.45 1-1V2c0-.55-.45-1-1-1s-1 .45-1 1zm0 18v2c0 .55.45 1 1 1s1-.45 1-1v-2c0-.55-.45-1-1-1s-1 .45-1 1zM5.99 4.58a.996.996 0 00-1.41 0 .996.996 0 000 1.41l1.06 1.06c.39.39 1.03.39 1.41 0s.39-1.03 0-1.41L5.99 4.58zm12.37 12.37a.996.996 0 00-1.41 0 .996.996 0 000 1.41l1.06 1.06c.39.39 1.03.39 1.41 0a.996.996 0 000-1.41l-1.06-1.06zm1.06-10.96a.996.996 0 000-1.41.996.996 0 00-1.41 0l-1.06 1.06c-.39.39-.39 1.03 0 1.41s1.03.39 1.41 0l1.06-1.06zM7.05 18.36a.996.996 0 000-1.41.996.996 0 00-1.41 0l-1.06 1.06c-.39.39-.39 1.03 0 1.41s1.03.39 1.41 0l1.06-1.06z"/>
              </svg>
            </n-icon>
          </template>
        </n-switch>
      </n-space>
    </n-card>

    <!-- Account actions -->
    <n-card :bordered="true" style="max-width: 640px;">
      <n-space align="center" justify="space-between">
        <div>
          <n-text>退出登录</n-text>
          <br>
          <n-text depth="3" style="font-size: 13px;">退出当前账号</n-text>
        </div>
        <n-button type="error" @click="handleLogout">
          <template #icon>
            <n-icon>
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                <path d="M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8v-2H4V5z"/>
              </svg>
            </n-icon>
          </template>
          退出登录
        </n-button>
      </n-space>
    </n-card>
  </n-space>
</template>
