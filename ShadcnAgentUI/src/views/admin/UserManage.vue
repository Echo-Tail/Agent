<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from '@/components/ui/tabs'
import {
  listUsersApi,
  toggleUserStatusApi,
} from '@/api/user'
import {
  listInviteCodesApi,
  batchGenerateApi,
  deleteInviteCodeApi,
} from '@/api/invite'
import { useAuthStore } from '@/stores/auth'
import { UserRoleKeys, UserStatusKeys } from '@/types/enums'
import { useI18n } from 'vue-i18n'
import type { UserDTO, InviteCode } from '@/types/api'
import {
  Users,
  UserCheck,
  UserX,
  Shield,
  Loader2,
  Mail,
  Plus,
  Trash2,
  Copy,
  Check,
} from 'lucide-vue-next'
import { toast } from 'sonner'

const authStore = useAuthStore()
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

const { t } = useI18n()

const users = ref<UserDTO[]>([])
const loading = ref(false)
const toggling = ref<Set<number>>(new Set())

// Invite code modal
const showInviteModal = ref(false)
const inviteCodes = ref<InviteCode[]>([])
const inviteLoading = ref(false)
const inviteTab = ref('unused')
const batchCount = ref(5)
const batchLoading = ref(false)
const deletingCode = ref<Set<string>>(new Set())
const copiedCode = ref<string | null>(null)
const copiedBulk = ref(false)

const stats = computed(() => {
  const total = users.value.length
  const active = users.value.filter((u) => u.status === 'active').length
  const disabled = users.value.filter((u) => u.status === 'disabled').length
  const admin = users.value.filter((u) => u.role === 'admin').length
  return { total, active, disabled, admin }
})

const unusedCodes = computed(() => inviteCodes.value.filter((c) => !c.used))
const usedCodes = computed(() => inviteCodes.value.filter((c) => c.used))

onMounted(fetchUsers)

async function fetchUsers() {
  loading.value = true
  try {
    users.value = (await listUsersApi()) ?? []
  } finally {
    loading.value = false
  }
}

async function handleToggleStatus(user: UserDTO) {
  toggling.value = new Set(toggling.value).add(user.id)
  try {
    await toggleUserStatusApi(user.id)
    toast.success(t('toast.updateSuccess'))
    await fetchUsers()
  } catch { /* interceptor handles toast */ } finally {
    const next = new Set(toggling.value)
    next.delete(user.id)
    toggling.value = next
  }
}

async function openInviteModal() {
  showInviteModal.value = true
  await fetchInviteCodes()
}

async function fetchInviteCodes() {
  inviteLoading.value = true
  try {
    inviteCodes.value = (await listInviteCodesApi()) ?? []
  } finally {
    inviteLoading.value = false
  }
}

async function handleBatchGenerate() {
  batchLoading.value = true
  try {
    await batchGenerateApi(batchCount.value)
    toast.success(t('toast.createSuccess'))
    await fetchInviteCodes()
  } catch { /* interceptor handles toast */ } finally {
    batchLoading.value = false
  }
}

async function handleDeleteCode(code: string) {
  deletingCode.value = new Set(deletingCode.value).add(code)
  try {
    await deleteInviteCodeApi(code)
    toast.success(t('toast.deleteSuccess'))
    await fetchInviteCodes()
  } catch { /* interceptor handles toast */ } finally {
    const next = new Set(deletingCode.value)
    next.delete(code)
    deletingCode.value = next
  }
}

async function copyText(text: string) {
  await navigator.clipboard.writeText(text)
}

async function handleCopyCode(code: string) {
  try {
    await copyText(code)
    copiedCode.value = code
    toast.success(t('userManage.copySuccess'))
    setTimeout(() => {
      if (copiedCode.value === code) copiedCode.value = null
    }, 1500)
  } catch {
    toast.error(t('userManage.copyFailed'))
  }
}

async function handleCopyUnusedCodes() {
  if (unusedCodes.value.length === 0) return
  try {
    await copyText(unusedCodes.value.map((c) => c.code).join('\n'))
    copiedBulk.value = true
    toast.success(t('userManage.batchCopySuccess', { count: unusedCodes.value.length }))
    setTimeout(() => { copiedBulk.value = false }, 1500)
  } catch {
    toast.error(t('userManage.copyFailed'))
  }
}

function formatDate(dateStr: string) {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}
</script>

<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <PageHeader :title="$t('pageTitle.userManage')" :description="$t('userManage.title')" />
    </div>

    <!-- Stats -->
    <div class="grid gap-4 md:grid-cols-4">
      <Card>
        <CardHeader class="flex flex-row items-center justify-between pb-2">
          <CardTitle class="text-sm font-medium">{{ $t('common.total') }}</CardTitle>
          <Users class="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div class="text-2xl font-bold">{{ stats.total }}</div>
        </CardContent>
      </Card>
      <Card>
        <CardHeader class="flex flex-row items-center justify-between pb-2">
          <CardTitle class="text-sm font-medium">{{ $t('userStatus.active') }}</CardTitle>
          <UserCheck class="h-4 w-4 text-emerald-500" />
        </CardHeader>
        <CardContent>
          <div class="text-2xl font-bold text-emerald-600">{{ stats.active }}</div>
        </CardContent>
      </Card>
      <Card>
        <CardHeader class="flex flex-row items-center justify-between pb-2">
          <CardTitle class="text-sm font-medium">{{ $t('userStatus.disabled') }}</CardTitle>
          <UserX class="h-4 w-4 text-amber-500" />
        </CardHeader>
        <CardContent>
          <div class="text-2xl font-bold text-amber-600">{{ stats.disabled }}</div>
        </CardContent>
      </Card>
      <Card>
        <CardHeader class="flex flex-row items-center justify-between pb-2">
          <CardTitle class="text-sm font-medium">{{ $t('userRole.admin') }}</CardTitle>
          <Shield class="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div class="text-2xl font-bold">{{ stats.admin }}</div>
        </CardContent>
      </Card>
    </div>

    <!-- User List -->
    <div class="flex items-center justify-between">
      <h3 class="text-lg font-semibold">{{ $t('userManage.title') }}</h3>
      <Button variant="outline" size="sm" @click="openInviteModal">
        <Mail class="mr-1 h-4 w-4" />{{ $t('userManage.inviteCodes') }}
      </Button>
    </div>

    <div v-if="loading" class="space-y-2">
      <Skeleton v-for="i in 5" :key="i" class="h-12 w-full" />
    </div>
    <div v-else class="border border-border rounded-lg overflow-hidden">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead class="w-16">{{ $t('userManage.columns.id') }}</TableHead>
            <TableHead>{{ $t('userManage.columns.username') }}</TableHead>
            <TableHead class="w-20">{{ $t('userManage.columns.role') }}</TableHead>
            <TableHead class="w-20">{{ $t('userManage.columns.status') }}</TableHead>
            <TableHead class="w-28">{{ $t('userManage.columns.createdAt') }}</TableHead>
            <TableHead class="w-32">{{ $t('userManage.inviteCodes') }}</TableHead>
            <TableHead class="w-24">{{ $t('userManage.columns.action') }}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="user in users" :key="user.id">
            <TableCell class="text-muted-foreground">{{ user.id }}</TableCell>
            <TableCell class="font-medium">{{ user.username }}</TableCell>
            <TableCell>
              <Badge :variant="user.role === 'admin' ? 'default' : 'secondary'" class="text-xs">
                {{ $t(UserRoleKeys[user.role]) || user.role }}
              </Badge>
            </TableCell>
            <TableCell>
              <Badge
                variant="outline"
                :class="user.status === 'active'
                  ? 'text-green-600 border-green-200 bg-green-50 dark:border-green-800 dark:bg-green-950'
                  : 'text-destructive border-destructive/30 bg-destructive/10'"
                class="text-xs"
              >
                {{ $t(UserStatusKeys[user.status]) || user.status }}
              </Badge>
              </TableCell>
            <TableCell class="text-muted-foreground">{{ formatDate(user.createdAt) }}</TableCell>
            <TableCell class="font-mono text-xs text-muted-foreground">{{ user.inviteCode || '-' }}</TableCell>
            <TableCell>
              <Button
                variant="outline"
                size="sm"
                class="h-7 text-xs"
                :disabled="user.id === authStore.currentUser?.id || user.role === 'admin'"
                :loading="toggling.has(user.id)"
                @click="handleToggleStatus(user)"
              >
                <Loader2 v-if="toggling.has(user.id)" class="mr-1 h-3 w-3 animate-spin" />
                {{ user.status === 'active' ? $t('toolManage.disableTool') : $t('toolManage.enableTool') }}
              </Button>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>

    <!-- Invite Code Modal -->
    <Dialog :open="showInviteModal" @update:open="showInviteModal = $event">
      <DialogContent class="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{{ $t('userManage.inviteCodes') }}</DialogTitle>
        </DialogHeader>
        <div class="space-y-4">
          <!-- Batch generate -->
          <div class="border border-border rounded-lg p-4">
            <h4 class="text-sm font-medium mb-3">{{ $t('userManage.batchGenerate') }}</h4>
            <div class="flex items-center gap-2">
              <div class="flex items-center border border-input rounded-md">
                <Button
                  variant="ghost"
                  size="sm"
                  class="h-8 w-8 rounded-none"
                  :disabled="batchCount <= 1"
                  @click="batchCount--"
                >-</Button>
                <span class="w-12 text-center text-sm tabular-nums">{{ batchCount }}</span>
                <Button
                  variant="ghost"
                  size="sm"
                  class="h-8 w-8 rounded-none"
                  :disabled="batchCount >= 100"
                  @click="batchCount++"
                >+</Button>
              </div>
              <Button size="sm" :loading="batchLoading" @click="handleBatchGenerate">
                <Plus class="mr-1 h-3.5 w-3.5" />{{ $t('common.create') }}
              </Button>
            </div>
          </div>

          <!-- Code list -->
          <Tabs :default-value="'unused'" @update:model-value="(v: string | number) => inviteTab = v as string">
            <TabsList class="grid w-full grid-cols-2">
              <TabsTrigger value="unused">{{ $t('userManage.inviteCodeUnused') }} ({{ unusedCodes.length }})</TabsTrigger>
              <TabsTrigger value="used">{{ $t('userManage.inviteCodeUsed') }} ({{ usedCodes.length }})</TabsTrigger>
            </TabsList>
            <TabsContent value="unused" class="max-h-72 overflow-y-auto space-y-1">
              <div v-if="!inviteLoading && unusedCodes.length > 0" class="sticky top-0 z-10 flex justify-end bg-background pb-2">
                <Button variant="outline" size="sm" class="h-8 text-xs" @click="handleCopyUnusedCodes">
                  <Check v-if="copiedBulk" class="mr-1 h-3.5 w-3.5" />
                  <Copy v-else class="mr-1 h-3.5 w-3.5" />
                  {{ $t('userManage.copyAllUnused') }}
                </Button>
              </div>
              <div v-if="inviteLoading" class="flex justify-center py-4">
                <Loader2 class="h-5 w-5 animate-spin text-muted-foreground" />
              </div>
              <div v-else-if="unusedCodes.length === 0" class="text-center py-6 text-sm text-muted-foreground">
                {{ $t('common.noData') }}
              </div>
              <div
                v-for="code in unusedCodes"
                :key="code.code"
                class="flex items-center justify-between px-3 py-2 rounded-md hover:bg-muted/50 text-sm"
              >
                <div>
                  <span class="font-mono font-medium">{{ code.code }}</span>
                  <span class="text-xs text-muted-foreground ml-2">{{ $t('common.create') }} {{ formatDate(code.createdAt) }}</span>
                </div>
                <div class="flex items-center gap-1">
                  <Button
                    variant="ghost"
                    size="icon"
                    class="h-7 w-7"
                    :title="$t('userManage.copyInviteCode')"
                    @click="handleCopyCode(code.code)"
                  >
                    <Check v-if="copiedCode === code.code" class="h-3.5 w-3.5 text-green-600" />
                    <Copy v-else class="h-3.5 w-3.5" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    class="h-7 w-7 text-destructive hover:text-destructive"
                    :disabled="deletingCode.has(code.code)"
                    @click="handleDeleteCode(code.code)"
                  >
                    <Loader2 v-if="deletingCode.has(code.code)" class="h-3.5 w-3.5 animate-spin" />
                    <Trash2 v-else class="h-3.5 w-3.5" />
                  </Button>
                </div>
              </div>
            </TabsContent>
            <TabsContent value="used" class="max-h-72 overflow-y-auto space-y-1">
              <div v-if="inviteLoading" class="flex justify-center py-4">
                <Loader2 class="h-5 w-5 animate-spin text-muted-foreground" />
              </div>
              <div v-else-if="usedCodes.length === 0" class="text-center py-6 text-sm text-muted-foreground">
                {{ $t('common.noData') }}
              </div>
              <div
                v-for="code in usedCodes"
                :key="code.code"
                class="flex items-center justify-between px-3 py-2 rounded-md hover:bg-muted/50 text-sm"
              >
                <div>
                  <span class="font-mono font-medium">{{ code.code }}</span>
                  <span class="text-xs text-muted-foreground ml-2">
                    {{ $t('userManage.inviteCodeColumns.usedBy') }}：{{ code.usedBy || '-' }} · {{ formatDate(code.createdAt) }}
                  </span>
                </div>
                <div class="flex items-center gap-1">
                  <Button
                    variant="ghost"
                    size="icon"
                    class="h-7 w-7"
                    :title="$t('userManage.copyInviteCode')"
                    @click="handleCopyCode(code.code)"
                  >
                    <Check v-if="copiedCode === code.code" class="h-3.5 w-3.5 text-green-600" />
                    <Copy v-else class="h-3.5 w-3.5" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    class="h-7 w-7 text-destructive hover:text-destructive"
                    :disabled="deletingCode.has(code.code)"
                    @click="handleDeleteCode(code.code)"
                  >
                    <Loader2 v-if="deletingCode.has(code.code)" class="h-3.5 w-3.5 animate-spin" />
                    <Trash2 v-else class="h-3.5 w-3.5" />
                  </Button>
                </div>
              </div>
            </TabsContent>
          </Tabs>
        </div>
      </DialogContent>
    </Dialog>
  </div>
</template>
