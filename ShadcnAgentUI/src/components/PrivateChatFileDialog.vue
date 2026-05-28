<!--
 * PrivateChatFileDialog.vue — 私聊文件管理对话框
 *
 * 功能：在私聊会话中查看、上传、搜索、下载文件。
 * 文件按对话上下文隔离（contextType=PRIVATE + contextId=对方用户ID）。
 *
 * @component PrivateChatFileDialog
-->

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { listMyFilesApi, uploadFileApi, downloadFileApi } from '@/api/file'
import type { FileRecord } from '@/types/api'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { toast } from 'sonner'
import { Download, Search, ArrowUpDown, Upload, FileIcon, Loader2 } from 'lucide-vue-next'

/** 组件属性 */
const props = defineProps<{
  /** 对话上下文类型，私聊固定为 'PRIVATE' */
  contextType: string
  /** 对话上下文 ID，即对方的用户 ID */
  contextId: number
  /** 对话框是否打开 */
  open: boolean
}>()

/** 组件事件 */
const emit = defineEmits<{
  /** 对话框打开状态变化 */
  (e: 'update:open', val: boolean): void
}>()

/** 当前会话的文件列表 */
const files = ref<FileRecord[]>([])
/** 是否正在加载文件列表 */
const loading = ref(true)
/** 搜索框文本 */
const searchText = ref('')
/** 排序字段：uploadedAt（上传时间） 或 fileSize（文件大小） */
const sortField = ref<'uploadedAt' | 'fileSize'>('uploadedAt')
/** 排序方向：desc（降序） 或 asc（升序） */
const sortOrder = ref<'desc' | 'asc'>('desc')
/** 是否正在上传文件 */
const uploading = ref(false)
/** 隐藏的文件选择 input 引用 */
const fileInputRef = ref<HTMLInputElement | null>(null)

onMounted(() => loadFiles())

/** 对话框打开时重新加载文件列表 */
watch(() => props.open, (val) => {
  if (val) loadFiles()
})

/** 从后端加载当前会话的文件列表 */
async function loadFiles() {
  loading.value = true
  try {
    files.value = await listMyFilesApi(props.contextType, props.contextId)
  } catch {
    toast.error('加载文件失败')
  } finally {
    loading.value = false
  }
}

/** 按搜索文本过滤 + 按字段排序后的文件列表 */
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

/** 切换排序字段或方向 */
function toggleSort(field: 'uploadedAt' | 'fileSize') {
  if (sortField.value === field) {
    sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc'
  } else {
    sortField.value = field
    sortOrder.value = 'desc'
  }
}

/** 下载文件（触发浏览器下载） */
async function downloadFile(file: FileRecord) {
  try {
    const blob = await downloadFileApi(file.id)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = file.originalName
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    toast.error('下载失败')
  }
}

/** 触发隐藏的文件选择 input */
function triggerFileSelect() {
  fileInputRef.value?.click()
}

/** 处理文件选择上传 */
async function handleUpload(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    await uploadFileApi(file, props.contextType, props.contextId)
    toast.success('上传成功')
    await loadFiles()
  } catch {
    toast.error('上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

/** 格式化文件大小（B/KB/MB） */
function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

/** 格式化日期为本地日期字符串 */
function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString()
}

/** 关闭对话框 */
function close() {
  emit('update:open', false)
}
</script>

<template>
  <Dialog :open="open" @update:open="close">
    <DialogContent class="max-w-2xl">
      <DialogHeader>
        <DialogTitle>私聊文件</DialogTitle>
      </DialogHeader>

      <!-- 工具栏：搜索 + 上传 -->
      <div class="flex items-center gap-2 py-2">
        <div class="relative flex-1">
          <Search class="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input v-model="searchText" placeholder="搜索文件名..." class="pl-8 h-9" />
        </div>
        <Button variant="outline" size="sm" :disabled="uploading" @click="triggerFileSelect">
          <Upload class="h-4 w-4 mr-1" />
          {{ uploading ? '上传中...' : '上传' }}
        </Button>
        <input ref="fileInputRef" type="file" class="hidden" @change="handleUpload" />
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