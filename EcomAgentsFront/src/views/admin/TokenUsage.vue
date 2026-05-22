<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
import { NTag, type DataTableColumns } from 'naive-ui'
import { getTokenUsageSummaryApi, getImageModelCallsApi, getTokenUsageDetailApi } from '../../api/token-usage'
import type { TokenUsageSummary, TokenUsageRecord } from '../../api/token-usage'

const loading = ref(false)
const startDate = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 10))
const endDate = ref(new Date().toISOString().slice(0, 10))
const imageModelCalls = ref(0)
const summaryData = ref<TokenUsageSummary[]>([])
const detailData = ref<TokenUsageRecord[]>([])
const showDetail = ref(false)

const summaryColumns: DataTableColumns<TokenUsageSummary> = [
  { title: '模型名称', key: 'modelName', width: 160 },
  {
    title: '类型',
    key: 'modelType',
    width: 80,
    render: (row) => h(NTag, { type: row.modelType === 'IMAGE' ? 'info' : 'default', size: 'tiny', bordered: false }, {
      default: () => row.modelType === 'IMAGE' ? '图片' : '文本',
    }),
  },
  { title: '调用次数', key: 'callCount', width: 100, sorter: (a, b) => a.callCount - b.callCount },
  { title: 'Prompt Token', key: 'promptTokens', width: 120, sorter: (a, b) => a.promptTokens - b.promptTokens },
  { title: 'Completion Token', key: 'completionTokens', width: 150, sorter: (a, b) => a.completionTokens - b.completionTokens },
  { title: '总 Token', key: 'totalTokens', width: 100, sorter: (a, b) => a.totalTokens - b.totalTokens },
]

const detailColumns: DataTableColumns<TokenUsageRecord> = [
  { title: '时间', key: 'createdAt', width: 170, render: (row) => formatTime(row.createdAt) },
  { title: '模型', key: 'modelName', width: 140 },
  { title: '类型', key: 'modelType', width: 80, render: (row) => row.modelType === 'IMAGE' ? '图片' : '文本' },
  { title: 'Prompt', key: 'promptTokens', width: 90 },
  { title: 'Completion', key: 'completionTokens', width: 120 },
  { title: '总计', key: 'totalTokens', width: 90 },
  {
    title: '状态',
    key: 'success',
    width: 90,
    render: (row) => h('span', { style: `color:${row.success ? '#18a058' : '#d03050'};` }, row.success ? '成功' : '失败'),
  },
  { title: '错误', key: 'errorMessage', ellipsis: { tooltip: true }, render: (row) => row.errorMessage || '-' },
]

async function fetchData() {
  loading.value = true
  try {
    const [summaryRes, imageRes, detailRes] = await Promise.all([
      getTokenUsageSummaryApi(startDate.value, endDate.value),
      getImageModelCallsApi(startDate.value, endDate.value),
      getTokenUsageDetailApi(startDate.value, endDate.value),
    ])
    if (summaryRes.data.code === 200) summaryData.value = summaryRes.data.data ?? []
    if (imageRes.data.code === 200) imageModelCalls.value = imageRes.data.data ?? 0
    if (detailRes.data.code === 200) detailData.value = detailRes.data.data ?? []
  } finally {
    loading.value = false
  }
}

function formatTime(t: string) {
  const d = new Date(t)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${day} ${h}:${min}`
}

onMounted(fetchData)
</script>

<template>
  <n-space vertical size="large">
    <div class="toolbar">
      <n-h3 style="margin: 0">Token 用量统计</n-h3>
      <n-space align="center">
        <n-date-picker v-model:formatted-value="startDate" value-format="yyyy-MM-dd" type="date" placeholder="开始日期" style="width: 150px" />
        <span style="color: #999">至</span>
        <n-date-picker v-model:formatted-value="endDate" value-format="yyyy-MM-dd" type="date" placeholder="结束日期" style="width: 150px" />
        <n-button type="primary" @click="fetchData" :loading="loading">查询</n-button>
        <n-button quaternary @click="showDetail = !showDetail">{{ showDetail ? '隐藏明细' : '查看明细' }}</n-button>
      </n-space>
    </div>

    <n-grid :cols="4" :x-gap="16">
      <n-gi><n-card :bordered="true" size="small"><n-statistic label="调用总次数" :value="summaryData.reduce((s, r) => s + r.callCount, 0)" /></n-card></n-gi>
      <n-gi><n-card :bordered="true" size="small"><n-statistic label="Token 总量" :value="summaryData.reduce((s, r) => s + r.totalTokens, 0)" /></n-card></n-gi>
      <n-gi><n-card :bordered="true" size="small"><n-statistic label="输入 Token" :value="summaryData.reduce((s, r) => s + r.promptTokens, 0)" /></n-card></n-gi>
      <n-gi><n-card :bordered="true" size="small"><n-statistic label="图片模型调用" :value="imageModelCalls" /></n-card></n-gi>
    </n-grid>

    <n-card title="各模型统计" :bordered="true" size="small">
      <n-data-table :columns="summaryColumns" :data="summaryData" :loading="loading" :bordered="true" :single-line="false" striped />
    </n-card>

    <n-collapse v-if="showDetail" :default-expanded-names="['detail']">
      <n-collapse-item title="详细调用记录" name="detail">
        <n-data-table :columns="detailColumns" :data="detailData" :loading="loading" :bordered="true" :single-line="false" striped />
      </n-collapse-item>
    </n-collapse>
  </n-space>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
</style>
