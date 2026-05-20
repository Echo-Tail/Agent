<script setup lang="ts">
import { ref, computed, provide } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useMessage, useDialog } from 'naive-ui'
import { useAuthStore } from '../stores/auth'
import { useThemeStore } from '../stores/theme'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const themeStore = useThemeStore()

provide('message', message)
provide('dialog', dialog)

const collapsed = ref(false)

const pageTitles: Record<string, string> = {
  Dashboard: '工作台',
  AgentList: '我的Agent',
  AgentCreate: '创建Agent',
  Chat: '对话',
  History: '历史会话',
  UserManage: '用户管理',
  ModelManage: '模型管理',
  ToolManage: '工具管理',
  SkillManage: '技能管理',
  TokenUsage: 'Token 用量',
  KnowledgeBase: '知识库',
  Logs: '日志',
  Settings: '设置',
}

const currentTitle = computed(() => pageTitles[route.name as string] || 'EcomAgents')

interface MenuOption {
  label: string
  key: string
}

const menuOptions = computed<MenuOption[]>(() => {
  const items: MenuOption[] = [
    { label: '工作台', key: 'Dashboard' },
    { label: '对话', key: 'Chat' },
    { label: '我的Agent', key: 'AgentList' },
    { label: '历史会话', key: 'History' },
    { label: '知识库', key: 'KnowledgeBase' },
  ]
  if (auth.isAdmin) {
    items.push(
      { label: 'Token 用量', key: 'TokenUsage' },
      { label: '日志', key: 'Logs' },
      { label: '用户管理', key: 'UserManage' },
      { label: '模型管理', key: 'ModelManage' },
      { label: '工具管理', key: 'ToolManage' },
      { label: '技能管理', key: 'SkillManage' },
    )
  }
  items.push({ label: '设置', key: 'Settings' })
  return items
})

const activeKey = computed(() => (route.name as string) || 'Dashboard')

function handleMenuUpdate(key: string) {
  router.push({ name: key })
}

function handleLogout() {
  dialog.warning({
    title: '确认退出',
    content: '确定要退出登录吗？',
    positiveText: '退出',
    negativeText: '取消',
    onPositiveClick: () => {
      auth.logout()
      router.push({ name: 'Login' })
    },
  })
}
</script>

<template>
  <n-layout position="absolute" style="height: 100vh">
    <n-layout has-sider position="absolute">
      <n-layout-sider
        bordered
        collapse-mode="width"
        :collapsed-width="64"
        :width="240"
        :collapsed="collapsed"
        show-trigger="bar"
        @collapse="collapsed = true"
        @expand="collapsed = false"
        :native-scrollbar="false"
        :style="'background: var(--sidebar-bg, #3D3631);'"
      >
        <div class="sidebar-header">
          <n-icon size="28" color="#fff">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/>
            </svg>
          </n-icon>
          <span v-show="!collapsed" class="sidebar-title">EcomAgents</span>
        </div>

        <n-menu
          :value="activeKey"
          :options="menuOptions"
          :collapsed="collapsed"
          :collapsed-width="64"
          :collapsed-icon-size="22"
          @update:value="handleMenuUpdate"
          style="background: transparent;"
        />

        <div class="sidebar-footer">
          <n-space v-if="!collapsed" style="flex: 1; min-width: 0;">
            <n-ellipsis>
              <n-text style="color: rgba(255,255,255,0.85); font-size: 13px;">
                {{ auth.currentUser?.username || '用户' }}
              </n-text>
            </n-ellipsis>
          </n-space>
          <n-button
            quaternary
            circle
            size="small"
            @click="themeStore.toggle()"
            style="color: rgba(255,255,255,0.7);"
            :title="themeStore.isDark ? '切换亮色模式' : '切换暗色模式'"
          >
            <template #icon>
              <n-icon>
                <svg v-if="themeStore.isDark" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 7c-2.76 0-5 2.24-5 5s2.24 5 5 5 5-2.24 5-5-2.24-5-5-5zM2 13h2c.55 0 1-.45 1-1s-.45-1-1-1H2c-.55 0-1 .45-1 1s.45 1 1 1zm18 0h2c.55 0 1-.45 1-1s-.45-1-1-1h-2c-.55 0-1 .45-1 1s.45 1 1 1zM11 2v2c0 .55.45 1 1 1s1-.45 1-1V2c0-.55-.45-1-1-1s-1 .45-1 1zm0 18v2c0 .55.45 1 1 1s1-.45 1-1v-2c0-.55-.45-1-1-1s-1 .45-1 1zM5.99 4.58a.996.996 0 00-1.41 0 .996.996 0 000 1.41l1.06 1.06c.39.39 1.03.39 1.41 0s.39-1.03 0-1.41L5.99 4.58zm12.37 12.37a.996.996 0 00-1.41 0 .996.996 0 000 1.41l1.06 1.06c.39.39 1.03.39 1.41 0a.996.996 0 000-1.41l-1.06-1.06zm1.06-10.96a.996.996 0 000-1.41.996.996 0 00-1.41 0l-1.06 1.06c-.39.39-.39 1.03 0 1.41s1.03.39 1.41 0l1.06-1.06zM7.05 18.36a.996.996 0 000-1.41.996.996 0 00-1.41 0l-1.06 1.06c-.39.39-.39 1.03 0 1.41s1.03.39 1.41 0l1.06-1.06z"/>
                </svg>
                <svg v-else xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 3a9 9 0 109 9c0-.46-.04-.92-.1-1.36a5.389 5.389 0 01-4.4 2.26 5.403 5.403 0 01-3.14-9.8c-.44-.06-.9-.1-1.36-.1z"/>
                </svg>
              </n-icon>
            </template>
          </n-button>
          <n-button
            quaternary
            circle
            size="small"
            @click="handleLogout"
            style="color: rgba(255,255,255,0.7);"
          >
            <template #icon>
              <n-icon>
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8v-2H4V5z"/>
                </svg>
              </n-icon>
            </template>
          </n-button>
        </div>
      </n-layout-sider>

      <n-layout-content>
        <div class="topbar">
          <n-h3 style="margin: 0;">{{ currentTitle }}</n-h3>
        </div>
        <div class="content-area">
          <router-view :key="route.fullPath" />
        </div>
      </n-layout-content>
    </n-layout>
  </n-layout>
</template>

<style scoped>
.sidebar-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 16px;
}

.sidebar-title {
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  white-space: nowrap;
}

.sidebar-footer {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.12);
}

.topbar {
  padding: 16px 24px;
  border-bottom: 1px solid var(--border-color, #eee);
  background: var(--card-bg, #fff);
}

.content-area {
  padding: 24px;
  min-height: calc(100vh - 80px);
  background: var(--bg-color, #f5f7fa);
  position: relative;
}
</style>
