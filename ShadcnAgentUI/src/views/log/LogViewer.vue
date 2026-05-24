<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/PageHeader.vue'
import StatCards from '@/components/StatCards.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import SearchInput from '@/components/SearchInput.vue'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  queryLogsApi,
  getLogStatsApi,
  clearLogsApi,
} from '@/api/systemLog'
import type { SystemLogDTO, LogStatsDTO } from '@/api/systemLog'
import { PAGE_SIZE_OPTIONS } from '@/constants'
import {
  FileText,
  Loader2,
  Download,
  Trash2,
  AlertTriangle,
  AlertCircle,
} from 'lucide-vue-next'
import { toast } from 'sonner'

const { t, locale } = useI18n()

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
const filterStartDate = ref('')
const filterEndDate = ref('')

const detailEntry = ref<SystemLogDTO | null>(null)
const showDetail = ref(false)
const parsedDetailData = ref('')

const levelKeys: Record<string, string> = {
  DEBUG: 'log.level.DEBUG',
  INFO: 'log.level.INFO',
  WARN: 'log.level.WARN',
  ERROR: 'log.level.ERROR',
}

const categoryKeys: Record<string, string> = {
  API: 'log.category.API',
  USER_ACTION: 'log.category.USER_ACTION',
  ROUTER: 'log.category.ROUTER',
  ERROR: 'log.category.ERROR',
  PERFORMANCE: 'log.category.PERFORMANCE',
  AUTH: 'log.category.AUTH',
}

const levelOptions = ['DEBUG', 'INFO', 'WARN', 'ERROR'].map(l => ({ label: levelKeys[l], value: l }))
const categoryOptions = ['API', 'USER_ACTION', 'ROUTER', 'ERROR', 'PERFORMANCE', 'AUTH']
  .map(c => ({ label: categoryKeys[c], value: c }))

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

async function load() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try {
    const [logData, statsData] = await Promise.all([
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
    logs.value = logData.content
    total.value = logData.page.totalElements
    stats.value = statsData
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('error.loadLogsFailed')
  } finally {
    loading.value = false
  }
}

function onPageChange(newPage: number) {
  page.value = newPage
  load()
}

function toggleLevel(level: string) {
  const idx = filterLevels.value.indexOf(level)
  if (idx >= 0) filterLevels.value.splice(idx, 1)
  else filterLevels.value.push(level)
  onFilterChange()
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
  try {
    const data = await queryLogsApi({
      page: 0,
      size: 99999,
      levels: filterLevels.value.length ? filterLevels.value : undefined,
      categories: filterCategories.value.length ? filterCategories.value : undefined,
      search: filterSearch.value || undefined,
      startDate: filterStartDate.value ? new Date(filterStartDate.value).toISOString() : undefined,
      endDate: filterEndDate.value ? new Date(filterEndDate.value).toISOString() : undefined,
    })
    const allLogs = data.content
    const blob = new Blob([JSON.stringify(allLogs, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `logs-${new Date().toISOString().slice(0, 10)}.json`
    a.click()
    URL.revokeObjectURL(url)
    toast.success(t('toast.exportSuccess'))
  } catch {
    toast.error(t('error.exportFailed'))
  }
}

const showClearDialog = ref(false)

async function handleClear() {
  showClearDialog.value = true
}

async function confirmClear() {
  try {
    await clearLogsApi()
    toast.success(t('toast.clearSuccess'))
    load()
  } catch {
    toast.error(t('error.clearLogsFailed'))
  }
  showClearDialog.value = false
}

const statsCards = computed(() => {
  if (!stats.value) return null
  return [
    { label: t('log.stats.total'), value: stats.value.total, icon: FileText },
    { label: t('log.stats.errors'), value: stats.value.byLevel.ERROR ?? 0, icon: AlertCircle },
    { label: t('log.stats.warnings'), value: stats.value.byLevel.WARN ?? 0, icon: AlertTriangle },
    { label: t('log.stats.api'), value: stats.value.byCategory.API ?? 0, icon: FileText },
    { label: t('log.stats.userActions'), value: stats.value.byCategory.USER_ACTION ?? 0, icon: FileText },
  ]
})

function formatDateTime(dateStr: string) {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString(locale.value || 'zh-CN')
}

onMounted(() => { load() })
</script>

<template>
  <div class="space-y-6">
    <PageHeader :title="$t('pageTitle.logs')" :description="$t('pageDesc.logs')" />

    <div v-if="error" class="bg-destructive/10 text-destructive text-sm px-4 py-2 rounded-md flex items-center justify-between">
      <span>{{ error }}</span>
      <Button variant="ghost" size="sm" class="h-6 text-xs" @click="error = ''">{{ $t('common.close') }}</Button>
    </div>

    <StatCards v-if="statsCards" :items="statsCards" :columns="5" />

    <!-- 24h error trend -->
    <Card v-if="stats?.last24h?.length">
      <CardContent class="p-4">
        <p class="text-sm font-medium mb-3">{{ $t('log.stats.hourlyTrend') }}</p>
        <div class="flex items-end gap-1 h-16 px-1">
          <div
            v-for="h in stats.last24h"
            :key="h.hour"
            :title="t('log.hourlyTooltip', { hour: h.hour, count: h.count })"
            class="flex-1 flex flex-col items-center gap-1"
          >
            <div
              :style="{
                width: '100%',
                height: h.count > 0 ? `${Math.min(h.count / Math.max(...stats.last24h.map(x => x.count), 1), 1) * 52}px` : '2px',
                background: h.count > 0 ? '#ef4444' : '#e5e7eb',
                borderRadius: '2px',
                minHeight: '2px',
              }"
            />
            <span class="text-[10px] text-muted-foreground whitespace-nowrap">{{ h.hour }}</span>
          </div>
        </div>
      </CardContent>
    </Card>

    <!-- Filters -->
    <div class="flex items-center gap-2 flex-wrap">
      <div class="flex gap-1">
        <Button
          v-for="opt in levelOptions"
          :key="opt.value"
          variant="outline"
          size="sm"
          class="h-8 text-xs"
          :class="filterLevels.includes(opt.value) ? 'border-primary bg-primary/5' : ''"
          @click="toggleLevel(opt.value)"
        >
          {{ $t(opt.label) }}
        </Button>
      </div>
      <div class="w-px h-6 bg-border" />
      <Select v-model="filterCategories" @update:model-value="onFilterChange">
        <SelectTrigger class="w-[140px] h-8">
          <SelectValue :placeholder="$t('log.filters.category')" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem v-for="opt in categoryOptions" :key="opt.value" :value="opt.value">{{ $t(opt.label) }}</SelectItem>
        </SelectContent>
      </Select>
      <Input v-model="filterStartDate" type="date" class="w-36 h-8" @change="onFilterChange" />
      <span class="text-xs text-muted-foreground">{{ $t('common.to') }}</span>
      <Input v-model="filterEndDate" type="date" class="w-36 h-8" @change="onFilterChange" />
      <SearchInput v-model="filterSearch" :placeholder="$t('log.filters.search')" input-class="w-44" @search="onFilterChange" />
      <Button variant="secondary" size="sm" class="h-8" :disabled="loading" @click="onFilterChange">
        <Loader2 v-if="loading" class="mr-1 h-3.5 w-3.5 animate-spin" />
        {{ $t('log.filters.query') }}
      </Button>
    </div>

    <!-- Actions -->
    <div class="flex items-center justify-end gap-2">
      <Button variant="outline" size="sm" class="h-8" @click="handleExport">
        <Download class="mr-1 h-3.5 w-3.5" />{{ $t('common.export') }}
      </Button>
      <Button variant="destructive" size="sm" class="h-8" @click="handleClear">
        <Trash2 class="mr-1 h-3.5 w-3.5" />{{ $t('log.stats.clearLogs') }}
      </Button>
    </div>

    <!-- Log Table -->
    <div class="border border-border rounded-lg overflow-hidden">
      <div v-if="loading" class="p-4 space-y-2">
        <Skeleton v-for="i in 8" :key="i" class="h-8 w-full" />
      </div>
      <div v-else class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="bg-muted/50 border-b border-border">
              <th class="text-left px-3 py-2.5 font-medium text-muted-foreground whitespace-nowrap">{{ $t('log.columns.time') }}</th>
              <th class="text-left px-3 py-2.5 font-medium text-muted-foreground w-16">{{ $t('log.columns.level') }}</th>
              <th class="text-left px-3 py-2.5 font-medium text-muted-foreground w-20">{{ $t('log.columns.category') }}</th>
              <th class="text-left px-3 py-2.5 font-medium text-muted-foreground">{{ $t('log.columns.message') }}</th>
              <th class="text-left px-3 py-2.5 font-medium text-muted-foreground w-16">{{ $t('log.columns.duration') }}</th>
              <th class="text-left px-3 py-2.5 font-medium text-muted-foreground w-24">{{ $t('log.columns.page') }}</th>
              <th class="text-left px-3 py-2.5 font-medium text-muted-foreground w-16">{{ $t('log.columns.action') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-border">
            <tr v-for="entry in logs" :key="entry.id" class="hover:bg-muted/30 transition-colors">
              <td class="px-3 py-2.5 text-xs text-muted-foreground whitespace-nowrap">
                {{ formatDateTime(entry.createdAt) }}
              </td>
              <td class="px-3 py-2.5">
                <Badge
                  variant="outline"
                  class="text-xs"
                  :class="{
                    'text-gray-500 border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-900': entry.level === 'DEBUG',
                    'text-blue-600 border-blue-200 bg-blue-50 dark:border-blue-800 dark:bg-blue-950': entry.level === 'INFO',
                    'text-amber-600 border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-950': entry.level === 'WARN',
                    'text-destructive border-destructive/30 bg-destructive/10': entry.level === 'ERROR',
                  }"
                >
                  {{ $t(levelKeys[entry.level] || entry.level) }}
                </Badge>
              </td>
              <td class="px-3 py-2.5">
                <span class="text-xs text-muted-foreground">{{ $t(categoryKeys[entry.category] || entry.category) }}</span>
              </td>
              <td class="px-3 py-2.5 text-xs max-w-md truncate" :title="entry.message">{{ entry.message }}</td>
              <td class="px-3 py-2.5 text-xs text-muted-foreground">{{ entry.duration != null ? `${entry.duration}ms` : '-' }}</td>
              <td class="px-3 py-2.5 text-xs text-muted-foreground">{{ entry.route || '-' }}</td>
              <td class="px-3 py-2.5">
                <Button variant="ghost" size="sm" class="h-7 text-xs" @click="openDetail(entry)">{{ $t('common.detail') }}</Button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Pagination -->
    <div v-if="total > 0" class="flex items-center justify-between">
      <p class="text-xs text-muted-foreground">
        {{ $t('log.pagination', { total: total, page: page, totalPages: totalPages }) }}
      </p>
      <div class="flex items-center gap-1">
        <Button variant="outline" size="sm" class="h-7 text-xs" :disabled="page <= 1" @click="onPageChange(page - 1)">
          {{ $t('common.prevPage') }}
        </Button>
        <Button
          v-for="p in Math.min(totalPages, 5)"
          :key="p"
          variant="outline"
          size="sm"
          class="h-7 text-xs min-w-[28px]"
          :class="p === page ? 'border-primary bg-primary/5' : ''"
          @click="onPageChange(p)"
        >
          {{ p }}
        </Button>
        <Button variant="outline" size="sm" class="h-7 text-xs" :disabled="page >= totalPages" @click="onPageChange(page + 1)">
          {{ $t('common.nextPage') }}
        </Button>
      </div>
      <div class="flex items-center gap-1">
        <span class="text-xs text-muted-foreground">{{ $t('common.perPage') }}</span>
        <Select
          :model-value="pageSize"
          @update:model-value="(v: unknown) => { const n = Number(v); if (!isNaN(n)) { pageSize = n; page = 1; load() } }"
        >
          <SelectTrigger class="w-16 h-7 text-xs">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem v-for="opt in PAGE_SIZE_OPTIONS" :key="opt" :value="opt">{{ opt }}</SelectItem>
          </SelectContent>
        </Select>
      </div>
    </div>

    <!-- Detail Modal -->
    <Dialog :open="showDetail" @update:open="showDetail = $event">
      <DialogContent class="sm:max-w-xl max-h-[80vh] overflow-y-auto">
        <DialogHeader v-if="detailEntry">
          <DialogTitle>{{ $t('log.detail.title') }}</DialogTitle>
        </DialogHeader>
        <div v-if="detailEntry" class="space-y-4">
          <div class="grid grid-cols-2 gap-3 text-sm">
            <div class="space-y-1">
              <p class="text-xs text-muted-foreground">{{ $t('log.detail.time') }}</p>
              <p>{{ formatDateTime(detailEntry.createdAt) }}</p>
            </div>
            <div class="space-y-1">
              <p class="text-xs text-muted-foreground">{{ $t('log.detail.level') }}</p>
              <Badge
                variant="outline"
                class="text-xs"
                :class="{
                  'text-destructive border-destructive/30 bg-destructive/10': detailEntry.level === 'ERROR',
                  'text-amber-600 border-amber-200 bg-amber-50': detailEntry.level === 'WARN',
                  'text-blue-600 border-blue-200 bg-blue-50': detailEntry.level === 'INFO',
                }"
              >
                {{ $t(levelKeys[detailEntry.level] || detailEntry.level) }}
              </Badge>
            </div>
            <div class="space-y-1">
              <p class="text-xs text-muted-foreground">{{ $t('log.detail.category') }}</p>
              <p>{{ $t(categoryKeys[detailEntry.category] || detailEntry.category) }}</p>
            </div>
            <div class="space-y-1">
              <p class="text-xs text-muted-foreground">{{ $t('log.detail.duration') }}</p>
              <p>{{ detailEntry.duration != null ? `${detailEntry.duration}ms` : '-' }}</p>
            </div>
            <div class="space-y-1">
              <p class="text-xs text-muted-foreground">{{ $t('log.detail.page') }}</p>
              <p>{{ detailEntry.route || '-' }}</p>
            </div>
            <div class="space-y-1">
              <p class="text-xs text-muted-foreground">{{ $t('log.detail.userId') }}</p>
              <p>{{ detailEntry.userId ?? '-' }}</p>
            </div>
          </div>
          <div class="space-y-1">
            <p class="text-xs text-muted-foreground">{{ $t('log.detail.message') }}</p>
            <p class="text-sm bg-muted/30 rounded-md p-3">{{ detailEntry.message }}</p>
          </div>
          <div v-if="parsedDetailData" class="space-y-1">
            <p class="text-xs text-muted-foreground">{{ $t('log.detail.data') }}</p>
            <pre class="text-xs bg-muted/30 rounded-md p-3 overflow-x-auto max-h-60">{{ parsedDetailData }}</pre>
          </div>
        </div>
      </DialogContent>
    </Dialog>

    <ConfirmDialog
      :open="showClearDialog"
      @update:open="showClearDialog = $event"
      :title="$t('log.confirmClear.title')"
      :description="$t('log.confirmClear.desc')"
      :confirm-text="$t('common.confirm')"
      @confirm="confirmClear"
    />
  </div>
</template>
