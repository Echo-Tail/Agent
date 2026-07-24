<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ArrowRight, ImagePlus, Loader2, Plus } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { createImageCanvasSession, listImageCanvasSessions, type ImageCanvasSession } from '@/api/image-workflow'
import { toast } from 'vue-sonner'

const router = useRouter()
const sessions = ref<ImageCanvasSession[]>([])
const loading = ref(true)
const createOpen = ref(false)
const creating = ref(false)
const title = ref('')

onMounted(load)

async function load() {
  loading.value = true
  try {
    sessions.value = await listImageCanvasSessions()
  } catch {
    toast.error('画布会话加载失败')
  } finally {
    loading.value = false
  }
}

async function createSession() {
  if (!title.value.trim() || creating.value) return
  creating.value = true
  try {
    const session = await createImageCanvasSession(title.value.trim())
    createOpen.value = false
    title.value = ''
    await router.push({ name: 'ImageCanvas', params: { sessionId: session.id } })
  } catch {
    toast.error('创建会话失败')
  } finally {
    creating.value = false
  }
}

</script>

<template>
  <div class="mx-auto max-w-6xl space-y-8 px-6 py-8">
    <header class="flex items-end justify-between gap-4">
      <div>
        <h1 class="text-balance text-2xl font-semibold">图像创作画布</h1>
        <p class="mt-1.5 text-pretty text-sm text-muted-foreground">选择一个会话继续创作，或新建画布。</p>
      </div>
      <Button @click="createOpen = true"><Plus class="mr-2 h-4 w-4" />新建会话</Button>
    </header>

    <div v-if="loading" class="grid min-h-64 place-items-center text-muted-foreground">
      <Loader2 class="h-6 w-6 animate-spin" />
    </div>
    <div v-else-if="!sessions.length" class="grid min-h-72 place-items-center rounded-2xl border border-dashed">
      <div class="text-center">
        <span class="mx-auto grid h-12 w-12 place-items-center rounded-xl bg-primary/10 text-primary"><ImagePlus class="h-5 w-5" /></span>
        <h2 class="mt-4 font-medium">还没有画布会话</h2>
        <p class="mt-1 text-sm text-muted-foreground">创建会话后开始第一轮图像生成。</p>
        <Button size="sm" class="mt-5" @click="createOpen = true"><Plus class="mr-1 h-4 w-4" />新建会话</Button>
      </div>
    </div>
    <div v-else class="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3">
      <button
        v-for="session in sessions"
        :key="session.id"
        class="group flex min-h-20 items-center rounded-xl border bg-card px-5 text-left shadow-sm transition-[transform,box-shadow,border-color] duration-150 hover:-translate-y-px hover:border-primary/30 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        @click="router.push({ name: 'ImageCanvas', params: { sessionId: session.id } })"
      >
        <strong class="min-w-0 flex-1 truncate text-sm font-medium">{{ session.title }}</strong>
        <span class="ml-4 grid size-8 shrink-0 place-items-center rounded-lg bg-muted text-muted-foreground transition-transform duration-150 group-hover:translate-x-0.5">
          <ArrowRight class="size-4" />
        </span>
      </button>
    </div>

    <Dialog v-model:open="createOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>新建图像画布</DialogTitle>
          <DialogDescription>画布会自动保存节点位置、生成关系和任务状态。</DialogDescription>
        </DialogHeader>
        <form class="space-y-5" @submit.prevent="createSession">
          <Input v-model="title" maxlength="100" autofocus placeholder="例如：咖啡机主图设计" />
          <DialogFooter>
            <Button type="button" variant="outline" @click="createOpen = false">取消</Button>
            <Button type="submit" :disabled="!title.trim() || creating">
              <Loader2 v-if="creating" class="mr-2 h-4 w-4 animate-spin" />创建并进入
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  </div>
</template>
