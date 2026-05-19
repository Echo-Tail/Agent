<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { useRouter } from 'vue-router'
import { useDialog, useMessage, NDropdown, NButton, NIcon } from 'naive-ui'
import { useChatStore } from '../../stores/chat'
import type { SessionSummary } from '../../types/session'

const router = useRouter()
const dialog = useDialog()
const message = useMessage()
const chat = useChatStore()

const searchQuery = ref('')
const selectedFolderId = ref<number | undefined>(undefined)
const selectedSessionId = ref<number | null>(null)
const showCreateFolder = ref(false)
const newFolderName = ref('')

const selectMode = ref(false)
const selectedIds = ref<number[]>([])

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

onMounted(async () => {
  await Promise.all([chat.fetchSessions(), chat.fetchFolders()])
})

function selectFolder(folderId: number | undefined) {
  selectedFolderId.value = folderId
}

function selectSession(session: SessionSummary) {
  selectedSessionId.value = session.id
}

function deleteSession(session: SessionSummary) {
  dialog.warning({
    title: '确认删除',
    content: `确定要删除会话「${session.title}」吗？`,
    positiveText: '删除',
    negativeText: '取消',
    positiveButtonProps: { type: 'error' },
    onPositiveClick: async () => {
      const ok = await chat.removeSession(session.id)
      if (ok) {
        message.success('已删除')
        if (selectedSessionId.value === session.id) {
          selectedSessionId.value = null
        }
      } else {
        message.error('删除失败')
      }
    },
  })
}

function continueSession(session: SessionSummary) {
  const query: Record<string, string> = { sessionId: session.id.toString() }
  query.agentId = session.agentId.toString()
  router.push({ name: 'Chat', query })
}

async function moveSession(session: SessionSummary, folderId: number) {
  const ok = await chat.updateSession(session.id, {
    folderId: folderId === -2 ? null : folderId,
  })
  if (ok) {
    message.success('已移动')
  } else {
    message.error('移动失败')
  }
}

function folderOptions() {
  const items = chat.folders.map((f) => ({
    label: f.name,
    key: f.id,
  }))
  items.unshift({ label: '不分类', key: -2 })
  return items
}

/* ====== 多选模式 ====== */

function toggleSelectMode() {
  selectMode.value = !selectMode.value
  if (!selectMode.value) selectedIds.value = []
}

function toggleSelect(id: number) {
  const idx = selectedIds.value.indexOf(id)
  if (idx >= 0) {
    selectedIds.value.splice(idx, 1)
  } else {
    selectedIds.value.push(id)
  }
}

function selectAll() {
  selectedIds.value = filteredSessions.value.map((s) => s.id)
}

function deselectAll() {
  selectedIds.value = []
}

const allSelected = computed(() =>
  filteredSessions.value.length > 0 &&
  selectedIds.value.length === filteredSessions.value.length,
)

async function batchMoveToFolder(folderId: number) {
  const targetFolderId = folderId === -2 ? null : folderId
  let hasError = false
  for (const id of selectedIds.value) {
    const ok = await chat.updateSession(id, { folderId: targetFolderId })
    if (!ok) hasError = true
  }
  if (hasError) {
    message.warning('部分会话移动失败')
  } else {
    message.success(`已将 ${selectedIds.value.length} 条会话移动`)
  }
  selectedIds.value = []
  selectMode.value = false
}

/* ====== 文件夹操作 ====== */

function startCreateFolder() {
  newFolderName.value = ''
  showCreateFolder.value = true
}

async function handleCreateFolder() {
  if (!newFolderName.value.trim()) return
  const ok = await chat.addFolder(newFolderName.value.trim())
  if (ok) {
    message.success('文件夹创建成功')
  } else {
    message.error('创建失败')
  }
  showCreateFolder.value = false
}

/* ====== 文件夹重命名 & 删除 ====== */

const renameTarget = ref<number | null>(null)
const renameName = ref('')
const showRenameFolder = ref(false)

function renderMenuExtra(option: { key: unknown }) {
  if (option.key === -1) return null

  const dropdownOptions = [
    { label: '重命名', key: 'rename' },
    { label: '删除', key: 'delete' },
  ]

  return h(
    NDropdown,
    {
      options: dropdownOptions,
      trigger: 'click',
      onSelect: (actionKey: string) => {
        if (actionKey === 'rename') startRenameFolder(option.key as number)
        else if (actionKey === 'delete') confirmDeleteFolder(option.key as number)
      },
    },
    {
      default: () =>
        h(
          NButton,
          { quaternary: true, size: 'tiny', onClick: (e: Event) => e.stopPropagation() },
          {
            icon: () =>
              h(NIcon, null, {
                default: () =>
                  h('svg', { xmlns: 'http://www.w3.org/2000/svg', viewBox: '0 0 24 24', fill: 'currentColor', width: '14', height: '14' }, [
                    h('path', { d: 'M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z' }),
                  ]),
              }),
          },
        ),
    },
  )
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
  const ok = await chat.renameFolder(renameTarget.value, renameName.value.trim())
  if (ok) {
    message.success('重命名成功')
  } else {
    message.error('重命名失败')
  }
  showRenameFolder.value = false
  renameTarget.value = null
}

function confirmDeleteFolder(folderId: number) {
  const folder = chat.folders.find((f) => f.id === folderId)
  if (!folder) return

  const sessionsInFolder = chat.sessions.filter((s) => s.folderId === folderId)

  if (sessionsInFolder.length > 0) {
    dialog.info({
      title: '确认删除',
      content: `文件夹「${folder.name}」内有 ${sessionsInFolder.length} 条会话，如何处理？`,
      positiveText: '仅删除文件夹',
      negativeText: '同时删除会话',
      onPositiveClick: async () => {
        await chat.removeFolder(folderId)
        message.success('文件夹已删除')
        if (selectedFolderId.value === folderId) selectedFolderId.value = undefined
        await chat.fetchSessions()
      },
      onNegativeClick: async () => {
        for (const s of sessionsInFolder) {
          await chat.removeSession(s.id)
        }
        await chat.removeFolder(folderId)
        message.success(`文件夹及 ${sessionsInFolder.length} 条会话已删除`)
        if (selectedFolderId.value === folderId) selectedFolderId.value = undefined
        await chat.fetchSessions()
      },
    })
  } else {
    dialog.warning({
      title: '确认删除',
      content: `确定要删除文件夹「${folder.name}」吗？`,
      positiveText: '删除',
      negativeText: '取消',
      positiveButtonProps: { type: 'error' },
      onPositiveClick: async () => {
        await chat.removeFolder(folderId)
        message.success('文件夹已删除')
        if (selectedFolderId.value === folderId) selectedFolderId.value = undefined
        await chat.fetchSessions()
      },
    })
  }
}

function formatTime(t: string) {
  const d = new Date(t)
  const y = d.getFullYear()
  const m = d.getMonth() + 1
  const day = d.getDate()
  const h = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${y}年${m}月${day}日 ${h}:${min}`
}

const menuOptions = computed(() => {
  return [
    { label: '全部会话', key: -1 } as { label: string; key: number },
    ...chat.folders.map((f) => ({
      label: f.name,
      key: f.id,
    })),
  ]
})
</script>

<template>
  <div class="history-layout">
    <!-- Left: Folders -->
    <div class="folders-panel">
      <div class="panel-header">
        <span style="font-weight: 600; font-size: 14px;">文件夹</span>
        <n-button type="primary" quaternary size="tiny" @click="startCreateFolder" title="新建文件夹">
          <template #icon>
            <n-icon>
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                <path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>
              </svg>
            </n-icon>
          </template>
        </n-button>
      </div>

      <n-menu
        :value="selectedFolderId ?? -1"
        :options="menuOptions"
        :render-extra="renderMenuExtra"
        @update:value="(k: number) => selectFolder(k === -1 ? undefined : k)"
        style="flex: 1; overflow-y: auto;"
      />
    </div>

    <!-- Center: Session List -->
    <div class="sessions-panel">
      <div class="panel-header">
        <span style="font-weight: 600; font-size: 14px;">历史会话</span>
        <n-button v-if="!selectMode" size="tiny" quaternary @click="toggleSelectMode()">选择</n-button>
        <template v-else>
          <n-button size="tiny" quaternary @click="allSelected ? deselectAll() : selectAll()">
            {{ allSelected ? '取消全选' : '全选' }}
          </n-button>
          <n-button size="tiny" quaternary @click="toggleSelectMode()">取消</n-button>
        </template>
      </div>

      <n-input
        v-model:value="searchQuery"
        placeholder="搜索会话..."
        clearable
        size="small"
        style="margin: 8px; width: auto;"
      />

      <!-- 批量操作栏 -->
      <div v-if="selectMode && selectedIds.length" class="batch-bar">
        <span style="font-size: 13px; color: #666;">已选 {{ selectedIds.length }} 条</span>
        <n-dropdown trigger="click" :options="folderOptions()" @select="batchMoveToFolder">
          <n-button size="tiny" secondary>移动到文件夹</n-button>
        </n-dropdown>
      </div>

      <n-spin v-if="chat.sessionLoading" style="margin-top: 40px;" />

      <n-list v-else-if="filteredSessions.length" hoverable style="flex: 1; overflow-y: auto;">
        <n-list-item
          v-for="s in filteredSessions"
          :key="s.id"
          :class="{ selected: s.id === selectedSessionId && !selectMode }"
          @click="selectMode ? toggleSelect(s.id) : selectSession(s)"
          style="cursor: pointer;"
        >
          <template #default>
            <div style="display: flex; align-items: flex-start; gap: 8px;">
              <n-checkbox v-if="selectMode" :checked="selectedIds.includes(s.id)" @click.stop @update:checked="toggleSelect(s.id)" />
              <div style="flex: 1; min-width: 0;">
                <div style="font-size: 14px; font-weight: 500; margin-bottom: 2px;">{{ s.title }}</div>
                <div style="font-size: 11px; color: #bbb; margin-top: 2px;">
                  {{ formatTime(s.updatedAt) }} · {{ s.messageCount }} 条消息
                </div>
              </div>
            </div>
            <div v-if="!selectMode" class="session-actions" @click.stop>
              <n-button size="tiny" type="primary" quaternary @click="continueSession(s)">
                <template #icon>
                  <n-icon><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg></n-icon>
                </template>
                继续对话
              </n-button>
              <n-dropdown trigger="click" :options="folderOptions()" @select="(k: number) => moveSession(s, k)">
                <n-button size="tiny" quaternary>
                  <template #icon>
                    <n-icon><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"/></svg></n-icon>
                  </template>
                  移动到
                </n-button>
              </n-dropdown>
              <n-button size="tiny" type="error" quaternary @click="deleteSession(s)">
                <template #icon>
                  <n-icon size="14">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>
                  </n-icon>
                </template>
              </n-button>
            </div>
          </template>
        </n-list-item>
      </n-list>

      <n-empty v-else description="没有找到会话" style="margin-top: 40px;" />
    </div>

    <!-- Create Folder Modal -->
    <n-modal v-model:show="showCreateFolder" title="新建文件夹"
      preset="dialog" positive-text="创建" negative-text="取消"
      @positive-click="handleCreateFolder" @negative-click="showCreateFolder = false">
      <n-input v-model:value="newFolderName" placeholder="文件夹名称" @keydown.enter="handleCreateFolder" />
    </n-modal>

    <!-- Rename Folder Modal -->
    <n-modal v-model:show="showRenameFolder" title="重命名文件夹" preset="dialog" positive-text="保存" negative-text="取消"
      @positive-click="handleRenameFolder" @negative-click="showRenameFolder = false">
      <n-input v-model:value="renameName" placeholder="文件夹名称" @keydown.enter="handleRenameFolder" />
    </n-modal>
  </div>
</template>

<style scoped>
.history-layout {
  display: flex;
  gap: 0;
  position: absolute;
  inset: 0;
  margin: 0;
}

.folders-panel {
  width: 200px;
  border-right: 1px solid var(--border-color, #eee);
  display: flex;
  flex-direction: column;
  background: var(--panel-bg, #fafafa);
}

.sessions-panel {
  flex: 1;
  border-right: 1px solid var(--border-color, #eee);
  display: flex;
  flex-direction: column;
}

.panel-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color, #eee);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.batch-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  border-bottom: 1px solid var(--border-color, #eee);
  background: var(--panel-bg, #fafafa);
}

.session-actions {
  display: flex;
  gap: 4px;
  margin-top: 6px;
  opacity: 0;
  transition: opacity 0.15s;
}

.n-list-item:hover .session-actions {
  opacity: 1;
}

.n-list-item.selected {
  background-color: var(--selected-bg, #F0E2D8);
}
</style>
