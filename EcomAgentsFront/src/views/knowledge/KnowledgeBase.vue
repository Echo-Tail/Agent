<script setup lang="ts">
import { h, ref, computed, onMounted, onUnmounted, watch } from 'vue'
import type { DataTableColumn } from 'naive-ui'
import { useRoute, useRouter } from 'vue-router'
import { useMessage, useDialog, NButton, NSpace } from 'naive-ui'
import { useKnowledgeStore } from '../../stores/knowledge'
import type { KnowledgeBase } from '../../types/knowledge'
import DocPreview from '../../components/DocPreview.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const store = useKnowledgeStore()

// KB edit modal
const showKbModal = ref(false)
const editingKb = ref<{ id?: number; name: string; description: string }>({ name: '', description: '' })
const isEditMode = ref(false)
const savingKb = ref(false)

// Doc preview
const previewDoc = ref<(typeof store.documents)[number] | null>(null)
const showPreview = ref(false)

// Upload
const uploadLoading = ref(false)

const isDetail = computed(() => !!route.params.id)
const kbId = computed(() => Number(route.params.id))

const isUnmounted = ref(false)
onUnmounted(() => { isUnmounted.value = true })

onMounted(async () => {
  try {
    if (isDetail.value) {
      await store.loadKb(kbId.value)
    } else {
      await store.fetchKbs()
    }
  } catch {
    // handled in store
  }
})

watch(
  () => route.params.id,
  async (newId) => {
    if (isUnmounted.value) return
    try {
      if (newId) {
        store.clearCurrent()
        await store.loadKb(Number(newId))
      } else {
        store.clearCurrent()
        await store.fetchKbs()
      }
    } catch {
      // handled in store
    }
  },
)

// --- KB CRUD ---

function openCreate() {
  editingKb.value = { name: '', description: '' }
  isEditMode.value = false
  showKbModal.value = true
}

function openEdit(kb: KnowledgeBase) {
  editingKb.value = { id: kb.id, name: kb.name, description: kb.description || '' }
  isEditMode.value = true
  showKbModal.value = true
}

async function handleSaveKb() {
  if (!editingKb.value.name.trim()) {
    message.warning('请输入知识库名称')
    return
  }
  savingKb.value = true
  try {
    if (isEditMode.value && editingKb.value.id) {
      const ok = await store.updateKb(editingKb.value.id, {
        name: editingKb.value.name,
        description: editingKb.value.description || undefined,
      })
      if (ok) message.success('已更新')
      else message.error('更新失败')
    } else {
      const kb = await store.createKb({
        name: editingKb.value.name,
        description: editingKb.value.description || undefined,
      })
      message.success('创建成功')
      if (kb) {
        router.push({ name: 'KnowledgeDetail', params: { id: kb.id } })
      }
    }
    showKbModal.value = false
  } catch (e) {
    message.error(e instanceof Error ? e.message : '操作失败')
  } finally {
    savingKb.value = false
  }
}

function handleDeleteKb(kb: KnowledgeBase) {
  dialog.warning({
    title: '确认删除',
    content: `确定要删除知识库「${kb.name}」及其所有文档吗？`,
    positiveText: '删除',
    negativeText: '取消',
    positiveButtonProps: { type: 'error' },
    onPositiveClick: async () => {
      const ok = await store.removeKb(kb.id)
      if (ok) {
        message.success('已删除')
        if (isDetail.value) {
          router.push({ name: 'KnowledgeBase' })
        }
      } else {
        message.error('删除失败')
      }
    },
  })
}

// --- Documents ---

function handleCustomRequest(params: { file: { file?: File } }) {
  if (params.file.file) {
    handleUpload({ file: params.file.file })
  }
}

async function handleUpload({ file }: { file: File }) {
  uploadLoading.value = true
  try {
    const doc = await store.uploadDoc(kbId.value, file)
    message.success(`「${doc?.fileName || file.name}」上传成功`)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '上传失败')
  } finally {
    uploadLoading.value = false
  }
}

function handleDeleteDoc(docId: number) {
  dialog.warning({
    title: '确认删除',
    content: '确定要删除此文档吗？',
    positiveText: '删除',
    negativeText: '取消',
    positiveButtonProps: { type: 'error' },
    onPositiveClick: async () => {
      const ok = await store.removeDoc(kbId.value, docId)
      if (ok) message.success('已删除')
      else message.error('删除失败')
    },
  })
}

function openPreview(doc: (typeof store.documents)[number]) {
  previewDoc.value = doc
  showPreview.value = true
}

// --- Search ---

let searchTimer: ReturnType<typeof setTimeout> | undefined
function handleSearchInput(val: string) {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => store.search(val), 300)
}

const docColumns: DataTableColumn<typeof store.documents[number]>[] = [
  { title: '文件名', key: 'fileName', ellipsis: { tooltip: true }, render: (row) => row.fileName },
  { title: '格式', key: 'fileType', width: 80, render: (row) => row.fileType },
  { title: '大小', key: 'charCount', width: 110, render: (row) => formatSize(row.charCount) },
  { title: '上传时间', key: 'uploadedAt', width: 160, render: (row) => formatDate(row.uploadedAt) },
  {
    title: '操作', key: 'actions', width: 120,
    render: (row) =>
      h(NSpace, null, {
        default: () => [
          h(NButton, { size: 'small', type: 'primary', onClick: () => openPreview(row) },
            { default: () => '预览' }),
          h(NButton, { size: 'small', type: 'warning', onClick: () => handleDeleteDoc(row.id) },
            { default: () => '删除' }),
        ],
      }),
  },
]

function formatSize(chars: number | null | undefined) {
  if (chars == null) return '-'
  if (chars < 1000) return `${chars} 字符`
  if (chars < 1000000) return `${(chars / 1000).toFixed(1)}K 字符`
  return `${(chars / 1000000).toFixed(1)}M 字符`
}

function formatDate(dateStr: string) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return d.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
    + ' ' + d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// --- KB List columns ---
// Using n-data-table requires h() — simpler approach: render KB list with n-card grid
</script>

<template>
  <!-- ====== LIST VIEW ====== -->
  <div v-if="!isDetail">
    <n-space vertical size="large">
      <!-- Header -->
      <div style="display: flex; justify-content: space-between; align-items: center;">
        <n-h3 style="margin: 0;">知识库</n-h3>
        <n-space>
          <n-input
            :value="store.searchQuery"
            @update:value="handleSearchInput"
            placeholder="搜索文档内容..."
            clearable
            style="width: 260px;"
          >
            <template #prefix>
              <n-icon>
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M15.5 14h-.79l-.28-.27A6.471 6.471 0 0016 9.5 6.5 6.5 0 109.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
                </svg>
              </n-icon>
            </template>
          </n-input>
          <n-button type="primary" @click="openCreate">
            <template #icon>
              <n-icon>
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>
                </svg>
              </n-icon>
            </template>
            创建知识库
          </n-button>
        </n-space>
      </div>

      <!-- Search results -->
      <template v-if="store.isSearching">
        <n-h4>搜索结果 ({{ store.searchResults.length }})</n-h4>
        <n-list v-if="store.searchResults.length">
          <n-list-item v-for="doc in store.searchResults" :key="doc.id" clickable @click="openPreview(doc)">
            <n-thing :title="doc.fileName" :description="doc.knowledgeBaseId ? `知识库 #${doc.knowledgeBaseId}` : ''">
              <template #footer>
                <n-ellipsis :line-clamp="2" style="font-size: 13px; color: #666;">
                  {{ doc.content }}
                </n-ellipsis>
              </template>
            </n-thing>
          </n-list-item>
        </n-list>
        <n-empty v-else description="没有匹配的文档" />
      </template>

      <!-- KB list -->
      <template v-else>
        <n-spin :show="store.loading">
          <n-grid v-if="store.kbs.length" :cols="2" :x-gap="16" :y-gap="16">
          <n-gi v-for="kb in store.kbs" :key="kb.id">
            <n-card
              hoverable
              @click="router.push({ name: 'KnowledgeDetail', params: { id: kb.id } })"
              style="cursor: pointer;"
            >
              <div style="display: flex; align-items: center; gap: 12px;">
                <n-avatar size="44" round color="#C8815F">
                  <n-icon size="22" color="#fff">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                      <path d="M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-1 9h-4v4h-2v-4H9V9h4V5h2v4h4v2z"/>
                    </svg>
                  </n-icon>
                </n-avatar>
                <div style="flex: 1; min-width: 0;">
                  <div style="font-weight: 600;">{{ kb.name }}</div>
                  <n-ellipsis v-if="kb.description" :line-clamp="1" style="font-size: 13px; color: #888;">
                    {{ kb.description }}
                  </n-ellipsis>
                </div>
                <n-button circle size="small" type="primary" quaternary @click.stop="openEdit(kb)">
                  <template #icon>
                    <n-icon>
                      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/>
                      </svg>
                    </n-icon>
                  </template>
                </n-button>
                <n-button circle size="small" type="error" quaternary @click.stop="handleDeleteKb(kb)">
                  <template #icon>
                    <n-icon color="#ef4444">
                      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                        <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
                      </svg>
                    </n-icon>
                  </template>
                </n-button>
              </div>
            </n-card>
          </n-gi>
        </n-grid>
        <n-empty v-else description="还没有知识库，点击上方按钮创建" />
      </n-spin>
      </template>
    </n-space>
  </div>

  <!-- ====== DETAIL VIEW ====== -->
  <div v-else>
    <n-space vertical size="large">
      <!-- Back + KB info -->
      <div style="display: flex; justify-content: space-between; align-items: center;">
        <n-space align="center">
          <n-button @click="router.push({ name: 'KnowledgeBase' })">
            <template #icon>
              <n-icon>
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/>
                </svg>
              </n-icon>
            </template>
            返回
          </n-button>
          <n-h3 v-if="store.currentKb" style="margin: 0;">
            {{ store.currentKb.name }}
          </n-h3>
        </n-space>
        <n-space>
          <n-button size="small" type="primary" quaternary @click="store.currentKb && openEdit(store.currentKb)">
            编辑
          </n-button>
          <n-button size="small" type="error" @click="store.currentKb && handleDeleteKb(store.currentKb)">
            删除
          </n-button>
        </n-space>
      </div>

      <n-p v-if="store.currentKb?.description" style="color: #888; margin: 0;">
        {{ store.currentKb.description }}
      </n-p>

      <n-spin :show="store.loading">
      <template v-if="store.currentKb">
        <!-- Upload -->
        <n-upload
          :multiple="false"
          :show-file-list="false"
          :custom-request="handleCustomRequest"
          accept=".txt,.md,.pdf,.docx,.xlsx,.csv,.json,.xml,.yaml,.yml,.properties,.log"
        >
          <n-upload-dragger>
            <div style="padding: 24px 0;">
              <n-icon size="48" color="#C8815F">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M9 16h6v-6h4l-7-7-7 7h4zm-4 2h14v2H5z"/>
                </svg>
              </n-icon>
              <n-p style="margin: 8px 0 0;">
                点击或拖拽文件到此处上传
              </n-p>
              <n-p style="font-size: 12px; color: #999; margin: 4px 0 0;">
                支持 TXT、MD、PDF、DOCX、XLSX、CSV、JSON、XML 等格式
              </n-p>
            </div>
          </n-upload-dragger>
        </n-upload>

        <!-- Document list -->
        <n-h4 style="margin: 0;">文档列表 ({{ store.documents.length }})</n-h4>

        <n-data-table
          v-if="store.documents.length"
          :columns="docColumns"
          :data="store.documents"
          :bordered="true"
          :single-line="false"
          :striped="true"
          :row-key="(row: typeof store.documents[number]) => row.id"
        />

        <n-empty v-else description="暂无文档，请上传" />
      </template>
    </n-spin>
    </n-space>

    <!-- Doc Preview Modal -->
    <DocPreview
      v-if="previewDoc"
      :doc="previewDoc"
      :show="showPreview"
      @update:show="showPreview = $event"
    />
  </div>

  <!-- KB Create/Edit Modal -->
  <n-modal v-model:show="showKbModal" :title="isEditMode ? '编辑知识库' : '创建知识库'" preset="card"
    style="width: 480px; max-width: 90vw;" :mask-closable="false">
    <n-form>
      <n-form-item label="名称" required>
        <n-input v-model:value="editingKb.name" placeholder="知识库名称" :maxlength="100" show-count />
      </n-form-item>
      <n-form-item label="描述">
        <n-input v-model:value="editingKb.description" type="textarea" placeholder="可选描述" :rows="3" :maxlength="500" />
      </n-form-item>
      <div style="display: flex; gap: 12px; justify-content: flex-end;">
        <n-button @click="showKbModal = false">取消</n-button>
        <n-button type="primary" :loading="savingKb" @click="handleSaveKb">
          {{ isEditMode ? '保存' : '创建' }}
        </n-button>
      </div>
    </n-form>
  </n-modal>
</template>

