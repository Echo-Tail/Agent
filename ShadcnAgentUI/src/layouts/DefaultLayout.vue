<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { useUnreadStore } from '@/stores/unread'
import { useI18n } from 'vue-i18n'
import { setLocale, getCurrentLocale } from '@/locales'
import { Button } from '@/components/ui/button'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import {
  LayoutDashboard,
  MessageSquare,
  Users,
  History,
  Ticket,
  BookOpen,
  BarChart3,
  TicketCheck,
  FileText,
  UserCog,
  Brain,
  Wrench,
  GraduationCap,
  Settings,
  LogOut,
  Sun,
  Moon,
  Menu,
  ChevronLeft,
  Bot,
  Languages,
  MessageCircle,
  Mail,
  Image,
  ImagePlus,
} from 'lucide-vue-next'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const themeStore = useThemeStore()
const unread = useUnreadStore()
const { t } = useI18n()

const sidebarCollapsed = ref(false)
const mobileSidebarOpen = ref(false)
const currentLocale = ref(getCurrentLocale())

function toggleLocale() {
  const next = currentLocale.value === 'zh-CN' ? 'en' : 'zh-CN'
  setLocale(next)
  currentLocale.value = next
}

const pageTitleKey: Record<string, string> = {
  Dashboard: 'pageTitle.myAgents',
  AgentList: 'pageTitle.myAgents',
  AgentPlaza: 'pageTitle.agentPlaza',
  AgentCreate: 'pageTitle.createAgent',
  Chat: 'pageTitle.chat',
  GroupChat: 'pageTitle.groupChat',
  GroupChatDetail: 'pageTitle.groupChatDetail',
  Messages: 'pageTitle.messages',
  MessagesDetail: 'pageTitle.messagesDetail',
  History: 'pageTitle.history',
  MyTickets: 'pageTitle.myTickets',
  UserManage: 'pageTitle.userManage',
  ModelManage: 'pageTitle.modelManage',
  ToolManage: 'pageTitle.toolManage',
  SkillManage: 'pageTitle.skillManage',
  TokenUsage: 'pageTitle.tokenUsage',
  TicketManage: 'pageTitle.ticketManage',
  KnowledgeBase: 'pageTitle.knowledgeBase',
  PublicAssets: 'pageTitle.publicAssets',
  Logs: 'pageTitle.logs',
  Settings: 'pageTitle.settings',
}

const currentTitle = computed(() => {
  const key = pageTitleKey[route.name as string]
  return key ? t(key) : 'EcomAgents'
})

interface NavItem {
  translationKey: string
  key: string
  icon: any
  adminOnly?: boolean
}

// 分组导航 — 组之间显示分割线
const navGroups: NavItem[][] = [
  // 核心 AI 交互
  [
    { translationKey: 'nav.chat', key: 'Chat', icon: MessageSquare },
    { translationKey: 'nav.groupChat', key: 'GroupChat', icon: MessageCircle },
    { translationKey: 'nav.messages', key: 'Messages', icon: Mail },
    { translationKey: 'nav.myAgents', key: 'Dashboard', icon: LayoutDashboard },
    { translationKey: 'nav.agentPlaza', key: 'AgentPlaza', icon: Users },
    { translationKey: 'nav.imageGeneration', key: 'ImageGeneration', icon: Image },
    { translationKey: 'nav.publicAssets', key: 'PublicAssets', icon: ImagePlus },
  ],
  // 数据与记录
  [
    { translationKey: 'nav.history', key: 'History', icon: History },
    { translationKey: 'nav.knowledgeBase', key: 'KnowledgeBase', icon: BookOpen },
    { translationKey: 'nav.myTickets', key: 'MyTickets', icon: Ticket },
  ],
  // 管理后台
  [
    { translationKey: 'nav.tokenUsage', key: 'TokenUsage', icon: BarChart3, adminOnly: true },
    { translationKey: 'nav.userManage', key: 'UserManage', icon: UserCog, adminOnly: true },
    { translationKey: 'nav.modelManage', key: 'ModelManage', icon: Brain, adminOnly: true },
    { translationKey: 'nav.skillManage', key: 'SkillManage', icon: GraduationCap, adminOnly: true },
    { translationKey: 'nav.toolManage', key: 'ToolManage', icon: Wrench, adminOnly: true },
    { translationKey: 'nav.ticketManage', key: 'TicketManage', icon: TicketCheck, adminOnly: true },
    { translationKey: 'nav.logs', key: 'Logs', icon: FileText, adminOnly: true },
  ],
  // 设置
  [
    { translationKey: 'nav.settings', key: 'Settings', icon: Settings },
  ],
]

const visibleNavGroups = computed(() =>
  navGroups
    .map(group => group.filter(item => !item.adminOnly || auth.isAdmin))
    .filter(group => group.length > 0)
)

const activeKey = computed(() => (route.name as string) || 'Dashboard')

function navigate(key: string) {
  router.push({ name: key })
  mobileSidebarOpen.value = false
}

let unreadPollTimer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  unread.fetchAll()
  unreadPollTimer = setInterval(() => unread.fetchAll(), 10000)
})

onUnmounted(() => {
  if (unreadPollTimer) {
    clearInterval(unreadPollTimer)
    unreadPollTimer = null
  }
})

function handleLogout() {
  auth.logout()
  router.push({ name: 'Login' })
}

const usernameDisplay = computed(() => auth.currentUser?.username || t('common.normal'))
const userAvatar = computed(() => {
  const name = auth.currentUser?.username || 'U'
  return name.charAt(0).toUpperCase()
})
</script>

<template>
  <div :class="['flex h-screen bg-background', { 'dark': themeStore.isDark }]">
    <!-- Mobile sidebar overlay -->
    <div
      v-if="mobileSidebarOpen"
      class="fixed inset-0 z-40 bg-black/50 lg:hidden"
      @click="mobileSidebarOpen = false"
    />

    <!-- Sidebar -->
    <aside
      :class="[
        'fixed lg:static inset-y-0 left-0 z-50 flex flex-col bg-sidebar border-r border-border transition-all duration-300',
        mobileSidebarOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0',
        sidebarCollapsed ? 'w-16' : 'w-60',
      ]"
    >
      <!-- Logo -->
      <div :class="['flex items-center gap-2 px-4 h-14 border-b border-border', sidebarCollapsed ? 'justify-center' : '']">
        <Bot class="h-6 w-6 text-sidebar-primary" />
        <span v-show="!sidebarCollapsed" class="font-bold text-base text-sidebar-foreground truncate">
          EcomAgents
        </span>
      </div>

      <!-- Nav -->
      <nav class="flex-1 overflow-y-auto py-2 px-2 space-y-1">
        <template v-for="(group, groupIndex) in visibleNavGroups" :key="groupIndex">
          <div v-if="groupIndex > 0" class="border-t border-sidebar-border my-2" />
          <button
            v-for="item in group"
            :key="item.key"
            @click="navigate(item.key)"
          :class="[
            'flex items-center gap-3 w-full px-3 py-2 rounded-md text-sm transition-colors',
            activeKey === item.key
              ? 'bg-sidebar-accent text-sidebar-accent-foreground font-medium'
              : 'text-sidebar-foreground hover:bg-sidebar-accent/50',
            sidebarCollapsed ? 'justify-center' : '',
          ]"
          :title="sidebarCollapsed ? $t(item.translationKey) : ''"
        >
          <component :is="item.icon" class="h-4 w-4 shrink-0" />
          <span v-show="!sidebarCollapsed" class="truncate">{{ $t(item.translationKey) }}</span>
          <span
            v-if="item.key === 'Messages' && unread.totalPrivate() > 0"
            class="absolute right-2 min-w-[18px] h-[18px] flex items-center justify-center rounded-full bg-destructive text-destructive-foreground text-[10px] font-bold px-1"
          >{{ unread.totalPrivate() > 99 ? '99+' : unread.totalPrivate() }}</span>
          <span
            v-else-if="item.key === 'GroupChat' && unread.totalGroup() > 0"
            class="absolute right-2 min-w-[18px] h-[18px] flex items-center justify-center rounded-full bg-destructive text-destructive-foreground text-[10px] font-bold px-1"
          >{{ unread.totalGroup() > 99 ? '99+' : unread.totalGroup() }}</span>
        </button>
        </template>
      </nav>

      <!-- Bottom section -->
      <div class="border-t border-border p-3 space-y-2">
        <div :class="['flex items-center gap-2', sidebarCollapsed ? 'justify-center' : '']">
          <Avatar class="h-8 w-8">
            <AvatarFallback class="text-xs bg-sidebar-primary text-sidebar-primary-foreground">
              {{ userAvatar }}
            </AvatarFallback>
          </Avatar>
          <div v-show="!sidebarCollapsed" class="flex-1 min-w-0">
            <p class="text-sm font-medium text-sidebar-foreground truncate">{{ usernameDisplay }}</p>
          </div>
        </div>
        <div :class="['flex', sidebarCollapsed ? 'flex-col items-center gap-1' : 'justify-between']">
          <Button variant="ghost" size="icon" class="h-8 w-8 text-sidebar-foreground" @click="themeStore.toggle()" :title="themeStore.isDark ? $t('tooltip.lightMode') : $t('tooltip.darkMode')">
            <Sun v-if="!themeStore.isDark" class="h-4 w-4" />
            <Moon v-else class="h-4 w-4" />
          </Button>
          <Button variant="ghost" size="icon" class="h-8 w-8 text-sidebar-foreground" @click="toggleLocale" :title="currentLocale === 'zh-CN' ? 'English' : '中文'">
            <Languages class="h-4 w-4" />
          </Button>
          <Button variant="ghost" size="icon" class="h-8 w-8 text-sidebar-foreground" @click="handleLogout" :title="$t('tooltip.logout')">
            <LogOut class="h-4 w-4" />
          </Button>
          <Button variant="ghost" size="icon" class="h-8 w-8 text-sidebar-foreground hidden lg:flex" @click="sidebarCollapsed = !sidebarCollapsed" :title="sidebarCollapsed ? $t('tooltip.expandSidebar') : $t('tooltip.collapseSidebar')">
            <ChevronLeft :class="['h-4 w-4 transition-transform', sidebarCollapsed ? 'rotate-180' : '']" />
          </Button>
        </div>
      </div>
    </aside>

    <!-- Main content -->
    <div class="flex-1 flex flex-col min-w-0">
      <!-- Top bar -->
      <header class="flex items-center gap-2 h-14 px-4 border-b border-border bg-card shrink-0">
        <Button variant="ghost" size="icon" class="lg:hidden h-8 w-8" @click="mobileSidebarOpen = true">
          <Menu class="h-4 w-4" />
        </Button>
        <h1 class="text-lg font-semibold text-foreground">{{ currentTitle }}</h1>
      </header>

      <!-- Content -->
      <main class="flex-1 overflow-auto p-6 bg-muted/30">
        <router-view :key="route.fullPath" />
      </main>
    </div>
  </div>
</template>
