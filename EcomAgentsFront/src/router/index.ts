import { createRouter, createWebHistory } from 'vue-router'
import { STORAGE_KEY_TOKEN, STORAGE_KEY_USER } from '../constants'
import BlankLayout from '../layouts/BlankLayout.vue'
import DefaultLayout from '../layouts/DefaultLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      component: BlankLayout,
      children: [
        {
          path: '',
          name: 'Login',
          component: () => import('../views/login/LoginView.vue'),
        },
      ],
    },
    {
      path: '/register',
      component: BlankLayout,
      children: [
        {
          path: '',
          name: 'Register',
          component: () => import('../views/register/RegisterView.vue'),
        },
      ],
    },
    {
      path: '/',
      component: DefaultLayout,
      children: [
        {
          path: '',
          name: 'Dashboard',
          component: () => import('../views/dashboard/DashboardView.vue'),
        },
        {
          path: 'agents',
          name: 'AgentList',
          component: () => import('../views/agent/AgentList.vue'),
        },
        {
          path: 'agents/create',
          name: 'AgentCreate',
          component: () => import('../views/agent/AgentCreate.vue'),
        },
        {
          path: 'agents/edit/:id',
          name: 'AgentEdit',
          component: () => import('../views/agent/AgentCreate.vue'),
        },
        {
          path: 'chat',
          name: 'Chat',
          component: () => import('../views/chat/DirectChatView.vue'),
        },
        {
          path: 'history',
          name: 'History',
          component: () => import('../views/history/HistoryView.vue'),
        },
        {
          path: 'admin/users',
          name: 'UserManage',
          component: () => import('../views/admin/UserManage.vue'),
        },
        {
          path: 'admin/models',
          name: 'ModelManage',
          component: () => import('../views/admin/ModelManage.vue'),
        },
        {
          path: 'admin/tools',
          name: 'ToolManage',
          component: () => import('../views/admin/ToolManage.vue'),
        },
        {
          path: 'admin/skills',
          name: 'SkillManage',
          component: () => import('../views/admin/SkillManage.vue'),
        },
        {
          path: 'knowledge',
          name: 'KnowledgeBase',
          component: () => import('../views/knowledge/KnowledgeBase.vue'),
        },
        {
          path: 'knowledge/:id',
          name: 'KnowledgeDetail',
          component: () => import('../views/knowledge/KnowledgeBase.vue'),
        },
        {
          path: 'settings',
          name: 'Settings',
          component: () => import('../views/settings/SettingsView.vue'),
        },
        {
          path: 'logs',
          name: 'Logs',
          component: () => import('../views/log/LogViewer.vue'),
        },
      ],
    },
  ],
})

// Navigation guard: auth check + role check
router.beforeEach((to, _from) => {
  const token = localStorage.getItem(STORAGE_KEY_TOKEN)
  const userStr = localStorage.getItem(STORAGE_KEY_USER)
  const user = userStr ? JSON.parse(userStr) : null
  const isAuthenticated = !!(token && user)
  const isAdmin = isAuthenticated && user.role === 'admin'

  // Redirect to login if not authenticated (except login/register)
  if (!isAuthenticated && to.name !== 'Login' && to.name !== 'Register') {
    return { name: 'Login' }
  }

  // Redirect to dashboard if already logged in and visiting login/register
  if (isAuthenticated && (to.name === 'Login' || to.name === 'Register')) {
    return { name: 'Dashboard' }
  }

  // Admin-only routes
  if (to.name === 'UserManage' || to.name === 'ModelManage' || to.name === 'ToolManage' || to.name === 'SkillManage') {
    if (!isAdmin) {
      return { name: 'Dashboard' }
    }
  }
})

export default router
