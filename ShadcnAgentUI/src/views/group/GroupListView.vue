<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { listMyGroupsApi, createGroupApi, updateGroupApi, disbandGroupApi } from '@/api/group'
import type { ChatGroup } from '@/types/group'
// import { useAuthStore } from '@/stores/auth'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { toast } from 'sonner'
import { Plus, MessageCircle, Settings2, Trash2, Check, X } from 'lucide-vue-next'

const { t } = useI18n()
const router = useRouter()
const groups = ref<ChatGroup[]>([])
const loading = ref(true)
const showCreateDialog = ref(false)
const newGroupName = ref('')

// 重命名
const renamingGroupId = ref<number | null>(null)
const renameValue = ref('')

// 解散确认
const disbandTargetId = ref<number | null>(null)
const showDisbandConfirm = ref(false)

onMounted(async () => {
  try {
    groups.value = await listMyGroupsApi()
  } catch {
    toast.error(t('error.loadFailed'))
  } finally {
    loading.value = false
  }
})

async function createGroup() {
  const name = newGroupName.value.trim()
  if (!name) {
    toast.error('群名称不能为空')
    return
  }
  try {
    const group = await createGroupApi(name)
    groups.value.unshift(group)
    showCreateDialog.value = false
    newGroupName.value = ''
    toast.success('群创建成功')
    router.push({ name: 'GroupChatDetail', params: { id: group.id } })
  } catch {
    toast.error('创建失败')
  }
}

function enterGroup(groupId: number) {
  router.push({ name: 'GroupChatDetail', params: { id: groupId } })
}

function startRename(group: ChatGroup, e: MouseEvent) {
  e.stopPropagation()
  renamingGroupId.value = group.id
  renameValue.value = group.name
}

async function confirmRename(groupId: number) {
  if (!renameValue.value.trim()) return
  try {
    const updated = await updateGroupApi(groupId, { name: renameValue.value.trim() })
    const idx = groups.value.findIndex(g => g.id === groupId)
    if (idx >= 0) groups.value[idx] = updated
    toast.success('已重命名')
  } catch {
    toast.error('重命名失败')
  } finally {
    renamingGroupId.value = null
  }
}

function cancelRename() {
  renamingGroupId.value = null
}

function startDisband(groupId: number, e: MouseEvent) {
  e.stopPropagation()
  disbandTargetId.value = groupId
  showDisbandConfirm.value = true
}

async function confirmDisband() {
  if (!disbandTargetId.value) return
  try {
    await disbandGroupApi(disbandTargetId.value)
    groups.value = groups.value.filter(g => g.id !== disbandTargetId.value)
    toast.success('群已解散')
  } catch {
    toast.error('解散失败')
  } finally {
    disbandTargetId.value = null
    showDisbandConfirm.value = false
  }
}
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <h2 class="text-2xl font-bold">{{ t('nav.groupChat') }}</h2>
      <Dialog v-model:open="showCreateDialog">
        <DialogTrigger as-child>
          <Button>
            <Plus class="mr-1 h-4 w-4" /> 创建群
          </Button>
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>创建群</DialogTitle>
          </DialogHeader>
          <div class="space-y-4 pt-4">
            <Input v-model="newGroupName" placeholder="输入群名称" @keydown.enter="createGroup" />
            <Button class="w-full" @click="createGroup">确认创建</Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>

    <div v-if="loading" class="flex justify-center py-20">
      <span class="text-muted-foreground">加载中...</span>
    </div>

    <div v-else-if="groups.length === 0" class="flex flex-col items-center justify-center py-20 text-muted-foreground">
      <MessageCircle class="h-16 w-16 mb-4 opacity-30" />
      <p class="text-lg">还没有群</p>
      <p class="text-sm mt-1">创建一个群开始群聊吧</p>
    </div>

    <div v-else class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <Card
        v-for="group in groups"
        :key="group.id"
        class="cursor-pointer hover:bg-accent/50 transition-colors relative group/card"
        @click="enterGroup(group.id)"
      >
        <CardHeader class="pb-2">
          <CardTitle class="flex items-center gap-2 text-base">
            <MessageCircle class="h-5 w-5 text-primary shrink-0" />
            <!-- 重命名模式 -->
            <div v-if="renamingGroupId === group.id" class="flex-1 flex items-center gap-1" @click.stop>
              <Input v-model="renameValue" class="h-7 text-sm" @keydown.enter="confirmRename(group.id)" @keydown.esc="cancelRename" />
              <Button variant="ghost" size="icon" class="h-7 w-7" @click="confirmRename(group.id)">
                <Check class="h-3.5 w-3.5" />
              </Button>
              <Button variant="ghost" size="icon" class="h-7 w-7" @click="cancelRename">
                <X class="h-3.5 w-3.5" />
              </Button>
            </div>
            <span v-else class="truncate">{{ group.name }}</span>
          </CardTitle>
        </CardHeader>
        <CardContent class="text-sm text-muted-foreground flex items-center justify-between">
          <span>创建于 {{ new Date(group.createdAt).toLocaleDateString() }}</span>
        </CardContent>
        <!-- 悬浮操作按钮 -->
        <div class="absolute top-2 right-2 flex gap-1 opacity-0 group-hover/card:opacity-100 transition-opacity" @click.stop>
          <Button variant="ghost" size="icon" class="h-7 w-7" title="重命名" @click="startRename(group, $event)">
            <Settings2 class="h-3.5 w-3.5" />
          </Button>
          <Button variant="ghost" size="icon" class="h-7 w-7 text-destructive hover:text-destructive" title="解散群" @click="startDisband(group.id, $event)">
            <Trash2 class="h-3.5 w-3.5" />
          </Button>
        </div>
      </Card>
    </div>

    <!-- 解散确认弹窗 -->
    <Dialog :open="showDisbandConfirm" @update:open="showDisbandConfirm = $event">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>确认解散群？</DialogTitle>
        </DialogHeader>
        <p class="text-sm text-muted-foreground">群解散后所有消息和文件将被删除，此操作不可撤销。</p>
        <div class="flex justify-end gap-2 pt-4">
          <Button variant="outline" @click="showDisbandConfirm = false">取消</Button>
          <Button variant="destructive" @click="confirmDisband">确认解散</Button>
        </div>
      </DialogContent>
    </Dialog>
  </div>
</template>
