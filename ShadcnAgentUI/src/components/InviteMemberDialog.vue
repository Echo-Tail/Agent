<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { searchUsersApi } from '@/api/user'
import { inviteMemberApi } from '@/api/group'
import type { UserDTO } from '@/types/api'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { toast } from 'sonner'
import { Search, Loader2, Check, UserPlus } from 'lucide-vue-next'

const props = defineProps<{
  groupId: number
  open: boolean
  /** 当前已在群里的用户 ID 列表（排除这些） */
  existingMemberIds: number[]
}>()

const emit = defineEmits<{
  (e: 'update:open', val: boolean): void
  (e: 'invited'): void
}>()

const keyword = ref('')
const allUsers = ref<UserDTO[]>([])
const selectedIds = ref<Set<number>>(new Set())
const loading = ref(true)
const inviting = ref(false)

// 排除已在群里的用户
const availableUsers = computed(() => {
  const memberSet = new Set(props.existingMemberIds)
  return allUsers.value.filter(u => !memberSet.has(u.id))
})

// 按关键字过滤
const filteredUsers = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return availableUsers.value
  return availableUsers.value.filter(u => u.username.toLowerCase().includes(kw))
})

onMounted(() => loadUsers())
watch(() => props.open, (val) => { if (val) loadUsers() })

async function loadUsers() {
  loading.value = true
  selectedIds.value = new Set()
  try {
    allUsers.value = await searchUsersApi('')
  } catch {
    toast.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

function toggleSelect(userId: number) {
  const set = new Set(selectedIds.value)
  if (set.has(userId)) set.delete(userId)
  else set.add(userId)
  selectedIds.value = set
}

async function confirmInvite() {
  if (selectedIds.value.size === 0) return
  inviting.value = true
  let success = 0
  let fail = 0
  for (const userId of selectedIds.value) {
    try {
      await inviteMemberApi(props.groupId, userId)
      success++
    } catch {
      fail++
    }
  }
  inviting.value = false
  if (success > 0) toast.success(`已邀请 ${success} 位成员${fail > 0 ? `，${fail} 位邀请失败` : ''}`)
  if (fail > 0) toast.error(`${fail} 位邀请失败`)
  emit('invited')
  close()
}

function close() {
  emit('update:open', false)
}
</script>

<template>
  <Dialog :open="open" @update:open="close">
    <DialogContent class="max-w-lg">
      <DialogHeader>
        <DialogTitle>邀请成员</DialogTitle>
      </DialogHeader>

      <!-- 搜索 -->
      <div class="relative">
        <Search class="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
        <Input v-model="keyword" placeholder="搜索用户名..." class="pl-8" />
      </div>

      <!-- 已选计数 -->
      <div v-if="selectedIds.size > 0" class="flex items-center gap-1 text-sm text-primary">
        <Check class="h-4 w-4" /> 已选 {{ selectedIds.size }} 人
      </div>

      <!-- 用户列表 -->
      <div v-if="loading" class="flex justify-center py-8">
        <Loader2 class="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
      <div v-else-if="allUsers.length === 0" class="py-8 text-center text-sm text-muted-foreground">
        暂无用户数据
      </div>
      <div v-else class="max-h-64 overflow-y-auto space-y-1">
        <div
          v-for="u in filteredUsers"
          :key="u.id"
          :class="[
            'flex items-center gap-3 rounded-md px-3 py-2 cursor-pointer transition-colors',
            selectedIds.has(u.id) ? 'bg-accent' : 'hover:bg-accent/50'
          ]"
          @click="toggleSelect(u.id)"
        >
          <input
            type="checkbox"
            :checked="selectedIds.has(u.id)"
            class="h-4 w-4 rounded border-gray-300 text-primary focus:ring-primary"
          />
          <Avatar class="h-8 w-8">
            <AvatarFallback class="text-xs">{{ u.username.charAt(0).toUpperCase() }}</AvatarFallback>
          </Avatar>
          <div class="flex-1">
            <p class="text-sm font-medium">{{ u.username }}</p>
            <p class="text-xs text-muted-foreground">{{ u.role === 'admin' ? '管理员' : '用户' }}</p>
          </div>
        </div>
        <div v-if="filteredUsers.length === 0 && keyword.trim() !== ''" class="py-4 text-center text-sm text-muted-foreground">
          没有找到匹配的用户
        </div>
        <div v-if="filteredUsers.length === 0 && keyword.trim() === '' && allUsers.length > 0" class="py-4 text-center text-sm text-muted-foreground">
          所有用户都已加入群
        </div>
      </div>

      <!-- 确认按钮 -->
      <div class="flex justify-end gap-2 pt-2">
        <Button variant="outline" @click="close">取消</Button>
        <Button :disabled="selectedIds.size === 0 || inviting" @click="confirmInvite">
          <UserPlus class="h-4 w-4 mr-1" />
          {{ inviting ? '邀请中...' : `邀请所选成员（${selectedIds.size}）` }}
        </Button>
      </div>
    </DialogContent>
  </Dialog>
</template>
