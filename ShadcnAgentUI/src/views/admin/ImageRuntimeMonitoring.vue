<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Activity, AlertTriangle, CheckCircle2, Clock3, Loader2, RefreshCw, RotateCcw, TimerReset } from 'lucide-vue-next'
import { getImageRuntimeMonitoringApi, type ImageRuntimeMonitoring } from '@/api/image-runtime-monitoring'
import { toast } from 'vue-sonner'

const data = ref<ImageRuntimeMonitoring | null>(null)
const loading = ref(false)
let refreshTimer: ReturnType<typeof setInterval> | null = null

const statusCards = computed(() => {
  const value = data.value
  return [
    { label: '排队任务', value: value?.jobsByStatus.PENDING ?? 0, icon: Clock3 },
    { label: '运行任务', value: value?.jobsByStatus.RUNNING ?? 0, icon: Activity },
    { label: '成功率', value: percent(value?.successRate), icon: CheckCircle2 },
    { label: '失败率', value: percent(value?.failureRate), icon: AlertTriangle },
    { label: 'P95 生成耗时', value: duration(value?.p95JobDurationMs), icon: TimerReset },
    { label: '超时 / 重试', value: `${value?.timeouts ?? 0} / ${value?.retries ?? 0}`, icon: RotateCcw },
  ]
})

async function load(showError = true) {
  if (loading.value) return
  loading.value = true
  try {
    data.value = await getImageRuntimeMonitoringApi()
  } catch {
    if (showError) toast.error('图片运行监控加载失败')
  } finally {
    loading.value = false
  }
}

function percent(value?: number) { return `${((value ?? 0) * 100).toFixed(1)}%` }
function duration(value?: number) {
  const ms = value ?? 0
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)} 秒` : `${Math.round(ms)} ms`
}
function dateTime(value?: string | null) { return value ? new Date(value).toLocaleString('zh-CN') : '-' }
function providerName(value: string) { return value === 'unknown' ? '未知' : value }

onMounted(() => {
  void load()
  refreshTimer = setInterval(() => void load(false), 15000)
})
onUnmounted(() => { if (refreshTimer) clearInterval(refreshTimer) })
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="图片运行监控" description="查看当前进程的图片任务、供应商性能和最近失败记录">
      <Button variant="outline" :disabled="loading" @click="load()">
        <Loader2 v-if="loading" class="mr-2 h-4 w-4 animate-spin" />
        <RefreshCw v-else class="mr-2 h-4 w-4" />刷新
      </Button>
    </PageHeader>

    <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
      <Card v-for="item in statusCards" :key="item.label">
        <CardContent class="flex items-center justify-between p-4">
          <div><p class="text-xs text-muted-foreground">{{ item.label }}</p><p class="mt-1 text-xl font-bold">{{ item.value }}</p></div>
          <component :is="item.icon" class="h-5 w-5 text-muted-foreground/50" />
        </CardContent>
      </Card>
    </div>

    <div class="grid gap-4 lg:grid-cols-3">
      <Card>
        <CardHeader><CardTitle class="text-base">任务状态</CardTitle></CardHeader>
        <CardContent class="space-y-3">
          <div v-for="(count, status) in data?.jobsByStatus" :key="status" class="flex items-center justify-between text-sm">
            <span class="text-muted-foreground">{{ status }}</span><Badge variant="secondary">{{ count }}</Badge>
          </div>
          <div class="border-t pt-3 text-sm"><span class="text-muted-foreground">Worker 活跃数</span><span class="float-right font-medium">{{ data?.workerActive ?? 0 }}</span></div>
        </CardContent>
      </Card>
      <Card class="lg:col-span-2">
        <CardHeader><CardTitle class="text-base">运行时概览（进程启动后）</CardTitle></CardHeader>
        <CardContent class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <div><p class="text-xs text-muted-foreground">完成任务</p><p class="text-2xl font-semibold">{{ data?.completed ?? 0 }}</p></div>
          <div><p class="text-xs text-muted-foreground">平均生成耗时</p><p class="text-2xl font-semibold">{{ duration(data?.averageJobDurationMs) }}</p></div>
          <div><p class="text-xs text-muted-foreground">租约恢复</p><p class="text-2xl font-semibold">{{ data?.recovered ?? 0 }}</p></div>
          <div><p class="text-xs text-muted-foreground">数据更新时间</p><p class="mt-1 text-sm font-medium">{{ dateTime(data?.generatedAt) }}</p></div>
        </CardContent>
      </Card>
    </div>

    <Card>
      <CardHeader><CardTitle class="text-base">供应商性能</CardTitle></CardHeader>
      <CardContent class="overflow-x-auto">
        <table class="w-full min-w-[780px] text-sm">
          <thead><tr class="border-b text-left text-xs text-muted-foreground"><th class="py-2">供应商</th><th>完成</th><th>失败</th><th>成功率</th><th>错误</th><th>超时</th><th>平均请求耗时</th><th>P95 请求耗时</th></tr></thead>
          <tbody>
            <tr v-for="provider in data?.providers" :key="provider.provider" class="border-b last:border-0">
              <td class="py-3 font-medium">{{ providerName(provider.provider) }}</td><td>{{ provider.completed }}</td><td>{{ provider.failed }}</td><td>{{ percent(provider.successRate) }}</td><td>{{ provider.errors }}</td><td>{{ provider.timeouts }}</td><td>{{ duration(provider.averageRequestDurationMs) }}</td><td>{{ duration(provider.p95RequestDurationMs) }}</td>
            </tr>
            <tr v-if="!data?.providers.length"><td colspan="8" class="py-10 text-center text-muted-foreground">暂无运行数据</td></tr>
          </tbody>
        </table>
      </CardContent>
    </Card>

    <Card>
      <CardHeader><CardTitle class="text-base">最近失败任务</CardTitle></CardHeader>
      <CardContent class="overflow-x-auto">
        <table class="w-full min-w-[880px] text-sm">
          <thead><tr class="border-b text-left text-xs text-muted-foreground"><th class="py-2">任务</th><th>供应商</th><th>能力</th><th>错误码</th><th>安全错误信息</th><th>可重试</th><th>完成时间</th></tr></thead>
          <tbody>
            <tr v-for="failure in data?.recentFailures" :key="failure.id" class="border-b last:border-0 align-top">
              <td class="py-3 font-medium">#{{ failure.id }}</td><td>{{ failure.provider }}</td><td>{{ failure.capability }}</td><td><Badge variant="outline">{{ failure.errorCode || '-' }}</Badge></td><td class="max-w-[320px] whitespace-normal">{{ failure.message || '-' }}</td><td>{{ failure.retryable ? '是' : '否' }}</td><td>{{ dateTime(failure.completedAt) }}</td>
            </tr>
            <tr v-if="!data?.recentFailures.length"><td colspan="7" class="py-10 text-center text-muted-foreground">暂无失败任务</td></tr>
          </tbody>
        </table>
      </CardContent>
    </Card>
  </div>
</template>
