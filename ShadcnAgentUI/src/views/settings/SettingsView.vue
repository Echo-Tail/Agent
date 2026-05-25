<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Switch } from '@/components/ui/switch'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  User,
  Mail,
  Shield,
  Calendar,
  Sun,
  Moon,
  LogOut,
} from 'lucide-vue-next'
import { ref } from 'vue'
import { toast } from 'sonner'
import { setLocale, getCurrentLocale } from '@/locales'
import { Languages } from 'lucide-vue-next'
const { t } = useI18n()

const router = useRouter()
const auth = useAuthStore()
const themeStore = useThemeStore()
const showLogoutDialog = ref(false)
const currentLocale = ref(getCurrentLocale())

function toggleLocale() {
  const next = currentLocale.value === 'zh-CN' ? 'en' : 'zh-CN'
  setLocale(next)
  currentLocale.value = next
}

function handleLogout() {
  auth.logout()
  toast.success(t('toast.logoutSuccess'))
  router.push({ name: 'Login' })
}
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-5 gap-6">
    <!-- Profile (left 3/5) -->
    <Card class="lg:col-span-3">
      <CardHeader>
        <CardTitle class="text-lg">{{ $t('settings.profile') }}</CardTitle>
      </CardHeader>
      <CardContent class="space-y-4">
        <div class="flex items-center gap-3 pb-4 border-b border-border">
          <Avatar class="h-14 w-14">
            <AvatarFallback class="text-lg bg-primary/10 text-primary">
              {{ (auth.currentUser?.username || 'U').charAt(0).toUpperCase() }}
            </AvatarFallback>
          </Avatar>
          <div>
            <h3 class="font-semibold text-lg">{{ auth.currentUser?.username || '-' }}</h3>
            <p class="text-sm text-muted-foreground">{{ auth.currentUser?.email || '-' }}</p>
          </div>
        </div>

        <div class="space-y-3 text-sm">
          <div class="flex items-center gap-3">
            <User class="h-4 w-4 text-muted-foreground" />
            <span class="text-muted-foreground w-20">{{ $t('settings.username') }}</span>
            <span>{{ auth.currentUser?.username || '-' }}</span>
          </div>
          <div class="flex items-center gap-3">
            <Mail class="h-4 w-4 text-muted-foreground" />
            <span class="text-muted-foreground w-20">{{ $t('settings.email') }}</span>
            <span>{{ auth.currentUser?.email || '-' }}</span>
          </div>
          <div class="flex items-center gap-3">
            <Shield class="h-4 w-4 text-muted-foreground" />
            <span class="text-muted-foreground w-20">{{ $t('settings.role') }}</span>
            <Badge :variant="auth.isAdmin ? 'default' : 'secondary'" class="text-xs">
              {{ auth.isAdmin ? $t('userRole.admin') : $t('userRole.user') }}
            </Badge>
          </div>
          <div class="flex items-center gap-3">
            <Shield class="h-4 w-4 text-muted-foreground" />
            <span class="text-muted-foreground w-20">{{ $t('settings.status') }}</span>
            <Badge
              variant="outline"
              class="text-xs"
              :class="auth.currentUser?.status === 'active' ? 'text-green-600 border-green-200 bg-green-50 dark:border-green-800 dark:bg-green-950' : 'text-destructive border-destructive/30 bg-destructive/10'"
            >
              {{ auth.currentUser?.status === 'active' ? $t('userStatus.active') : $t('userStatus.disabled') }}
            </Badge>
          </div>
          <div class="flex items-center gap-3">
            <Calendar class="h-4 w-4 text-muted-foreground" />
            <span class="text-muted-foreground w-20">{{ $t('settings.registerTime') }}</span>
            <span>{{ auth.currentUser?.createdAt ? new Date(auth.currentUser.createdAt).toLocaleDateString('zh-CN') : '-' }}</span>
          </div>
        </div>
      </CardContent>
    </Card>

    <!-- Right column (2/5): Appearance + Language + Logout -->
    <div class="lg:col-span-2 space-y-6">
      <!-- Appearance -->
      <Card>
        <CardHeader>
          <CardTitle class="text-lg">{{ $t('settings.appearance') }}</CardTitle>
        </CardHeader>
        <CardContent>
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-3">
              <Sun v-if="!themeStore.isDark" class="h-5 w-5 text-muted-foreground" />
              <Moon v-else class="h-5 w-5 text-muted-foreground" />
              <div>
                <p class="text-sm font-medium">{{ $t('settings.darkMode') }}</p>
                <p class="text-xs text-muted-foreground">{{ $t('settings.darkModeDesc') }}</p>
              </div>
            </div>
            <Switch :checked="themeStore.isDark" @update:checked="themeStore.setDark" />
          </div>
        </CardContent>
      </Card>

      <!-- Language -->
      <Card>
        <CardHeader>
          <CardTitle class="text-lg">{{ $t('settings.language') }}</CardTitle>
        </CardHeader>
        <CardContent>
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-3">
              <Languages class="h-5 w-5 text-muted-foreground" />
              <div>
                <p class="text-sm font-medium">{{ $t('settings.language') }}</p>
                <p class="text-xs text-muted-foreground">{{ $t('settings.languageDesc') }}</p>
              </div>
            </div>
            <Button variant="outline" size="sm" class="h-8" @click="toggleLocale">
              {{ currentLocale === 'zh-CN' ? 'English' : '中文' }}
            </Button>
          </div>
        </CardContent>
      </Card>

      <!-- Logout -->
      <Card>
        <CardContent class="p-0">
          <div class="flex items-center justify-between px-6 py-4">
            <div class="flex items-center gap-3">
              <LogOut class="h-5 w-5 text-muted-foreground" />
              <div>
                <p class="text-sm font-medium">{{ $t('settings.logout') }}</p>
                <p class="text-xs text-muted-foreground">{{ $t('settings.logoutDesc') }}</p>
              </div>
            </div>
            <Button variant="destructive" @click="showLogoutDialog = true">
              <LogOut class="mr-2 h-4 w-4" />{{ $t('settings.logout') }}
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>

    <!-- Logout Dialog -->
    <Dialog :open="showLogoutDialog" @update:open="showLogoutDialog = $event">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{{ $t('dialog.logoutConfirm.title') }}</DialogTitle>
          <DialogDescription>{{ $t('dialog.logoutConfirm.desc') }}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" @click="showLogoutDialog = false">{{ $t('common.cancel') }}</Button>
          <Button variant="destructive" @click="handleLogout">{{ $t('settings.logout') }}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
