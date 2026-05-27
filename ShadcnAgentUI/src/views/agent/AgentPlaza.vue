<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAgentStore } from '@/stores/agent'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import AgentIcon from '@/components/AgentIcon.vue'
import { MessageSquare, Users } from 'lucide-vue-next'

const router = useRouter()
const agentStore = useAgentStore()
const loading = ref(true)

onMounted(async () => {
  loading.value = true
  await agentStore.fetchPlazaAgents()
  loading.value = false
})

function goToChat(agentId: number) {
  router.push({ name: 'Chat', query: { agentId: agentId.toString() } })
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader :title="$t('pageTitle.agentPlaza')" />

    <div v-if="loading" class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
      <div v-for="i in 6" :key="i">
        <Card><CardContent class="p-6"><Skeleton class="h-12 w-12 rounded-full mb-4" /><Skeleton class="h-4 w-32 mb-2" /><Skeleton class="h-3 w-48" /></CardContent></Card>
      </div>
    </div>

    <EmptyState v-else-if="agentStore.plazaAgents.length === 0" :icon="Users" :title="$t('agent.noPlazaAgents')" :description="$t('agent.plaza')" />

    <div v-else class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
      <Card v-for="agent in agentStore.plazaAgents" :key="agent.id" class="cursor-pointer hover:shadow-md transition-shadow" @click="goToChat(agent.id)">
        <CardContent class="p-5">
          <div class="flex items-center gap-3 mb-3">
            <div class="flex h-12 w-12 items-center justify-center rounded-full bg-primary/10 text-primary overflow-hidden">
              <AgentIcon v-if="agent.icon || agent.avatar" :icon="agent.icon" :avatar="agent.avatar" class="h-6 w-6" />
              <span v-else class="text-lg font-bold">{{ agent.name.charAt(0).toUpperCase() }}</span>
            </div>
            <div>
              <h3 class="font-semibold">{{ agent.name }}</h3>
              <p class="text-xs text-muted-foreground line-clamp-1">{{ agent.description || '' }}</p>
            </div>
          </div>
          <div v-if="agent.tags?.length" class="flex flex-wrap gap-1 mb-3">
            <Badge v-for="tag in agent.tags.slice(0, 3)" :key="tag" variant="outline" class="text-xs">{{ tag }}</Badge>
          </div>
          <Button variant="outline" size="sm" class="w-full" @click.stop="goToChat(agent.id)">
            <MessageSquare class="mr-2 h-3 w-3" /> {{ $t('agent.startChat') }}
          </Button>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
