<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { getContactsApi } from '@/api/group'
import { Card, CardContent } from '@/components/ui/card'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { useUnreadStore } from '@/stores/unread'
import { toast } from 'sonner'
import { Mail, Loader2 } from 'lucide-vue-next'

const { t } = useI18n()
const router = useRouter()
const unread = useUnreadStore()

const contacts = ref<Array<{ userId: number; username: string }>>([])
const loading = ref(true)

onMounted(async () => {
  try {
    contacts.value = await getContactsApi()
    unread.fetchAll()
  } catch {
    toast.error('加载失败')
  } finally {
    loading.value = false
  }
})

function openChat(userId: number) {
  router.push({ name: 'MessagesDetail', params: { userId } })
}
</script>

<template>
  <div>
    <h2 class="text-2xl font-bold mb-6">{{ t('nav.messages') }}</h2>

    <div v-if="loading" class="flex justify-center py-20">
      <Loader2 class="h-6 w-6 animate-spin text-muted-foreground" />
    </div>

    <div v-else-if="contacts.length === 0" class="flex flex-col items-center justify-center py-20 text-muted-foreground">
      <Mail class="h-16 w-16 mb-4 opacity-30" />
      <p class="text-lg">还没有消息</p>
      <p class="text-sm mt-1">点击用户头像或个人资料可以发起私聊</p>
    </div>

    <div v-else class="space-y-2">
      <Card
        v-for="c in contacts"
        :key="c.userId"
        class="cursor-pointer hover:bg-accent/50 transition-colors relative"
        @click="openChat(c.userId)"
      >
        <CardContent class="flex items-center gap-3 py-3">
          <Avatar class="h-10 w-10">
            <AvatarFallback>{{ c.username.charAt(0).toUpperCase() }}</AvatarFallback>
          </Avatar>
          <div class="flex-1 min-w-0">
            <p class="font-medium">{{ c.username }}</p>
            <p class="text-xs text-muted-foreground">点击开始聊天</p>
          </div>
          <Badge
            v-if="unread.privateMessages[c.userId] > 0"
            class="h-5 min-w-5 rounded-full px-1.5 text-[11px] leading-none flex items-center justify-center"
          >
            {{ unread.privateMessages[c.userId] > 99 ? '99+' : unread.privateMessages[c.userId] }}
          </Badge>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
