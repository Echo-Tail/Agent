<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { NButton, NTag, useMessage } from 'naive-ui'
import {
  completeTicketApi,
  listAdminTicketsApi,
  listTicketChangesApi,
  startTicketApi,
} from '../../api/ticket'
import type { Ticket, TicketChangeRecord, TicketFilters } from '../../types/ticket'
import {
  affectedMenuLabels,
  affectedMenuOptions,
  ticketPriorityLabels,
  ticketPriorityOptions,
  ticketStatusLabels,
  ticketStatusOptions,
} from '../../types/ticket'

const message = useMessage()
const loading = ref(false)
const handling = ref(false)
const tickets = ref<Ticket[]>([])
const selectedTicket = ref<Ticket | null>(null)
const changes = ref<TicketChangeRecord[]>([])
const showDetail = ref(false)
const showComplete = ref(false)
const handlingNote = ref('')

const filters = reactive<TicketFilters>({
  status: null,
  affectedMenu: null,
  priority: null,
  title: '',
  submitterId: null,
})

const columns = [
  { title: '工单编号', key: 'ticketNumber', width: 150 },
  { title: '标题', key: 'title', ellipsis: { tooltip: true } },
  { title: '提交人', key: 'submitterName', width: 110 },
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
    width: 230,
    render: (row: Ticket) => h('div', { style: 'display:flex; gap:8px;' }, [
      h(NButton, { size: 'small', onClick: () => openDetail(row) }, { default: () => '查看' }),
      row.status === 'PENDING'
        ? h(NButton, { size: 'small', type: 'primary', onClick: () => start(row) }, { default: () => '开始处理' })
        : null,
      row.status === 'IN_PROGRESS'
        ? h(NButton, { size: 'small', type: 'success', onClick: () => openComplete(row) }, { default: () => '完成' })
        : null,
    ]),
  },
]

async function fetchTickets() {
  loading.value = true
  try {
    const res = await listAdminTicketsApi(filters)
    if (res.data.code === 200) tickets.value = res.data.data ?? []
  } catch {
    message.error('工单加载失败')
  } finally {
    loading.value = false
  }
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

async function start(ticket: Ticket) {
  handling.value = true
  try {
    const res = await startTicketApi(ticket.id)
    if (res.data.code === 200) {
      message.success('已开始处理')
      await fetchTickets()
      selectedTicket.value = res.data.data
    } else {
      message.error(res.data.message)
    }
  } catch {
    message.error('操作失败')
  } finally {
    handling.value = false
  }
}

function openComplete(ticket: Ticket) {
  selectedTicket.value = ticket
  handlingNote.value = ticket.handlingNote ?? ''
  showComplete.value = true
}

async function complete() {
  if (!selectedTicket.value || !handlingNote.value.trim()) {
    message.warning('请填写处理意见')
    return
  }
  handling.value = true
  try {
    const res = await completeTicketApi(selectedTicket.value.id, handlingNote.value.trim())
    if (res.data.code === 200) {
      message.success('工单已完成')
      showComplete.value = false
      await fetchTickets()
      selectedTicket.value = res.data.data
    } else {
      message.error(res.data.message)
    }
  } catch {
    message.error('操作失败')
  } finally {
    handling.value = false
  }
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
        <n-select v-model:value="filters.affectedMenu" clearable placeholder="受影响菜单" :options="affectedMenuOptions" style="width: 150px" />
        <n-select v-model:value="filters.priority" clearable placeholder="优先级" :options="ticketPriorityOptions" style="width: 120px" />
        <n-input-number v-model:value="filters.submitterId" clearable placeholder="提交人ID" style="width: 140px" />
        <n-input v-model:value="filters.title" clearable placeholder="标题关键词" style="width: 220px" />
        <n-button @click="fetchTickets" :loading="loading">查询</n-button>
      </n-space>
    </div>

    <n-data-table :columns="columns" :data="tickets" :loading="loading || handling" :bordered="true" :single-line="false" striped />

    <n-drawer v-model:show="showDetail" width="660">
      <n-drawer-content v-if="selectedTicket" :title="selectedTicket.ticketNumber">
        <n-space vertical size="large">
          <n-descriptions bordered :column="1" size="small">
            <n-descriptions-item label="标题">{{ selectedTicket.title }}</n-descriptions-item>
            <n-descriptions-item label="提交人">{{ selectedTicket.submitterName || selectedTicket.submitterId }}</n-descriptions-item>
            <n-descriptions-item label="处理人">{{ selectedTicket.handlerName || '-' }}</n-descriptions-item>
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
          <n-space>
            <n-button v-if="selectedTicket.status === 'PENDING'" type="primary" @click="start(selectedTicket)">开始处理</n-button>
            <n-button v-if="selectedTicket.status === 'IN_PROGRESS'" type="success" @click="openComplete(selectedTicket)">完成</n-button>
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

    <n-modal v-model:show="showComplete" preset="card" title="完成工单" style="width: 560px; max-width: 92vw">
      <n-input v-model:value="handlingNote" type="textarea" placeholder="处理意见" :autosize="{ minRows: 4, maxRows: 8 }" />
      <template #footer>
        <n-space justify="end">
          <n-button @click="showComplete = false">取消</n-button>
          <n-button type="primary" :loading="handling" @click="complete">完成</n-button>
        </n-space>
      </template>
    </n-modal>
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
