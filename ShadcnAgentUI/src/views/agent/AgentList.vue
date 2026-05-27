<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAgentStore } from '@/stores/agent'
import { Card, CardContent } from '@/components/ui/card'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Plus,
  Bot,
  Pencil,
  Trash2,
  MessageSquare,
} from 'lucide-vue-next'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import AgentIcon from '@/components/AgentIcon.vue'
import { deleteAgentApi } from '@/api/agent'
import { toast } from 'sonner'

const { t } = useI18n()
const router = useRouter()
const agentStore = useAgentStore()

const loading = ref(true)
const deletingId = ref<number | null>(null)
const showDeleteDialog = ref(false)
const deleteTarget = ref<number | null>(null)

onMounted(async () => {
  loading.value = true
  await agentStore.fetchMyAgents()
  loading.value = false
})

function goToCreate() {
  router.push({ name: 'AgentCreate' })
}

function goToEdit(id: number) {
  router.push({ name: 'AgentEdit', params: { id } })
}

function goToChat(agentId: number) {
  router.push({ name: 'Chat', query: { agentId: agentId.toString() } })
}

function confirmDelete(id: number) {
  deleteTarget.value = id
  showDeleteDialog.value = true
}

async function handleDelete() {
  if (deleteTarget.value === null) return
  deletingId.value = deleteTarget.value
  try {
    await deleteAgentApi(deleteTarget.value)
    toast.success(t('toast.deleteSuccess'))
    await agentStore.fetchMyAgents()
  } catch { /* interceptor handles toast */ } finally {
    deletingId.value = null
    showDeleteDialog.value = false
    deleteTarget.value = null
  }
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader :title="$t('pageTitle.myAgents')">
      <Button @click="goToCreate">
        <Plus class="mr-2 h-4 w-4" />
        {{ $t('agent.create') }}
      </Button>
    </PageHeader>

    <!-- Skeleton -->
    <div v-if="loading" class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
      <div v-for="i in 6" :key="i">
        <Card><CardContent class="p-6"><Skeleton class="h-4 w-32 mb-2" /><Skeleton class="h-3 w-48" /></CardContent></Card>
      </div>
    </div>

    <!-- Empty -->
    <EmptyState v-else-if="agentStore.myAgents.length === 0" :icon="Bot" :title="$t('agent.noMyAgents')" />

    <!-- Grid -->
    <div v-else class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
      <Card v-for="agent in agentStore.myAgents" :key="agent.id">
        <CardContent class="p-5">
          <div class="flex items-start justify-between mb-3">
            <div class="flex items-center gap-3" @click="goToChat(agent.id)">
              <div class="flex h-12 w-12 items-center justify-center rounded-full bg-primary/10 text-primary overflow-hidden cursor-pointer">
                <AgentIcon v-if="agent.icon || agent.avatar" :icon="agent.icon" :avatar="agent.avatar" class="h-6 w-6" />
                <span v-else class="text-lg font-bold">{{ agent.name.charAt(0).toUpperCase() }}</span>
              </div>
              <div class="cursor-pointer">
                <h3 class="font-semibold">{{ agent.name }}</h3>
                <p class="text-xs text-muted-foreground line-clamp-1">{{ agent.description || $t('common.noDescription') }}</p>
              </div>
            </div>
            <Badge :variant="agent.status === 'active' ? 'default' : 'secondary'">
              {{ agent.status === 'active' ? $t('userStatus.active') : $t('userStatus.disabled') }}
            </Badge>
          </div>
          <div v-if="agent.tags?.length" class="flex flex-wrap gap-1 mb-3">
            <Badge v-for="tag in agent.tags.slice(0, 3)" :key="tag" variant="outline" class="text-xs">{{ tag }}</Badge>
          </div>
          <div class="flex gap-2 pt-2 border-t border-border">
            <Button variant="outline" size="sm" class="flex-1" @click="goToEdit(agent.id)">
              <Pencil class="mr-1 h-3 w-3" /> {{ $t('common.edit') }}
            </Button>
            <Button variant="outline" size="sm" class="flex-1" @click="goToChat(agent.id)">
              <MessageSquare class="mr-1 h-3 w-3" /> {{ $t('chat.directChat') }}
            </Button>
            <Button variant="outline" size="sm" class="text-destructive hover:text-destructive" @click="confirmDelete(agent.id)">
              <Trash2 class="h-3 w-3" />
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>

    <ConfirmDialog
      :open="showDeleteDialog"
      @update:open="showDeleteDialog = $event"
      :title="t('dialog.deleteConfirm.title')"
      :description="t('dialog.deleteConfirm.desc', { entity: 'Agent' })"
      :confirm-text="t('common.delete')"
      :loading="deletingId !== null"
      @confirm="handleDelete"
    />
  </div>
</template>
