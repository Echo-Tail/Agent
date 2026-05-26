<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { listGroupFilesApi } from '@/api/group'
import { uploadGroupFileApi } from '@/api/group'
import type { GroupFile } from '@/types/group'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { toast } from 'sonner'
import { Download, Search, ArrowUpDown, Upload, FileIcon, Loader2 } from 'lucide-vue-next'

const props = defineProps<{
  groupId: number
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'update:open', val: boolean): void
}>()

const files = ref<GroupFile[]>([])
const loading = ref(true)
const searchText = ref('')
const sortField = ref<'uploadedAt' | 'fileSize'>('uploadedAt')
const sortOrder = ref<'desc' | 'asc'>('desc')
const uploading = ref(false)

onMounted(() => loadFiles())

watch(() => props.open, (val) => {
  if (val) loadFiles()
})

async function loadFiles() {
  loading.value = true
  try {
    files.value = await listGroupFilesApi(props.groupId)
  } catch {
    toast.error('加载文件失败')
  } finally {
    loading.value = false
  }
}

const filteredFiles = computed(() => {
  let list = files.value
  if (searchText.value.trim()) {
    const kw = searchText.value.trim().toLowerCase()
    list = list.filter(f => f.originalName.toLowerCase().includes(kw))
  }
  return [...list].sort((a, b) => {
    let cmp: number
    if (sortField.value === 'uploadedAt') {
      cmp = new Date(a.uploadedAt).getTime() - new Date(b.uploadedAt).getTime()
    } else {
      cmp = a.fileSize - b.fileSize
    }
    return sortOrder.value === 'desc' ? -cmp : cmp
  })
})

function toggleSort(field: 'uploadedAt' | 'fileSize') {
  if (sortField.value === field) {
    sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc'
  } else {
    sortField.value = field
    sortOrder.value = 'desc'
  }
}

function downloadFile(file: GroupFile) {
  window.open(`/v1/groups/${props.groupId}/files/${file.id}/download`, '_blank')
}

async function handleUpload(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    await uploadGroupFileApi(props.groupId, file)
    toast.success('上传成功')
    await loadFiles()
  } catch {
    toast.error('上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString()
}

function close() {
  emit('update:open', false)
}
</script>

<template>
  <Dialog :open="open" @update:open="close">
    <DialogContent class="max-w-2xl">
      <DialogHeader>
        <DialogTitle>群文件</DialogTitle>
      </DialogHeader>

      <!-- 工具栏：搜索 + 上传 -->
      <div class="flex items-center gap-2 py-2">
        <div class="relative flex-1">
          <Search class="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input v-model="searchText" placeholder="搜索文件名..." class="pl-8 h-9" />
        </div>
        <Button variant="outline" size="sm" :disabled="uploading" class="relative">
          <Upload class="h-4 w-4 mr-1" />
          {{ uploading ? '上传中...' : '上传' }}
          <input type="file" class="absolute inset-0 opacity-0 cursor-pointer" @change="handleUpload" />
        </Button>
      </div>

      <!-- 排序按钮 -->
      <div class="flex gap-2 text-xs text-muted-foreground pb-1">
        <button class="flex items-center gap-1 hover:text-foreground" @click="toggleSort('uploadedAt')">
          <ArrowUpDown class="h-3 w-3" /> 时间
          <span v-if="sortField === 'uploadedAt'" class="text-primary">{{ sortOrder === 'desc' ? '↓' : '↑' }}</span>
        </button>
        <button class="flex items-center gap-1 hover:text-foreground" @click="toggleSort('fileSize')">
          <ArrowUpDown class="h-3 w-3" /> 大小
          <span v-if="sortField === 'fileSize'" class="text-primary">{{ sortOrder === 'desc' ? '↓' : '↑' }}</span>
        </button>
      </div>

      <!-- 文件列表 -->
      <div v-if="loading" class="flex justify-center py-10">
        <Loader2 class="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
      <div v-else-if="filteredFiles.length === 0" class="py-10 text-center text-muted-foreground text-sm">
        暂无文件
      </div>
      <div v-else class="max-h-80 overflow-y-auto space-y-1">
        <div
          v-for="f in filteredFiles"
          :key="f.id"
          class="flex items-center gap-3 rounded-md px-3 py-2 hover:bg-accent/50 group"
        >
          <FileIcon class="h-5 w-5 shrink-0 text-muted-foreground" />
          <div class="flex-1 min-w-0">
            <p class="text-sm truncate">{{ f.originalName }}</p>
            <p class="text-xs text-muted-foreground">
              {{ formatSize(f.fileSize) }} · {{ formatDate(f.uploadedAt) }}
            </p>
          </div>
          <Button variant="ghost" size="icon" class="h-8 w-8 shrink-0 opacity-0 group-hover:opacity-100" title="下载" @click="downloadFile(f)">
            <Download class="h-4 w-4" />
          </Button>
        </div>
      </div>
    </DialogContent>
  </Dialog>
</template>
