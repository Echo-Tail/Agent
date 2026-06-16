import { createRouter, createWebHistory } from 'vue-router'
import { STORAGE_KEY_TOKEN, STORAGE_KEY_USER } from '@/constants'
import { useAuthStore } from '@/stores/auth'
import BlankLayout from '@/layouts/BlankLayout.vue'
import DefaultLayout from '@/layouts/DefaultLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      component: BlankLayout,
      children: [
        { path: '', name: 'Login', component: () => import('@/views/login/LoginView.vue') },
      ],
    },
    {
      path: '/register',
      component: BlankLayout,
      children: [
        { path: '', name: 'Register', component: () => import('@/views/register/RegisterView.vue') },
      ],
    },
    {
      path: '/',
      component: DefaultLayout,
      children: [
        { path: '', name: 'Dashboard', component: () => import('@/views/dashboard/DashboardView.vue') },
        { path: 'agents', name: 'AgentList', component: () => import('@/views/agent/AgentList.vue') },
        { path: 'agents/plaza', name: 'AgentPlaza', component: () => import('@/views/agent/AgentPlaza.vue') },
        { path: 'agents/image', name: 'ImageGeneration', component: () => import('@/views/image/ImageGenerationView.vue') },
        { path: 'agents/assets', name: 'PublicAssets', component: () => import('@/views/assets/AssetLibraryView.vue') },
        { path: 'agents/prompts', name: 'PromptLibrary', component: () => import('@/views/prompts/PromptLibraryView.vue') },
        { path: 'agents/create', name: 'AgentCreate', component: () => import('@/views/agent/AgentCreate.vue') },
        { path: 'agents/edit/:id', name: 'AgentEdit', component: () => import('@/views/agent/AgentCreate.vue') },
        { path: 'chat', name: 'Chat', component: () => import('@/views/chat/DirectChatView.vue') },
        { path: 'groups', name: 'GroupChat', component: () => import('@/views/group/GroupListView.vue') },
        { path: 'groups/:id', name: 'GroupChatDetail', component: () => import('@/views/group/GroupChatView.vue') },
        { path: 'messages', name: 'Messages', component: () => import('@/views/message/MessageListView.vue') },
        { path: 'messages/:userId', name: 'MessagesDetail', component: () => import('@/views/message/MessageChatView.vue') },
        { path: 'history', name: 'History', component: () => import('@/views/history/HistoryView.vue') },
        { path: 'tickets', name: 'MyTickets', component: () => import('@/views/ticket/MyTickets.vue') },
        { path: 'admin/users', name: 'UserManage', component: () => import('@/views/admin/UserManage.vue') },
        { path: 'admin/models', name: 'ModelManage', component: () => import('@/views/admin/ModelManage.vue') },
        { path: 'admin/tools', name: 'ToolManage', component: () => import('@/views/admin/ToolManage.vue') },
        { path: 'admin/skills', name: 'SkillManage', component: () => import('@/views/admin/SkillManage.vue') },
        { path: 'knowledge', name: 'KnowledgeBase', component: () => import('@/views/knowledge/KnowledgeBase.vue') },
        { path: 'knowledge/:id', name: 'KnowledgeDetail', component: () => import('@/views/knowledge/KnowledgeBase.vue') },
        { path: 'settings', name: 'Settings', component: () => import('@/views/settings/SettingsView.vue') },
        { path: 'admin/token-usage', name: 'TokenUsage', component: () => import('@/views/admin/TokenUsage.vue') },
        { path: 'admin/tickets', name: 'TicketManage', component: () => import('@/views/admin/TicketManage.vue') },
        { path: 'logs', name: 'Logs', component: () => import('@/views/log/LogViewer.vue') },
      ],
    },
  ],
})

router.beforeEach(async (to, _from) => {
  const token = localStorage.getItem(STORAGE_KEY_TOKEN)
  const userStr = localStorage.getItem(STORAGE_KEY_USER)
  const user = userStr ? JSON.parse(userStr) : null
  const isAuthenticated = !!(token && user)

  if (!isAuthenticated && to.name !== 'Login' && to.name !== 'Register') {
    return { name: 'Login' }
  }

  if (isAuthenticated && (to.name === 'Login' || to.name === 'Register')) {
    return { name: 'Dashboard' }
  }

  if (isAuthenticated && to.name !== 'Login' && to.name !== 'Register') {
    const auth = useAuthStore()
    if (!auth.initialized) {
      auth.initialized = true
      const valid = await auth.verifyAuth()
      if (!valid) return { name: 'Login' }
    }
  }

  const adminRoutes = ['UserManage', 'ModelManage', 'ToolManage', 'SkillManage', 'TokenUsage', 'TicketManage']
  if (adminRoutes.includes(to.name as string)) {
    const auth = useAuthStore()
    const valid = await auth.verifyAuth()
    if (!valid) return { name: 'Login' }
    if (!auth.isAdmin) return { name: 'Dashboard' }
  }
})

export default router
