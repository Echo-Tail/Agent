<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import { useMessage, useDialog, NButton } from 'naive-ui'
import { getLogs, getLogStats, clearLogs, exportLogs } from '../../utils/logging/writer'
import { LOG_LEVELS, LOG_CATEGORIES } from '../../utils/logging/types'
import type { LogEntry, LogLevel, LogCategory, LogStats, LogFilters } from '../../utils/logging/types'

const message = useMessage()
const dialog = useDialog()

const logs = ref<LogEntry[]>([])
const stats = ref<LogStats | null>(null)
const loading = ref(false)
const setupError = ref('')
const logCount = ref(0)

const filterLevels = ref<LogLevel[]>([])
const filterCategories = ref<LogCategory[]>([])
const filterSearch = ref('')
const filterStartDate = ref<number | null>(null)
const filterEndDate = ref<number | null>(null)

const detailEntry = ref<LogEntry | null>(null)
const showDetail = ref(false)

const levelLabels: Record<LogLevel, string> = { DEBUG: '调试', INFO: '信息', WARN: '警告', ERROR: '错误' }
const levelColors: Record<LogLevel, string> = { DEBUG: 'default', INFO: 'info', WARN: 'warning', ERROR: 'error' }
const categoryLabels: Record<LogCategory, string> = {
  API: 'API', USER_ACTION: '用户操作', ROUTER: '导航', ERROR: '错误', PERFORMANCE: '性能', AUTH: '认证',
}

async function load() {
  if (loading.value) return
  loading.value = true
  try {
    const filters: LogFilters = {}
    if (filterLevels.value.length) filters.levels = filterLevels.value
    if (filterCategories.value.length) filters.categories = filterCategories.value
    if (filterSearch.value) filters.search = filterSearch.value
    if (filterStartDate.value) filters.startDate = new Date(filterStartDate.value).toISOString()
    if (filterEndDate.value) filters.endDate = new Date(filterEndDate.value).toISOString()

    const [logData, statData] = await Promise.all([
      getLogs(filters),
      getLogStats(),
    ])
    logs.value = logData
    stats.value = statData
    logCount.value = logData.length
  } catch (e) {
    setupError.value = e instanceof Error ? e.message : '加载日志失败'
  } finally {
    loading.value = false
  }
}

function openDetail(entry: LogEntry) {
  detailEntry.value = entry
  showDetail.value = true
}

function handleExport() {
  dialog.info({
    title: '导出日志',
    content: '将导出当前筛选条件匹配的所有日志为 JSON 文件，确认导出？',
    positiveText: '导出',
    negativeText: '取消',
    onPositiveClick: async () => {
      const filters: LogFilters = {}
      if (filterLevels.value.length) filters.levels = filterLevels.value
      if (filterCategories.value.length) filters.categories = filterCategories.value
      if (filterSearch.value) filters.search = filterSearch.value
      if (filterStartDate.value) filters.startDate = new Date(filterStartDate.value).toISOString()
      if (filterEndDate.value) filters.endDate = new Date(filterEndDate.value).toISOString()
      const blob = await exportLogs(filters)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `logs-${new Date().toISOString().slice(0, 10)}.json`
      a.click()
      URL.revokeObjectURL(url)
      message.success('导出成功')
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
      await clearLogs()
      message.success('日志已清除')
      load()
    },
  })
}

// Stats computed — don't use computed() to avoid render-time errors
function getStatsCards() {
  if (!stats.value) return null
  return [
    { label: '总计', value: stats.value.total },
    { label: '错误', value: stats.value.byLevel.ERROR },
    { label: '警告', value: stats.value.byLevel.WARN },
    { label: 'API', value: stats.value.byCategory.API },
    { label: '用户操作', value: stats.value.byCategory.USER_ACTION },
  ]
}

onMounted(() => {
  load()
})
</script>

<template>
  <div>
    <!-- Setup error -->
    <n-alert v-if="setupError" type="error" :title="setupError" closable @close="setupError = ''" />

    <n-spin :show="loading" size="large">
    <!-- Stats cards -->
    <n-grid :cols="5" :x-gap="12" v-if="stats">
      <n-gi v-for="card in getStatsCards()" :key="card!.label">
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
    <n-card title="24h 错误趋势" size="small" v-if="stats?.last24h.length">
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
        :options="LOG_LEVELS.map(l => ({ label: levelLabels[l], value: l }))"
        multiple placeholder="级别" clearable style="width: 180px;"
      />
      <n-select
        v-model:value="filterCategories"
        :options="LOG_CATEGORIES.map(c => ({ label: categoryLabels[c], value: c }))"
        multiple placeholder="类别" clearable style="width: 200px;"
      />
      <n-date-picker
        v-model:value="filterStartDate" type="datetime" placeholder="开始时间"
        clearable style="width: 200px;"
      />
      <n-date-picker
        v-model:value="filterEndDate" type="datetime" placeholder="结束时间"
        clearable style="width: 200px;"
      />
      <n-input
        v-model:value="filterSearch" placeholder="搜索消息或数据..."
        clearable style="width: 220px;"
      />
      <n-button type="primary" @click="load" :loading="loading">查询</n-button>
    </div>

    <!-- Action buttons -->
    <div style="display: flex; gap: 8px; justify-content: flex-end; margin: 12px 0;">
      <n-button type="primary" secondary @click="handleExport">导出</n-button>
      <n-button type="error" @click="handleClear">清除日志</n-button>
    </div>

    <!-- Log table (no loading overlay) -->
    <n-data-table
      :columns="[
        { title: '时间', key: 'timestamp', width: 170, render: (row: LogEntry) => new Date(row.timestamp).toLocaleString('zh-CN') },
        { title: '级别', key: 'level', width: 80, render: (row: LogEntry) => h('n-tag', { size: 'small', type: levelColors[row.level] }, () => levelLabels[row.level]) },
        { title: '类别', key: 'category', width: 100, render: (row: LogEntry) => h('n-tag', { size: 'small', bordered: false }, () => categoryLabels[row.category]) },
        { title: '消息', key: 'message', ellipsis: { tooltip: true } },
        { title: '耗时', key: 'duration', width: 80, render: (row: LogEntry) => row.duration != null ? `${row.duration}ms` : '-' },
        { title: '页面', key: 'route', width: 120, render: (row: LogEntry) => row.route || '-' },
        { title: '操作', key: 'actions', width: 70, render: (row: LogEntry) => h(NButton, { size: 'tiny', type: 'primary', onClick: () => openDetail(row) }, () => '详情') },
      ]"
      :data="logs"
      :bordered="true"
      :single-line="false"
      striped
      :row-key="(row: LogEntry) => row.id"
      :max-height="520"
    />
    </n-spin>

    <!-- Detail modal -->
    <n-modal v-model:show="showDetail" preset="card" title="日志详情"
      style="width: 700px; max-width: 90vw;" :mask-closable="false">
      <n-descriptions v-if="detailEntry" :column="2" bordered size="small">
        <n-descriptions-item label="时间">{{ new Date(detailEntry.timestamp).toLocaleString('zh-CN') }}</n-descriptions-item>
        <n-descriptions-item label="级别">
          <n-tag :type="levelColors[detailEntry.level]" size="small">{{ levelLabels[detailEntry.level] }}</n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="类别">
          <n-tag size="small" bordered>{{ categoryLabels[detailEntry.category] }}</n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="耗时">{{ detailEntry.duration != null ? `${detailEntry.duration}ms` : '-' }}</n-descriptions-item>
        <n-descriptions-item label="页面">{{ detailEntry.route || '-' }}</n-descriptions-item>
        <n-descriptions-item label="用户 ID">{{ detailEntry.userId ?? '-' }}</n-descriptions-item>
      </n-descriptions>
      <n-divider />
      <p style="margin: 8px 0;"><strong>消息</strong></p>
      <p>{{ detailEntry?.message }}</p>
      <template v-if="detailEntry?.data && Object.keys(detailEntry.data).length">
        <p style="margin: 12px 0 8px;"><strong>数据</strong></p>
        <pre style="background: #f5f5f5; padding: 12px; border-radius: 4px; overflow: auto;">{{ JSON.stringify(detailEntry.data, null, 2) }}</pre>
      </template>
    </n-modal>
  </div>
</template>
