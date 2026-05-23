<script setup lang="ts">
import { ref, onMounted, h, computed } from 'vue'
import { useMessage, useDialog, NButton, NTag } from 'naive-ui'
import { queryLogsApi, getLogStatsApi, clearLogsApi } from '../../api/systemLog'
import type { SystemLogDTO, LogStatsDTO } from '../../api/systemLog'
import { PAGE_SIZE_OPTIONS } from '../../constants'

const message = useMessage()
const dialog = useDialog()

const logs = ref<SystemLogDTO[]>([])
const stats = ref<LogStatsDTO | null>(null)
const loading = ref(false)
const error = ref('')

const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const filterLevels = ref<string[]>([])
const filterCategories = ref<string[]>([])
const filterSearch = ref('')
const filterStartDate = ref<number | null>(null)
const filterEndDate = ref<number | null>(null)

const detailEntry = ref<SystemLogDTO | null>(null)
const showDetail = ref(false)
const parsedDetailData = ref<string>('')

const levelLabels: Record<string, string> = { DEBUG: '调试', INFO: '信息', WARN: '警告', ERROR: '错误' }
type NTagType = 'default' | 'info' | 'success' | 'warning' | 'error' | 'primary'
const levelColors: Record<string, NTagType> = { DEBUG: 'default', INFO: 'info', WARN: 'warning', ERROR: 'error' }
const categoryLabels: Record<string, string> = {
  API: 'API', USER_ACTION: '用户操作', ROUTER: '导航', ERROR: '错误', PERFORMANCE: '性能', AUTH: '认证',
}
const levelOptions = ['DEBUG', 'INFO', 'WARN', 'ERROR'].map(l => ({ label: levelLabels[l], value: l }))
const categoryOptions = ['API', 'USER_ACTION', 'ROUTER', 'ERROR', 'PERFORMANCE', 'AUTH']
  .map(c => ({ label: categoryLabels[c], value: c }))

async function load() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try {
    const [logRes, statsRes] = await Promise.all([
      queryLogsApi({
        page: page.value - 1,
        size: pageSize.value,
        levels: filterLevels.value.length ? filterLevels.value : undefined,
        categories: filterCategories.value.length ? filterCategories.value : undefined,
        search: filterSearch.value || undefined,
        startDate: filterStartDate.value ? new Date(filterStartDate.value).toISOString() : undefined,
        endDate: filterEndDate.value ? new Date(filterEndDate.value).toISOString() : undefined,
      }),
      getLogStatsApi(),
    ])
    const pageData = logRes.data.data
    logs.value = pageData.content
    total.value = pageData.page.totalElements
    stats.value = statsRes.data.data
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载日志失败'
  } finally {
    loading.value = false
  }
}

function onPageChange(newPage: number) {
  page.value = newPage
  load()
}

function onPageSizeChange(newSize: number) {
  pageSize.value = newSize
  page.value = 1
  load()
}

function onFilterChange() {
  page.value = 1
  load()
}

function openDetail(entry: SystemLogDTO) {
  detailEntry.value = entry
  if (entry.data) {
    try {
      const parsed = JSON.parse(entry.data)
      parsedDetailData.value = JSON.stringify(parsed, null, 2)
    } catch {
      parsedDetailData.value = entry.data
    }
  } else {
    parsedDetailData.value = ''
  }
  showDetail.value = true
}

async function handleExport() {
  dialog.info({
    title: '导出日志',
    content: '将导出当前筛选条件匹配的所有日志为 JSON 文件，确认导出？',
    positiveText: '导出',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await queryLogsApi({
          page: 0,
          size: 99999,
          levels: filterLevels.value.length ? filterLevels.value : undefined,
          categories: filterCategories.value.length ? filterCategories.value : undefined,
          search: filterSearch.value || undefined,
          startDate: filterStartDate.value ? new Date(filterStartDate.value).toISOString() : undefined,
          endDate: filterEndDate.value ? new Date(filterEndDate.value).toISOString() : undefined,
        })
        const allLogs = res.data.data.content
        const blob = new Blob([JSON.stringify(allLogs, null, 2)], { type: 'application/json' })
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `logs-${new Date().toISOString().slice(0, 10)}.json`
        a.click()
        URL.revokeObjectURL(url)
        message.success('导出成功')
      } catch {
        message.error('导出失败')
      }
    },
  })
}

function handleClear() {
  dialog.warning({
    title: '清除日志',
    content: '确定要清除所有日志吗？此操作不可恢复。',
    positiveText: '清除',
    negativeText: '取消',
    positiveButtonProps: { type: 'error' },
    onPositiveClick: async () => {
      try {
        await clearLogsApi()
        message.success('日志已清除')
        load()
      } catch {
        message.error('清除失败')
      }
    },
  })
}

const statsCards = computed(() => {
  if (!stats.value) return null
  return [
    { label: '总计', value: stats.value.total },
    { label: '错误', value: stats.value.byLevel.ERROR ?? 0 },
    { label: '警告', value: stats.value.byLevel.WARN ?? 0 },
    { label: 'API', value: stats.value.byCategory.API ?? 0 },
    { label: '用户操作', value: stats.value.byCategory.USER_ACTION ?? 0 },
  ]
})

onMounted(() => {
  load()
})
</script>

<template>
  <div>
    <n-alert v-if="error" type="error" :title="error" closable @close="error = ''" />

    <n-spin :show="loading" size="large">
      <!-- Stats cards -->
      <n-grid :cols="5" :x-gap="12" v-if="statsCards">
        <n-gi v-for="card in statsCards" :key="card!.label">
          <n-card size="small" hoverable>
            <n-statistic :label="card!.label" :value="card!.value">
              <template #suffix v-if="card!.label === '错误' && stats!.errorRate > 0">
                <n-tag size="small" type="error">{{ (stats!.errorRate * 100).toFixed(1) }}%</n-tag>
              </template>
            </n-statistic>
          </n-card>
        </n-gi>
      </n-grid>

      <!-- Error trend -->
      <n-card title="24h 错误趋势" size="small" v-if="stats?.last24h?.length">
        <div style="display: flex; align-items: flex-end; gap: 3px; height: 60px; padding: 0 4px;">
          <div
            v-for="h in stats!.last24h"
            :key="h.hour"
            :title="`${h.hour} - ${h.count} 个错误`"
            style="flex: 1; display: flex; flex-direction: column; align-items: center; gap: 2px;"
          >
            <div
              :style="{
                width: '100%',
                height: h.count > 0 ? `${Math.min(h.count / Math.max(...stats!.last24h.map(x => x.count), 1), 1) * 48}px` : '2px',
                background: h.count > 0 ? '#ef4444' : '#eee',
                borderRadius: '2px',
                minHeight: '2px',
                transition: 'height 0.3s',
              }"
            />
            <span style="font-size: 10px; color: #999; white-space: nowrap;">{{ h.hour }}</span>
          </div>
        </div>
      </n-card>

      <!-- Filter bar -->
      <div style="display: flex; gap: 12px; flex-wrap: wrap; align-items: center; margin-top: 16px;">
        <n-select
          v-model:value="filterLevels"
          :options="levelOptions"
          multiple placeholder="级别" clearable style="width: 180px;"
          @update:value="onFilterChange"
        />
        <n-select
          v-model:value="filterCategories"
          :options="categoryOptions"
          multiple placeholder="类别" clearable style="width: 200px;"
          @update:value="onFilterChange"
        />
        <n-date-picker
          v-model:value="filterStartDate" type="datetime" placeholder="开始时间"
          clearable style="width: 200px;" @update:value="onFilterChange"
        />
        <n-date-picker
          v-model:value="filterEndDate" type="datetime" placeholder="结束时间"
          clearable style="width: 200px;" @update:value="onFilterChange"
        />
        <n-input
          v-model:value="filterSearch" placeholder="搜索消息或数据..."
          clearable style="width: 220px;" @keyup.enter="onFilterChange"
        />
        <n-button type="primary" @click="onFilterChange" :loading="loading">查询</n-button>
      </div>

      <!-- Action buttons -->
      <div style="display: flex; gap: 8px; justify-content: flex-end; margin: 12px 0;">
        <n-button type="primary" secondary @click="handleExport">导出</n-button>
        <n-button type="error" @click="handleClear">清除日志</n-button>
      </div>

      <!-- Log table -->
      <n-data-table
        :columns="[
          { title: '时间', key: 'createdAt', width: 170, render: (row: SystemLogDTO) => new Date(row.createdAt).toLocaleString('zh-CN') },
          { title: '级别', key: 'level', width: 80, render: (row: SystemLogDTO) => h(NTag, { size: 'small', type: levelColors[row.level] || 'default' }, { default: () => levelLabels[row.level] || row.level }) },
          { title: '类别', key: 'category', width: 100, render: (row: SystemLogDTO) => h(NTag, { size: 'small', bordered: false }, { default: () => categoryLabels[row.category] || row.category }) },
          { title: '消息', key: 'message', ellipsis: { tooltip: true } },
          { title: '耗时', key: 'duration', width: 80, render: (row: SystemLogDTO) => row.duration != null ? `${row.duration}ms` : '-' },
          { title: '页面', key: 'route', width: 120, render: (row: SystemLogDTO) => row.route || '-' },
          { title: '操作', key: 'actions', width: 70, render: (row: SystemLogDTO) => h(NButton, { size: 'tiny', type: 'primary', onClick: () => openDetail(row) }, () => '详情') },
        ]"
        :data="logs"
        :bordered="true"
        :single-line="false"
        striped
        :row-key="(row: SystemLogDTO) => row.id"
        :max-height="520"
      />

      <!-- Pagination -->
      <div style="display: flex; justify-content: flex-end; margin-top: 16px;" v-if="total > 0">
        <n-pagination
          :page="page"
          :page-size="pageSize"
          :page-sizes="PAGE_SIZE_OPTIONS"
          :item-count="total"
          show-size-picker
          @update:page="onPageChange"
          @update:page-size="onPageSizeChange"
        />
      </div>
    </n-spin>

    <!-- Detail modal -->
    <n-modal v-model:show="showDetail" preset="card" title="日志详情"
      style="width: 700px; max-width: 90vw;" :mask-closable="false">
      <n-descriptions v-if="detailEntry" :column="2" bordered size="small">
        <n-descriptions-item label="时间">{{ new Date(detailEntry.createdAt).toLocaleString('zh-CN') }}</n-descriptions-item>
        <n-descriptions-item label="级别">
          <n-tag :type="levelColors[detailEntry.level]" size="small">{{ levelLabels[detailEntry.level] || detailEntry.level }}</n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="类别">
          <n-tag size="small" bordered>{{ categoryLabels[detailEntry.category] || detailEntry.category }}</n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="耗时">{{ detailEntry.duration != null ? `${detailEntry.duration}ms` : '-' }}</n-descriptions-item>
        <n-descriptions-item label="页面">{{ detailEntry.route || '-' }}</n-descriptions-item>
        <n-descriptions-item label="用户 ID">{{ detailEntry.userId ?? '-' }}</n-descriptions-item>
      </n-descriptions>
      <n-divider />
      <p style="margin: 8px 0;"><strong>消息</strong></p>
      <p>{{ detailEntry?.message }}</p>
      <template v-if="parsedDetailData">
        <p style="margin: 12px 0 8px;"><strong>数据</strong></p>
        <pre style="background: #f5f5f5; padding: 12px; border-radius: 4px; overflow: auto;">{{ parsedDetailData }}</pre>
      </template>
    </n-modal>
  </div>
</template>
