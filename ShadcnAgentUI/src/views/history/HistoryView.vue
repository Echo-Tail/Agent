<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useChatStore } from '@/stores/chat'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import SearchInput from '@/components/SearchInput.vue'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  FolderPlus,
  Folder,
  FolderOpen,
  MoreHorizontal,
  MessageSquare,
  Trash2,
  MoveRight,
  CheckSquare,
  Square,
  Loader2,
  History,
} from 'lucide-vue-next'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { toast } from 'sonner'

const { t, locale } = useI18n()
const router = useRouter()
const chat = useChatStore()

const searchQuery = ref('')
const selectedFolderId = ref<number | undefined>(undefined)
const selectedSessionId = ref<number | null>(null)
const selectMode = ref(false)
const selectedIds = ref<number[]>([])
const sessionsLoading = ref(false)

// Create folder
const showCreateFolder = ref(false)
const newFolderName = ref('')
const creatingFolder = ref(false)

// Rename folder
const showRenameFolder = ref(false)
const renameTarget = ref<number | null>(null)
const renameName = ref('')
const renamingFolder = ref(false)

// Delete folder
const showDeleteFolder = ref(false)
const deleteFolderTarget = ref<number | null>(null)
const deleteFolderMode = ref<'folder-only' | 'both'>('both')

// Delete session
const showDeleteSession = ref(false)
const deleteSessionTarget = ref<number | null>(null)

// Batch delete
const showBatchDelete = ref(false)

const filteredSessions = computed(() => {
  let list = chat.sessions
  if (selectedFolderId.value !== undefined) {
    list = list.filter((s) => s.folderId === selectedFolderId.value)
  }
  if (searchQuery.value.trim()) {
    const q = searchQuery.value.trim().toLowerCase()
    list = list.filter((s) => s.title.toLowerCase().includes(q))
  }
  return list
})

const allSelected = computed(() =>
  filteredSessions.value.length > 0 &&
  selectedIds.value.length === filteredSessions.value.length,
)

onMounted(async () => {
  sessionsLoading.value = true
  await Promise.all([chat.fetchSessions(), chat.fetchFolders()])
  sessionsLoading.value = false
})

function selectFolder(folderId: number | undefined) {
  selectedFolderId.value = folderId
}

function continueSession(session: { id: number; agentId: number }) {
  router.push({ name: 'Chat', query: { sessionId: session.id.toString(), agentId: session.agentId.toString() } })
}

// ---- Session Actions ----

function confirmDeleteSession(id: number) {
  deleteSessionTarget.value = id
  showDeleteSession.value = true
}

async function handleDeleteSession() {
  if (deleteSessionTarget.value === null) return
  const ok = await chat.removeSession(deleteSessionTarget.value)
  if (ok) toast.success(t('toast.deleteSuccess'))
  else toast.error(t('error.deleteFailed'))
  showDeleteSession.value = false
  deleteSessionTarget.value = null
}

async function moveSession(sessionId: number, folderId: number) {
  const ok = await chat.updateSession(sessionId, { folderId: folderId === -2 ? null : folderId })
  if (ok) toast.success(t('toast.moveSuccess'))
  else toast.error(t('error.moveFailed'))
}

// ---- Multi-select ----

function toggleSelectMode() {
  selectMode.value = !selectMode.value
  if (!selectMode.value) selectedIds.value = []
}

function toggleSelect(id: number) {
  const idx = selectedIds.value.indexOf(id)
  if (idx >= 0) selectedIds.value.splice(idx, 1)
  else selectedIds.value.push(id)
}

function selectAll() {
  selectedIds.value = filteredSessions.value.map((s) => s.id)
}

function deselectAll() {
  selectedIds.value = []
}

async function batchMoveToFolder(folderId: number) {
  const targetFolderId = folderId === -2 ? null : folderId
  let hasError = false
  for (const id of selectedIds.value) {
    const ok = await chat.updateSession(id, { folderId: targetFolderId })
    if (!ok) hasError = true
  }
  if (hasError) toast.warning(t('error.moveFailed'))
  else toast.success(t('toast.batchMoveSuccess', { count: selectedIds.value.length }))
  selectedIds.value = []
  selectMode.value = false
}

async function handleBatchDelete() {
  let successCount = 0
  for (const id of selectedIds.value) {
    const ok = await chat.removeSession(id)
    if (ok) successCount++
  }
  toast.success(t('toast.batchDeleteSuccess', { count: successCount }))
  selectedIds.value = []
  selectMode.value = false
  showBatchDelete.value = false
}

// ---- Folder Operations ----

function startCreateFolder() {
  newFolderName.value = ''
  showCreateFolder.value = true
}

async function handleCreateFolder() {
  if (!newFolderName.value.trim()) return
  creatingFolder.value = true
  const ok = await chat.addFolder(newFolderName.value.trim())
  if (ok) toast.success(t('toast.createFolderSuccess'))
  else toast.error(t('error.operationFailed'))
  creatingFolder.value = false
  showCreateFolder.value = false
}

function startRenameFolder(folderId: number) {
  const folder = chat.folders.find((f) => f.id === folderId)
  if (!folder) return
  renameTarget.value = folderId
  renameName.value = folder.name
  showRenameFolder.value = true
}

async function handleRenameFolder() {
  if (!renameName.value.trim() || renameTarget.value === null) return
  renamingFolder.value = true
  const ok = await chat.renameFolder(renameTarget.value, renameName.value.trim())
  if (ok) toast.success(t('toast.renameFolderSuccess'))
  else toast.error(t('error.operationFailed'))
  renamingFolder.value = false
  showRenameFolder.value = false
  renameTarget.value = null
}

function confirmDeleteFolder(folderId: number) {
  deleteFolderTarget.value = folderId
  const sessionsInFolder = chat.sessions.filter((s) => s.folderId === folderId)
  if (sessionsInFolder.length > 0) {
    showDeleteFolder.value = true
    deleteFolderMode.value = 'folder-only'
  } else {
    showDeleteFolder.value = true
    deleteFolderMode.value = 'both'
  }
}

async function handleDeleteFolder() {
  if (deleteFolderTarget.value === null) return
  const folderId = deleteFolderTarget.value
  if (deleteFolderMode.value === 'both') {
    const sessionsInFolder = chat.sessions.filter((s) => s.folderId === folderId)
    for (const s of sessionsInFolder) {
      await chat.removeSession(s.id)
    }
  }
  const ok = await chat.removeFolder(folderId)
  if (ok) {
    toast.success(t('toast.deleteFolderSuccess'))
    if (selectedFolderId.value === folderId) selectedFolderId.value = undefined
    await chat.fetchSessions()
  } else {
    toast.error(t('error.deleteFailed'))
  }
  showDeleteFolder.value = false
  deleteFolderTarget.value = null
}

function formatTime(timestamp: string) {
  const d = new Date(timestamp)
  return d.toLocaleString(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US', {
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

</script>

<template>
  <div class="flex gap-0 h-[calc(100vh-8rem)] -m-6">
    <!-- Left: Folders -->
    <div class="w-52 shrink-0 border-r border-border flex flex-col bg-muted/20">
      <div class="flex items-center justify-between px-4 py-3 border-b border-border">
        <span class="text-sm font-semibold">{{ $t('session.folders') }}</span>
        <Button variant="ghost" size="icon" class="h-7 w-7" @click="startCreateFolder" :title="$t('session.newFolder')">
          <FolderPlus class="h-4 w-4" />
        </Button>
      </div>
      <div class="flex-1 overflow-y-auto py-1">
        <button
          class="flex items-center gap-2 w-full px-4 py-2 text-sm hover:bg-muted transition-colors"
          :class="selectedFolderId === undefined ? 'bg-muted font-medium text-foreground' : 'text-muted-foreground'"
          @click="selectFolder(undefined)"
        >
          <FolderOpen class="h-4 w-4 shrink-0" />
          <span class="truncate">{{ $t('session.allSessions') }}</span>
        </button>
        <div
          v-for="folder in chat.folders"
          :key="folder.id"
          class="group flex items-center gap-1 px-2"
        >
          <button
            class="flex items-center gap-2 flex-1 px-2 py-2 text-sm rounded-md hover:bg-muted transition-colors"
            :class="selectedFolderId === folder.id ? 'bg-muted font-medium text-foreground' : 'text-muted-foreground'"
            @click="selectFolder(folder.id)"
          >
            <Folder class="h-4 w-4 shrink-0" />
            <span class="truncate">{{ folder.name }}</span>
          </button>
          <DropdownMenu>
            <DropdownMenuTrigger as-child>
              <Button variant="ghost" size="icon" class="h-7 w-7 opacity-0 group-hover:opacity-100 shrink-0">
                <MoreHorizontal class="h-3.5 w-3.5" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem @click="startRenameFolder(folder.id)">{{ $t('session.renameFolder') }}</DropdownMenuItem>
              <DropdownMenuItem class="text-destructive" @click="confirmDeleteFolder(folder.id)">{{ $t('common.delete') }}</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>
    </div>

    <!-- Right: Session List -->
    <div class="flex-1 flex flex-col min-w-0">
      <div class="flex items-center justify-between px-4 py-3 border-b border-border">
        <span class="text-sm font-semibold">
          {{ $t('session.title') }}
          <span v-if="selectedFolderId !== undefined" class="text-muted-foreground font-normal">
            · {{ chat.folders.find(f => f.id === selectedFolderId)?.name }}
          </span>
        </span>
        <div class="flex items-center gap-1">
          <Button v-if="!selectMode" variant="ghost" size="sm" class="h-7 text-xs" @click="toggleSelectMode">
            <CheckSquare class="h-3.5 w-3.5 mr-1" />{{ $t('common.select') }}
          </Button>
          <template v-else>
            <Button variant="ghost" size="sm" class="h-7 text-xs" @click="allSelected ? deselectAll() : selectAll()">
              {{ allSelected ? $t('common.deselectAll') : $t('common.selectAll') }}
            </Button>
            <Button variant="ghost" size="sm" class="h-7 text-xs" @click="toggleSelectMode">{{ $t('common.cancel') }}</Button>
          </template>
        </div>
      </div>

      <!-- Search -->
      <div class="px-4 py-2 border-b border-border">
        <SearchInput id="session-search" name="session-search" v-model="searchQuery" :placeholder="$t('session.searchPlaceholder')" input-class="pl-8 text-sm" />
      </div>

      <!-- Batch actions -->
      <div v-if="selectMode && selectedIds.length" class="flex items-center justify-between px-4 py-2 bg-muted/30 border-b border-border text-sm">
        <span class="text-muted-foreground">{{ $t('common.select') }} {{ selectedIds.length }} {{ $t('common.records') }}</span>
        <div class="flex items-center gap-2">
          <DropdownMenu>
            <DropdownMenuTrigger as-child>
              <Button variant="outline" size="sm" class="h-7 text-xs">
                <MoveRight class="h-3 w-3 mr-1" />{{ $t('session.moveToFolder') }}
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent>
              <DropdownMenuItem @click="batchMoveToFolder(-2)">{{ $t('common.uncategorized') }}</DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem v-for="f in chat.folders" :key="f.id" @click="batchMoveToFolder(f.id)">
                {{ f.name }}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
          <Button variant="destructive" size="sm" class="h-7 text-xs" @click="showBatchDelete = true">
            <Trash2 class="h-3 w-3 mr-1" />{{ $t('common.delete') }}
          </Button>
        </div>
      </div>

      <!-- Loading -->
      <div v-if="sessionsLoading" class="flex-1 flex items-center justify-center">
        <Loader2 class="h-6 w-6 animate-spin text-muted-foreground" />
      </div>

      <!-- Session list -->
      <div v-else-if="filteredSessions.length" class="flex-1 overflow-y-auto divide-y divide-border">
        <div
          v-for="s in filteredSessions"
          :key="s.id"
          class="group px-4 py-3 hover:bg-muted/50 transition-colors cursor-pointer"
          :class="{ 'bg-muted': selectMode ? selectedIds.includes(s.id) : s.id === selectedSessionId }"
          @click="selectMode ? toggleSelect(s.id) : (selectedSessionId = s.id)"
        >
          <div class="flex items-start gap-3">
            <button
              v-if="selectMode"
              class="mt-0.5 shrink-0"
              @click.stop="toggleSelect(s.id)"
            >
              <CheckSquare v-if="selectedIds.includes(s.id)" class="h-4 w-4 text-primary" />
              <Square v-else class="h-4 w-4 text-muted-foreground" />
            </button>
            <div class="flex-1 min-w-0">
              <div class="flex items-center justify-between gap-2">
                <span class="text-sm font-medium truncate">{{ s.title }}</span>
                <span class="text-xs text-muted-foreground shrink-0">{{ $t('session.messageCount', { count: s.messageCount }) }}</span>
              </div>
              <div class="text-xs text-muted-foreground mt-0.5">
                {{ formatTime(s.updatedAt) }}
              </div>
            </div>
          </div>
          <!-- Actions (visible on hover, when not in select mode) -->
          <div v-if="!selectMode" class="flex items-center gap-1 mt-2 opacity-0 group-hover:opacity-100 transition-opacity">
            <Button variant="ghost" size="sm" class="h-6 text-xs" @click.stop="continueSession(s)">
              <MessageSquare class="h-3 w-3 mr-1" />{{ $t('session.continueChat') }}
            </Button>
            <DropdownMenu>
              <DropdownMenuTrigger as-child>
                <Button variant="ghost" size="sm" class="h-6 text-xs">
                  <MoveRight class="h-3 w-3 mr-1" />{{ $t('common.move') }}
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent>
                <DropdownMenuItem @click.stop="moveSession(s.id, -2)">{{ $t('common.uncategorized') }}</DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem v-for="f in chat.folders" :key="f.id" @click.stop="moveSession(s.id, f.id)">
                  {{ f.name }}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
            <Button variant="ghost" size="sm" class="h-6 text-xs text-destructive hover:text-destructive" @click.stop="confirmDeleteSession(s.id)">
              <Trash2 class="h-3 w-3" />
            </Button>
          </div>
        </div>
      </div>

      <!-- Empty -->
      <div v-else class="flex-1 flex flex-col items-center justify-center text-muted-foreground">
        <History class="h-10 w-10 mb-3 opacity-50" />
        <p class="text-sm">{{ searchQuery ? $t('error.noSessionMatch') : $t('error.noSessions') }}</p>
      </div>
    </div>

    <!-- Create Folder Dialog -->
    <Dialog :open="showCreateFolder" @update:open="showCreateFolder = $event">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{{ $t('session.newFolder') }}</DialogTitle>
        </DialogHeader>
        <Input
          id="folder-name"
          name="folder-name"
          v-model="newFolderName"
          :placeholder="$t('placeholder.folderName')"
          @keydown.enter="handleCreateFolder"
        />
        <DialogFooter>
          <Button variant="outline" @click="showCreateFolder = false">{{ $t('common.cancel') }}</Button>
          <Button :disabled="!newFolderName.trim() || creatingFolder" @click="handleCreateFolder">
            <Loader2 v-if="creatingFolder" class="mr-2 h-4 w-4 animate-spin" />
            {{ $t('common.create') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Rename Folder Dialog -->
    <Dialog :open="showRenameFolder" @update:open="showRenameFolder = $event">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{{ $t('session.renameFolder') }}</DialogTitle>
        </DialogHeader>
        <Input
          id="folder-rename"
          name="folder-rename"
          v-model="renameName"
          :placeholder="$t('placeholder.folderName')"
          @keydown.enter="handleRenameFolder"
        />
        <DialogFooter>
          <Button variant="outline" @click="showRenameFolder = false">{{ $t('common.cancel') }}</Button>
          <Button :disabled="!renameName.trim() || renamingFolder" @click="handleRenameFolder">
            <Loader2 v-if="renamingFolder" class="mr-2 h-4 w-4 animate-spin" />
            {{ $t('common.save') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Delete Folder Dialog -->
    <Dialog :open="showDeleteFolder" @update:open="showDeleteFolder = $event">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{{ $t('dialog.deleteFolder.title') }}</DialogTitle>
          <DialogDescription>
            {{ $t('dialog.deleteFolder.desc', { count: chat.sessions.filter(s => s.folderId === deleteFolderTarget).length }) }}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter class="gap-2">
          <Button variant="outline" @click="showDeleteFolder = false">{{ $t('common.cancel') }}</Button>
          <Button variant="secondary" @click="deleteFolderMode = 'folder-only'; handleDeleteFolder()">
            {{ $t('dialog.deleteFolder.folderOnly') }}
          </Button>
          <Button variant="destructive" @click="deleteFolderMode = 'both'; handleDeleteFolder()">
            {{ $t('dialog.deleteFolder.deleteBoth') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <ConfirmDialog
      :open="showDeleteSession"
      @update:open="showDeleteSession = $event"
      :title="t('dialog.deleteConfirm.title')"
      :description="t('dialog.deleteConfirm.desc', { entity: t('session.title') })"
      :confirm-text="t('common.delete')"
      @confirm="handleDeleteSession"
    />

    <ConfirmDialog
      :open="showBatchDelete"
      @update:open="showBatchDelete = $event"
      :title="t('dialog.deleteConfirm.title')"
      :description="t('dialog.batchDeleteConfirm.desc', { count: selectedIds.length })"
      :confirm-text="t('common.delete')"
      @confirm="handleBatchDelete"
    />
  </div>
</template>
