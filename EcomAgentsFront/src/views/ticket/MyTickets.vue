<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NButton, NTag, useMessage } from 'naive-ui'
import {
  createTicketApi,
  listMyTicketsApi,
  listTicketChangesApi,
  updateTicketApi,
} from '../../api/ticket'
import { uploadFileApi } from '../../api/file'
import { useAuthStore } from '../../stores/auth'
import type { FileRecord } from '../../types/api'
import type { Ticket, TicketChangeRecord, TicketRequest } from '../../types/ticket'
import {
  affectedMenuLabels,
  allAffectedMenuOptions,
  submitterAffectedMenuOptions,
  ticketPriorityLabels,
  ticketPriorityOptions,
  ticketStatusLabels,
  ticketStatusOptions,
} from '../../types/ticket'

const message = useMessage()
const auth = useAuthStore()
const loading = ref(false)
const submitting = ref(false)
const uploadInput = ref<HTMLInputElement | null>(null)
const tickets = ref<Ticket[]>([])
const selectedTicket = ref<Ticket | null>(null)
const changes = ref<TicketChangeRecord[]>([])
const showForm = ref(false)
const showDetail = ref(false)
const editingId = ref<number | null>(null)

const filters = reactive({
  status: null as string | null,
  affectedMenu: null as string | null,
  priority: null as string | null,
  title: '',
})

const form = reactive<TicketRequest>({
  title: '',
  affectedMenu: 'OTHER',
  priority: 'MEDIUM',
  content: '',
  attachmentIds: [],
})
const attachments = ref<FileRecord[]>([])

const canEditSelected = computed(() => selectedTicket.value && selectedTicket.value.status !== 'COMPLETED')
const menuOptions = computed(() => auth.isAdmin ? allAffectedMenuOptions : submitterAffectedMenuOptions)

const columns = [
  { title: '工单编号', key: 'ticketNumber', width: 150 },
  { title: '标题', key: 'title', ellipsis: { tooltip: true } },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render: (row: Ticket) => h(NTag, { type: statusTagType(row.status), size: 'small' }, { default: () => ticketStatusLabels[row.status] }),
  },
  { title: '受影响菜单', key: 'affectedMenu', width: 120, render: (row: Ticket) => affectedMenuLabels[row.affectedMenu] },
  { title: '优先级', key: 'priority', width: 90, render: (row: Ticket) => ticketPriorityLabels[row.priority] },
  { title: '创建时间', key: 'createdAt', width: 170, render: (row: Ticket) => formatTime(row.createdAt) },
  {
    title: '操作',
    key: 'actions',
    width: 140,
    render: (row: Ticket) => h(NButton, { size: 'small', onClick: () => openDetail(row) }, { default: () => '查看' }),
  },
]

async function fetchTickets() {
  loading.value = true
  try {
    const res = await listMyTicketsApi(filters as never)
    if (res.data.code === 200) tickets.value = res.data.data ?? []
  } catch {
    message.error('工单加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  resetForm()
  showForm.value = true
}

function openEdit(ticket: Ticket) {
  editingId.value = ticket.id
  form.title = ticket.title
  form.affectedMenu = ticket.affectedMenu
  form.priority = ticket.priority
  form.content = ticket.content
  attachments.value = [...ticket.attachments]
  form.attachmentIds = attachments.value.map((file) => file.id)
  showForm.value = true
}

async function openDetail(ticket: Ticket) {
  selectedTicket.value = ticket
  showDetail.value = true
  changes.value = []
  try {
    const res = await listTicketChangesApi(ticket.id)
    if (res.data.code === 200) changes.value = res.data.data ?? []
  } catch {
    message.error('修改记录加载失败')
  }
}

async function submitForm() {
  if (!form.title.trim() || !form.content.trim() || !form.affectedMenu || !form.priority) {
    message.warning('请填写完整工单信息')
    return
  }
  submitting.value = true
  try {
    const req = { ...form, title: form.title.trim(), content: form.content.trim() }
    const res = editingId.value ? await updateTicketApi(editingId.value, req) : await createTicketApi(req)
    if (res.data.code === 200) {
      message.success(editingId.value ? '工单已更新' : '工单已创建')
      showForm.value = false
      await fetchTickets()
      if (selectedTicket.value?.id === res.data.data.id) selectedTicket.value = res.data.data
    } else {
      message.error(res.data.message)
    }
  } catch {
    message.error('提交失败')
  } finally {
    submitting.value = false
  }
}

async function handleFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (!files.length) return
  const existingSize = attachments.value.reduce((sum, file) => sum + file.fileSize, 0)
  const newSize = files.reduce((sum, file) => sum + file.size, 0)
  if (existingSize + newSize > 20 * 1024 * 1024) {
    message.warning('附件总大小不能超过20MB')
    return
  }
  submitting.value = true
  try {
    for (const file of files) {
      const res = await uploadFileApi(file)
      if (res.data.code === 200) {
        attachments.value.push(res.data.data)
      } else {
        message.error(res.data.message)
      }
    }
    form.attachmentIds = attachments.value.map((file) => file.id)
  } catch {
    message.error('附件上传失败')
  } finally {
    submitting.value = false
  }
}

function removeAttachment(id: number) {
  attachments.value = attachments.value.filter((file) => file.id !== id)
  form.attachmentIds = attachments.value.map((file) => file.id)
}

function resetForm() {
  form.title = ''
  form.affectedMenu = 'OTHER'
  form.priority = 'MEDIUM'
  form.content = ''
  form.attachmentIds = []
  attachments.value = []
}

function formatTime(value?: string) {
  return value ? new Date(value).toLocaleString() : '-'
}

function formatFileSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function statusTagType(status: string) {
  if (status === 'PENDING') return 'warning'
  if (status === 'IN_PROGRESS') return 'info'
  return 'success'
}

onMounted(fetchTickets)
</script>

<template>
  <n-space vertical size="large">
    <div class="toolbar">
      <n-space>
        <n-select v-model:value="filters.status" clearable placeholder="状态" :options="ticketStatusOptions" style="width: 130px" />
        <n-select v-model:value="filters.affectedMenu" clearable placeholder="受影响菜单" :options="menuOptions" style="width: 150px" />
        <n-select v-model:value="filters.priority" clearable placeholder="优先级" :options="ticketPriorityOptions" style="width: 120px" />
        <n-input v-model:value="filters.title" clearable placeholder="标题关键词" style="width: 220px" />
        <n-button @click="fetchTickets" :loading="loading">查询</n-button>
      </n-space>
      <n-button type="primary" @click="openCreate">创建工单</n-button>
    </div>

    <n-data-table :columns="columns" :data="tickets" :loading="loading" :bordered="true" :single-line="false" striped />

    <n-modal v-model:show="showForm" preset="card" :title="editingId ? '编辑工单' : '创建工单'" style="width: 760px; max-width: 92vw">
      <n-form label-placement="top">
        <n-form-item label="标题" required>
          <n-input v-model:value="form.title" maxlength="120" show-count />
        </n-form-item>
        <n-grid :cols="2" :x-gap="16">
          <n-gi>
            <n-form-item label="受影响菜单" required>
              <n-select v-model:value="form.affectedMenu" :options="menuOptions" />
            </n-form-item>
          </n-gi>
          <n-gi>
            <n-form-item label="优先级" required>
              <n-select v-model:value="form.priority" :options="ticketPriorityOptions" />
            </n-form-item>
          </n-gi>
        </n-grid>
        <n-form-item label="内容" required>
          <n-input v-model:value="form.content" type="textarea" :autosize="{ minRows: 5, maxRows: 10 }" />
        </n-form-item>
        <n-form-item label="附件">
          <n-space vertical style="width: 100%">
            <input ref="uploadInput" type="file" multiple style="display: none" @change="handleFiles" />
            <n-button @click="uploadInput?.click()" :loading="submitting">选择附件</n-button>
            <n-space v-if="attachments.length">
              <n-tag v-for="file in attachments" :key="file.id" closable @close="removeAttachment(file.id)">
                {{ file.originalName }} ({{ formatFileSize(file.fileSize) }})
              </n-tag>
            </n-space>
          </n-space>
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showForm = false">取消</n-button>
          <n-button type="primary" :loading="submitting" @click="submitForm">提交</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-drawer v-model:show="showDetail" width="620">
      <n-drawer-content v-if="selectedTicket" :title="selectedTicket.ticketNumber">
        <n-space vertical size="large">
          <n-descriptions bordered :column="1" size="small">
            <n-descriptions-item label="标题">{{ selectedTicket.title }}</n-descriptions-item>
            <n-descriptions-item label="状态">{{ ticketStatusLabels[selectedTicket.status] }}</n-descriptions-item>
            <n-descriptions-item label="受影响菜单">{{ affectedMenuLabels[selectedTicket.affectedMenu] }}</n-descriptions-item>
            <n-descriptions-item label="优先级">{{ ticketPriorityLabels[selectedTicket.priority] }}</n-descriptions-item>
            <n-descriptions-item label="内容"><div class="multiline">{{ selectedTicket.content }}</div></n-descriptions-item>
            <n-descriptions-item label="处理意见">{{ selectedTicket.handlingNote || '-' }}</n-descriptions-item>
          </n-descriptions>
          <n-space v-if="selectedTicket.attachments.length" vertical>
            <n-text strong>附件</n-text>
            <n-a v-for="file in selectedTicket.attachments" :key="file.id" :href="`/v1/files/${file.id}/download`" target="_blank">
              {{ file.originalName }} ({{ formatFileSize(file.fileSize) }})
            </n-a>
          </n-space>
          <n-space v-if="canEditSelected">
            <n-button type="primary" @click="openEdit(selectedTicket)">编辑工单</n-button>
          </n-space>
          <n-divider />
          <n-text strong>修改记录</n-text>
          <n-timeline>
            <n-timeline-item v-for="record in changes" :key="record.id" :time="formatTime(record.changedAt)">
              {{ record.changedByName || record.changedBy }} 修改 {{ record.fieldName }}：
              {{ record.oldValue || '空' }} → {{ record.newValue || '空' }}
            </n-timeline-item>
          </n-timeline>
        </n-space>
      </n-drawer-content>
    </n-drawer>
  </n-space>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.multiline {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
