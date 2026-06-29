<script setup lang="ts">
import { onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import {
  getTokenUsageSummaryApi,
  getImageModelCallsApi,
  getTokenUsageDetailApi,
} from '@/api/token-usage'
import type { TokenUsageSummary, TokenUsageRecord } from '@/api/token-usage'
import {
  BarChart3,
  Loader2,
  ChevronDown,
  ChevronUp,
} from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'

useI18n()

const modelTypeKeys: Record<string, string> = {
  TEXT: 'modelManage.modelType.TEXT',
  IMAGE: 'modelManage.modelType.IMAGE',
}

const loading = ref(false)
const startDate = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 10))
const endDate = ref(new Date().toISOString().slice(0, 10))
const imageModelCalls = ref(0)
const summaryData = ref<TokenUsageSummary[]>([])
const detailData = ref<TokenUsageRecord[]>([])
const showDetail = ref(false)

async function fetchData() {
  loading.value = true
  try {
    const [summaryDataRes, imageCalls, detailDataRes] = await Promise.all([
      getTokenUsageSummaryApi(startDate.value, endDate.value),
      getImageModelCallsApi(startDate.value, endDate.value),
      getTokenUsageDetailApi(startDate.value, endDate.value),
    ])
    summaryData.value = summaryDataRes ?? []
    imageModelCalls.value = imageCalls ?? 0
    detailData.value = detailDataRes ?? []
  } finally {
    loading.value = false
  }
}

const totalCalls = () => summaryData.value.reduce((s, r) => s + r.callCount, 0)
const totalTokens = () => summaryData.value.reduce((s, r) => s + r.totalTokens, 0)
const totalPrompt = () => summaryData.value.reduce((s, r) => s + r.promptTokens, 0)
const totalCost = () => summaryData.value.reduce((s, r) => s + (r.cnyCost ?? 0), 0)

function formatTime(t: string) {
  return new Date(t).toLocaleString('zh-CN')
}

onMounted(fetchData)
</script>

<template>
  <div class="space-y-6">
    <PageHeader :title="$t('pageTitle.tokenUsage')" :description="$t('pageDesc.tokenUsage')" />

    <!-- Date Range + Actions -->
    <div class="flex items-center gap-2 flex-wrap">
      <Input id="token-usage-start-date" name="token-usage-start-date" v-model="startDate" type="date" class="w-36" />
      <span class="text-muted-foreground">{{ $t('common.to') }}</span>
      <Input id="token-usage-end-date" name="token-usage-end-date" v-model="endDate" type="date" class="w-36" />
      <Button size="sm" :loading="loading" @click="fetchData">
        <Loader2 v-if="loading" class="mr-1 h-4 w-4 animate-spin" />
        {{ $t('common.search') }}
      </Button>
      <Button variant="outline" size="sm" @click="showDetail = !showDetail">
        {{ showDetail ? $t('tokenUsage.hideDetail') : $t('tokenUsage.showDetail') }}
        <ChevronDown v-if="!showDetail" class="ml-1 h-3 w-3" />
        <ChevronUp v-else class="ml-1 h-3 w-3" />
      </Button>
    </div>

    <!-- Stats Cards -->
    <div class="grid gap-4 md:grid-cols-5">
      <Card>
        <CardHeader class="flex flex-row items-center justify-between pb-2">
          <CardTitle class="text-sm font-medium">{{ $t('tokenUsage.statCards.requestCount') }}</CardTitle>
          <BarChart3 class="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div class="text-2xl font-bold">{{ totalCalls() }}</div>
        </CardContent>
      </Card>
      <Card>
        <CardHeader class="flex flex-row items-center justify-between pb-2">
          <CardTitle class="text-sm font-medium">{{ $t('tokenUsage.statCards.totalTokens') }}</CardTitle>
          <BarChart3 class="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div class="text-2xl font-bold">{{ totalTokens().toLocaleString() }}</div>
        </CardContent>
      </Card>
      <Card>
        <CardHeader class="flex flex-row items-center justify-between pb-2">
          <CardTitle class="text-sm font-medium">{{ $t('tokenUsage.statCards.inputTokens') }}</CardTitle>
          <BarChart3 class="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div class="text-2xl font-bold">{{ totalPrompt().toLocaleString() }}</div>
        </CardContent>
      </Card>
      <Card>
        <CardHeader class="flex flex-row items-center justify-between pb-2">
          <CardTitle class="text-sm font-medium">{{ $t('tokenUsage.imageModelCalls') }}</CardTitle>
          <BarChart3 class="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div class="text-2xl font-bold">{{ imageModelCalls }}</div>
        </CardContent>
      </Card>
      <Card>
        <CardHeader class="flex flex-row items-center justify-between pb-2">
          <CardTitle class="text-sm font-medium">{{ $t('tokenUsage.statCards.totalCost') }}</CardTitle>
          <BarChart3 class="h-4 w-4 text-muted-foreground" />
        </CardHeader>
        <CardContent>
          <div class="text-2xl font-bold">¥{{ totalCost().toFixed(2) }}</div>
        </CardContent>
      </Card>
    </div>

    <!-- Summary Table -->
    <div class="border border-border rounded-lg overflow-hidden">
      <div class="px-4 py-3 border-b border-border bg-muted/20 font-semibold text-sm">{{ $t('tokenUsage.byModel') }}</div>
      <div v-if="loading" class="p-4 space-y-2">
        <Skeleton v-for="i in 3" :key="i" class="h-8 w-full" />
      </div>
      <div v-else-if="summaryData.length === 0" class="text-center py-8 text-sm text-muted-foreground">
        {{ $t('common.noData') }}
      </div>
      <table v-else class="w-full text-sm">
        <thead>
          <tr class="bg-muted/30 border-b border-border">
            <th class="text-left px-4 py-2 font-medium text-muted-foreground">{{ $t('tokenUsage.columns.agentName') }}</th>
            <th class="text-left px-4 py-2 font-medium text-muted-foreground">{{ $t('tokenUsage.columns.modelName') }}</th>
            <th class="text-left px-4 py-2 font-medium text-muted-foreground">{{ $t('tokenUsage.columns.username') }}</th>
            <th class="text-left px-4 py-2 font-medium text-muted-foreground w-16">{{ $t('common.type') }}</th>
            <th class="text-right px-4 py-2 font-medium text-muted-foreground w-24">{{ $t('tokenUsage.callCount') }}</th>
            <th class="text-right px-4 py-2 font-medium text-muted-foreground w-28">{{ $t('tokenUsage.statCards.inputTokens') }}</th>
            <th class="text-right px-4 py-2 font-medium text-muted-foreground w-28">{{ $t('tokenUsage.statCards.outputTokens') }}</th>
            <th class="text-right px-4 py-2 font-medium text-muted-foreground w-24">{{ $t('tokenUsage.statCards.totalTokens') }}</th>
            <th class="text-right px-4 py-2 font-medium text-muted-foreground w-28">{{ $t('tokenUsage.columns.cnyCost') }}</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-border">
          <tr v-for="(row, idx) in summaryData" :key="row.modelName + '-' + (row.agentName || '') + '-' + idx" class="hover:bg-muted/30">
            <td class="px-4 py-2.5 font-medium">{{ row.agentName || '-' }}</td>
            <td class="px-4 py-2.5">{{ row.modelName }}</td>
            <td class="px-4 py-2.5 text-sm">{{ row.username || '-' }}</td>
            <td class="px-4 py-2.5">
              <Badge v-if="row.modelType === 'IMAGE'" variant="secondary" class="text-xs">{{ $t(modelTypeKeys.IMAGE) }}</Badge>
              <span v-else class="text-xs text-muted-foreground">{{ $t(modelTypeKeys.TEXT) }}</span>
            </td>
            <td class="px-4 py-2.5 text-right tabular-nums">{{ row.callCount.toLocaleString() }}</td>
            <td class="px-4 py-2.5 text-right tabular-nums">{{ row.promptTokens.toLocaleString() }}</td>
            <td class="px-4 py-2.5 text-right tabular-nums">{{ row.completionTokens.toLocaleString() }}</td>
            <td class="px-4 py-2.5 text-right tabular-nums font-medium">{{ row.totalTokens.toLocaleString() }}</td>
            <td class="px-4 py-2.5 text-right tabular-nums font-medium">¥{{ (row.cnyCost ?? 0).toFixed(2) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Detail Records -->
    <div v-if="showDetail" class="border border-border rounded-lg overflow-hidden">
      <div class="px-4 py-3 border-b border-border bg-muted/20 font-semibold text-sm">{{ $t('tokenUsage.detailRecords') }}</div>
      <div v-if="loading" class="p-4 text-sm text-muted-foreground">{{ $t('common.loading') }}</div>
      <div v-else-if="detailData.length === 0" class="text-center py-8 text-sm text-muted-foreground">
        {{ $t('common.noData') }}
      </div>
      <div v-else class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="bg-muted/30 border-b border-border">
              <th class="text-left px-3 py-2 font-medium text-muted-foreground">{{ $t('common.time') }}</th>
              <th class="text-left px-3 py-2 font-medium text-muted-foreground">{{ $t('tokenUsage.columns.agentName') }}</th>
              <th class="text-left px-3 py-2 font-medium text-muted-foreground">{{ $t('tokenUsage.columns.modelName') }}</th>
              <th class="text-left px-3 py-2 font-medium text-muted-foreground">{{ $t('tokenUsage.columns.username') }}</th>
              <th class="text-left px-3 py-2 font-medium text-muted-foreground w-14">{{ $t('common.type') }}</th>
              <th class="text-right px-3 py-2 font-medium text-muted-foreground w-20">{{ $t('tokenUsage.statCards.inputTokens') }}</th>
              <th class="text-right px-3 py-2 font-medium text-muted-foreground w-24">{{ $t('tokenUsage.statCards.outputTokens') }}</th>
              <th class="text-right px-3 py-2 font-medium text-muted-foreground w-20">{{ $t('common.total') }}</th>
              <th class="text-right px-3 py-2 font-medium text-muted-foreground w-28">{{ $t('tokenUsage.columns.cnyCost') }}</th>
              <th class="text-left px-3 py-2 font-medium text-muted-foreground w-16">{{ $t('common.status') }}</th>
              <th class="text-left px-3 py-2 font-medium text-muted-foreground">{{ $t('common.error') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-border">
            <tr v-for="row in detailData" :key="row.id" class="hover:bg-muted/30">
              <td class="px-3 py-2 text-xs text-muted-foreground whitespace-nowrap">{{ formatTime(row.createdAt) }}</td>
              <td class="px-3 py-2 text-xs">{{ row.agentName || '-' }}</td>
              <td class="px-3 py-2 text-xs">{{ row.modelName }}</td>
              <td class="px-3 py-2 text-xs">{{ row.username || '-' }}</td>
              <td class="px-3 py-2">
                <Badge v-if="row.modelType === 'IMAGE'" variant="secondary" class="text-xs">{{ $t(modelTypeKeys.IMAGE) }}</Badge>
                <span v-else class="text-xs text-muted-foreground">{{ $t(modelTypeKeys.TEXT) }}</span>
              </td>
              <td class="px-3 py-2 text-right tabular-nums text-xs">{{ row.promptTokens.toLocaleString() }}</td>
              <td class="px-3 py-2 text-right tabular-nums text-xs">{{ row.completionTokens.toLocaleString() }}</td>
              <td class="px-3 py-2 text-right tabular-nums text-xs font-medium">{{ row.totalTokens.toLocaleString() }}</td>
              <td class="px-3 py-2 text-right tabular-nums text-xs font-medium">¥{{ (row.cnyCost ?? 0).toFixed(2) }}</td>
              <td class="px-3 py-2">
                <span class="text-xs" :class="row.success ? 'text-green-600' : 'text-destructive'">
                  {{ row.success ? $t('common.success') : $t('common.fail') }}
                </span>
              </td>
              <td class="px-3 py-2 text-xs text-muted-foreground max-w-[200px] truncate" :title="row.errorMessage || ''">
                {{ row.errorMessage || '-' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
