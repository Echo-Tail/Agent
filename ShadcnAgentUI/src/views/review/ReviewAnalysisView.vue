<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { toast } from 'sonner'
import {
  VisAxis, VisDonut, VisDonutSelectors, VisGroupedBar, VisSingleContainer, VisXYContainer,
  VisXYLabels,
} from '@unovis/vue'
import { BarChart3, LoaderCircle, Plus, RefreshCw, Search, Sparkles } from 'lucide-vue-next'
import {
  createReviewProject, getAnalysisRun, getReviewCollection, getReviewDashboard,
  listAnalysisRuns, listOpportunityInsights, listReviewInsights, listReviewOpportunities, listReviewProjects,
  retryAnalysisFailures, startReviewAnalysis, startReviewCollection,
  type AnalysisRun, type ReviewCollection, type ReviewDashboard, type ReviewInsight,
  type ReviewOpportunity, type ReviewProject,
} from '@/api/review-analysis'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import {
  ChartContainer, ChartCrosshair, ChartTooltip, ChartTooltipContent, componentToString,
  type ChartConfig,
} from '@/components/ui/chart'

const props = defineProps<{ section: 'tasks' | 'report' | 'evidence' }>()
const router = useRouter()
const projects = ref<ReviewProject[]>([])
const selectedProjectId = ref<number>()
const collection = ref<ReviewCollection>()
const run = ref<AnalysisRun>()
const dashboard = ref<ReviewDashboard>()
const opportunities = ref<ReviewOpportunity[]>([])
const insights = ref<ReviewInsight[]>([])
const createOpen = ref(false)
const detailOpen = ref(false)
const opportunityDetailOpen = ref(false)
const asinInput = ref('')
const selectedInsight = ref<ReviewInsight>()
const selectedOpportunity = ref<ReviewOpportunity>()
const opportunityInsights = ref<ReviewInsight[]>([])
const opportunityDetailLoading = ref(false)
const keyword = ref('')
const ratingSort = ref<'asc' | 'desc'>('asc')
const loading = ref(false)
const analysisStarting = ref(false)
const workflowError = ref('')
let pollTimer: ReturnType<typeof setInterval> | undefined
const POLL_INTERVAL_MS = 5000

const selectedProject = computed(() => projects.value.find(item => item.id === selectedProjectId.value))
const isCollecting = computed(() => collection.value && !['success', 'partial', 'failed'].includes(collection.value.status))
const isAnalyzing = computed(() => run.value && ['pending', 'running'].includes(run.value.status))
const isBusy = computed(() => Boolean(isCollecting.value || isAnalyzing.value || analysisStarting.value))
const sectionTitle = computed(() => ({
  tasks: '分析任务',
  report: '洞察报告',
  evidence: '评论证据',
}[props.section]))
const progress = computed(() => {
  if (run.value?.status === 'draft' || run.value?.status === 'confirmed') return 100
  if (run.value && isAnalyzing.value) {
    const total = run.value.sourceReviewCount || 1
    const completed = run.value.processedReviewCount + run.value.failedReviewCount
    if (completed >= total) return 95
    return Math.min(90, Math.round(completed / total * 90))
  }
  return 0
})
const progressLabel = computed(() => {
  if (isCollecting.value) return '正在采集 Amazon 评论'
  if (analysisStarting.value) return '正在准备分析'
  if (isAnalyzing.value && run.value
      && run.value.sourceReviewCount > 0
      && run.value.processedReviewCount + run.value.failedReviewCount >= run.value.sourceReviewCount) {
    return '正在聚合问题并生成改进机会'
  }
  if (isAnalyzing.value) return '正在识别问题并生成中文洞察'
  if (run.value?.status === 'draft') return '分析完成'
  if (run.value?.status === 'failed' || collection.value?.status === 'failed') return '分析失败'
  return '等待开始'
})

const labels: Record<string, string> = {
  critical: 'P0 · 安全风险', major: 'P1 · 核心功能', moderate: 'P2 · 体验问题', minor: 'P3 · 轻微问题',
  negative: '负面', mixed: '正负混合', positive: '正面',
  installation_setup: '安装与配置', daily_commute: '日常通勤', carplay_android_auto: '手机互联',
  bluetooth_call: '蓝牙通话', music_audio: '音乐与音频', navigation: '导航',
  reverse_parking: '倒车与泊车', night_driving: '夜间驾驶', cold_start: '冷启动',
  long_trip: '长途驾驶', firmware_update: '固件升级', after_sales: '售后服务',
  vehicle_compatibility: '车辆兼容', wiring_installation: '线束与安装', display_touch: '屏幕与触控',
  carplay: 'CarPlay', android_auto: 'Android Auto', bluetooth_wifi: '蓝牙与 Wi-Fi',
  audio_dsp_microphone: '音频、DSP 与麦克风', gps_navigation: 'GPS 与导航',
  reverse_camera: '倒车摄像头', ui_interaction: '界面交互', performance_stability: '性能与稳定性',
  hardware_reliability: '硬件可靠性', documentation_support: '说明与支持',
  other: '其它',
}
const displayLabel = (value: string) => labels[value] ?? value
const statusLabel = (status: string) => ({
  draft: '待开始', collecting: '采集中', collected: '待分析', analyzing: '分析中',
  review: '已完成', confirmed: '已完成', failed: '失败',
}[status] ?? status)

const parsedAsins = computed(() => {
  const matches = asinInput.value.toUpperCase().match(/(?<![A-Z0-9])[A-Z0-9]{10}(?![A-Z0-9])/g) ?? []
  return [...new Set(matches)]
})
const sortedInsights = computed(() => [...insights.value].sort((left, right) => {
  if (left.rating == null) return right.rating == null ? left.id - right.id : 1
  if (right.rating == null) return -1
  const difference = left.rating - right.rating
  return ratingSort.value === 'asc' ? difference || left.id - right.id : -difference || left.id - right.id
}))

const chartColors = ['var(--chart-1)', 'var(--chart-2)', 'var(--chart-3)', 'var(--chart-4)', 'var(--chart-5)']
type DimensionDatum = { key: string; label: string; count: number; color: string }

function dimensionData(items: ReviewDashboard['severities']): DimensionDatum[] {
  return items.map((item, index) => ({
    key: item.key,
    label: displayLabel(item.key),
    count: item.count,
    color: chartColors[index % chartColors.length],
  }))
}

function dimensionConfig(items: DimensionDatum[]): ChartConfig {
  return Object.fromEntries(items.map(item => [
    item.key, { label: item.label, color: item.color },
  ]))
}

const severityChartData = computed(() => dimensionData(dashboard.value?.severities ?? []))
const scenarioChartData = computed(() => dimensionData(dashboard.value?.scenarios ?? []))
const moduleChartData = computed(() =>
  dimensionData(dashboard.value?.modules ?? []).sort((left, right) => right.count - left.count),
)
const moduleChartLabelOffset = computed(() =>
  Math.max(0.25, (moduleChartData.value[0]?.count ?? 0) * 0.04),
)
const severityChartConfig = computed(() => dimensionConfig(severityChartData.value))
const scenarioChartConfig = computed(() => dimensionConfig(scenarioChartData.value))
const valueChartConfig = {
  count: { label: '评论数量', color: 'var(--chart-1)' },
} satisfies ChartConfig
const moduleChartConfig = computed(() => valueChartConfig)

function truncate(value: string, max: number) {
  if (!value || value.length <= max) return value
  return `${value.slice(0, max)}…`
}

async function loadProjects(selectId?: number) {
  projects.value = await listReviewProjects()
  const projectId = selectId ?? selectedProjectId.value ?? projects.value[0]?.id
  if (projectId) await selectProject(projectId)
}

async function selectProject(projectId: number) {
  selectedProjectId.value = projectId
  collection.value = undefined
  run.value = undefined
  workflowError.value = ''
  dashboard.value = undefined
  opportunities.value = []
  insights.value = []
  const project = projects.value.find(item => item.id === projectId)
  const runs = await listAnalysisRuns(projectId)
  run.value = runs[0]
  if (project?.latestCollectionId) {
    collection.value = await getReviewCollection(projectId, project.latestCollectionId, true)
  }
  if (!run.value && collection.value && ['success', 'partial'].includes(collection.value.status)) {
    await startAutomaticAnalysis()
  } else if (run.value && !isAnalyzing.value) {
    await loadResults()
  }
  if (isCollecting.value || isAnalyzing.value) startPolling()
}

async function createTask() {
  const values = parsedAsins.value
  if (!values.length) {
    toast.error('未识别到有效的 10 位 ASIN')
    return
  }
  loading.value = true
  try {
    const project = await createReviewProject({ asins: values })
    projects.value.unshift(project)
    selectedProjectId.value = project.id
    run.value = undefined
    dashboard.value = undefined
    opportunities.value = []
    insights.value = []
    workflowError.value = ''
    collection.value = await startReviewCollection(project.id)
    asinInput.value = ''
    createOpen.value = false
    toast.success('任务已创建，系统将自动采集并分析评论')
    startPolling()
  } finally {
    loading.value = false
  }
}

async function startAutomaticAnalysis() {
  if (!selectedProjectId.value || analysisStarting.value || run.value) return
  analysisStarting.value = true
  try {
    run.value = await startReviewAnalysis(selectedProjectId.value)
    startPolling()
  } catch (error) {
    workflowError.value = error instanceof Error ? error.message : '无法启动评论分析'
  } finally {
    analysisStarting.value = false
  }
}

function startPolling() {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = setInterval(refreshProgress, POLL_INTERVAL_MS)
}

async function refreshProgress() {
  const projectId = selectedProjectId.value
  if (!projectId) return
  if (collection.value && isCollecting.value) {
    collection.value = await getReviewCollection(projectId, collection.value.id, true)
    if (['success', 'partial'].includes(collection.value.status) && !run.value) {
      await startAutomaticAnalysis()
    }
  }
  if (run.value && isAnalyzing.value) {
    run.value = await getAnalysisRun(projectId, run.value.id)
    if (!isAnalyzing.value) {
      await loadResults()
      await loadProjects(projectId)
      toast.success('评论洞察报告已生成')
    }
  }
  if (!isBusy.value && pollTimer) {
    clearInterval(pollTimer)
    pollTimer = undefined
  }
}

async function loadResults() {
  if (!selectedProjectId.value || !run.value || isAnalyzing.value) return
  const [summary, opportunityData, insightData] = await Promise.all([
    getReviewDashboard(selectedProjectId.value, run.value.id),
    listReviewOpportunities(selectedProjectId.value, run.value.id),
    listReviewInsights(selectedProjectId.value, run.value.id, {
      page: 0, size: 100, keyword: keyword.value || undefined,
    }),
  ])
  dashboard.value = summary
  opportunities.value = opportunityData
  insights.value = insightData.content
}

async function retryTask() {
  if (!selectedProjectId.value) return
  workflowError.value = ''
  if (run.value?.failedReviewCount) {
    run.value = await retryAnalysisFailures(selectedProjectId.value, run.value.id)
  } else if (!run.value && collection.value && ['success', 'partial'].includes(collection.value.status)) {
    await startAutomaticAnalysis()
  } else {
    collection.value = await startReviewCollection(selectedProjectId.value)
    run.value = undefined
  }
  startPolling()
}

function openProject(project: ReviewProject, target: 'report' | 'evidence') {
  selectedProjectId.value = project.id
  router.push({ name: target === 'report' ? 'ReviewReport' : 'ReviewEvidence' })
}

function openInsight(item: ReviewInsight) {
  selectedInsight.value = item
  detailOpen.value = true
}

async function openOpportunity(item: ReviewOpportunity) {
  if (!selectedProjectId.value || !run.value) return
  selectedOpportunity.value = item
  opportunityInsights.value = []
  opportunityDetailOpen.value = true
  opportunityDetailLoading.value = true
  try {
    opportunityInsights.value = await listOpportunityInsights(
      selectedProjectId.value, run.value.id, item.id,
    )
  } finally {
    opportunityDetailLoading.value = false
  }
}

onMounted(() => loadProjects())
onUnmounted(() => { if (pollTimer) clearInterval(pollTimer) })
</script>

<template>
  <div class="flex flex-col gap-6">
    <div>
      <h2 class="text-2xl font-semibold tracking-tight">{{ sectionTitle }}</h2>
      <p class="text-sm text-muted-foreground">输入 ASIN 后，系统自动完成评论采集、问题识别和中文洞察生成。</p>
    </div>

    <template v-if="props.section === 'tasks'">
      <Card>
        <CardContent class="flex flex-wrap items-center justify-between gap-5 py-6">
          <div class="flex flex-col gap-1">
            <p class="font-semibold">创建一组新的评论洞察</p>
            <p class="text-sm text-muted-foreground">一次粘贴任意数量的 ASIN，系统会自动识别、去重并完成分析。</p>
          </div>
          <Button @click="createOpen = true"><Plus data-icon="inline-start" />新建评论分析</Button>
        </CardContent>
      </Card>

      <Card v-if="selectedProject && (collection || run)">
        <CardHeader>
          <CardTitle class="text-base">{{ selectedProject.name }}</CardTitle>
          <CardDescription>{{ progressLabel }}</CardDescription>
        </CardHeader>
        <CardContent class="flex flex-col gap-3">
          <div v-if="isCollecting || analysisStarting" class="flex items-center gap-2 text-sm">
            <LoaderCircle class="animate-spin" />
            <span>{{ progressLabel }}，Bright Data 暂不提供百分比进度</span>
          </div>
          <template v-else-if="run">
            <div class="flex items-center justify-between text-sm"><span>{{ progressLabel }}</span><span>{{ progress }}%</span></div>
            <div class="h-2 overflow-hidden rounded-full bg-secondary">
              <div class="h-full bg-primary transition-all" :style="{ width: `${progress}%` }" />
            </div>
          </template>
          <p v-if="run && run.sourceReviewCount > 0" class="text-xs text-muted-foreground">
            已处理 {{ run.processedReviewCount }} / {{ run.sourceReviewCount }} 条评论
          </p>
          <p v-if="workflowError" class="text-sm text-destructive">{{ workflowError }}</p>
          <Button v-if="workflowError || run?.status === 'failed' || collection?.status === 'failed'" variant="outline" class="self-start" @click="retryTask">
            <RefreshCw data-icon="inline-start" />重新分析
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>历史任务</CardTitle><CardDescription>查看每组 ASIN 的分析状态和结果。</CardDescription></CardHeader>
        <CardContent>
          <Table>
            <TableHeader><TableRow><TableHead>任务</TableHead><TableHead>ASIN</TableHead><TableHead>状态</TableHead><TableHead /></TableRow></TableHeader>
            <TableBody>
              <TableRow v-for="project in projects" :key="project.id">
                <TableCell class="font-medium">{{ project.name }}</TableCell>
                <TableCell><div class="flex flex-wrap gap-1"><Badge v-for="product in project.products" :key="product.id" variant="outline">{{ product.asin }}</Badge></div></TableCell>
                <TableCell><Badge variant="secondary">{{ statusLabel(project.status) }}</Badge></TableCell>
                <TableCell>
                  <div class="flex justify-end gap-2">
                    <Button v-if="project.status === 'review' || project.status === 'confirmed'" size="sm" variant="outline" @click="openProject(project, 'evidence')">评论证据</Button>
                    <Button v-if="project.status === 'review' || project.status === 'confirmed'" size="sm" @click="openProject(project, 'report')">查看报告</Button>
                  </div>
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </template>

    <template v-else>
      <Card>
        <CardHeader><CardTitle class="text-base">分析任务</CardTitle><CardDescription>选择一组 ASIN 查看对应结果。</CardDescription></CardHeader>
        <CardContent>
          <Select :model-value="selectedProjectId?.toString()" @update:model-value="value => selectProject(Number(value))">
            <SelectTrigger class="max-w-lg"><SelectValue placeholder="选择分析任务" /></SelectTrigger>
            <SelectContent><SelectGroup>
              <SelectItem v-for="project in projects" :key="project.id" :value="project.id.toString()">{{ project.name }}</SelectItem>
            </SelectGroup></SelectContent>
          </Select>
        </CardContent>
      </Card>

      <Card v-if="selectedProject && isBusy">
        <CardHeader><CardTitle class="text-base">{{ progressLabel }}</CardTitle><CardDescription>报告生成后会自动展示，无需继续操作。</CardDescription></CardHeader>
        <CardContent class="flex flex-col gap-3">
          <div v-if="isCollecting || analysisStarting" class="flex items-center gap-2 text-sm text-muted-foreground">
            <LoaderCircle class="animate-spin" />
            <span>正在等待 Bright Data 完成采集</span>
          </div>
          <template v-else-if="run">
            <div class="flex items-center justify-between text-sm"><span>已处理 {{ run.processedReviewCount }} / {{ run.sourceReviewCount }} 条评论</span><span>{{ progress }}%</span></div>
            <div class="h-2 overflow-hidden rounded-full bg-secondary"><div class="h-full bg-primary" :style="{ width: `${progress}%` }" /></div>
          </template>
        </CardContent>
      </Card>

      <template v-if="props.section === 'report' && dashboard && run && !isAnalyzing">
        <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <Card v-for="metric in [
            ['评论总数', dashboard.reviewCount], ['发现问题', dashboard.insightCount],
            ['改进机会', dashboard.opportunityCount], ['平均评分', dashboard.averageRating ?? '-'],
          ]" :key="metric[0]">
            <CardHeader><CardDescription>{{ metric[0] }}</CardDescription><CardTitle>{{ metric[1] }}</CardTitle></CardHeader>
          </Card>
        </div>
        <div class="grid gap-4 lg:grid-cols-3">
          <Card>
            <CardHeader><CardTitle class="text-base">严重程度</CardTitle></CardHeader>
            <CardContent>
              <ChartContainer :config="severityChartConfig" class="h-64">
                <VisSingleContainer :data="severityChartData">
                  <VisDonut
                    :value="(item: DimensionDatum) => item.count"
                    :color="(item: DimensionDatum) => item.color"
                    :arc-width="32"
                    :central-label="`${dashboard.insightCount}`"
                    central-sub-label="条问题"
                  />
                  <ChartTooltip
                    :triggers="{
                      [VisDonutSelectors.segment]: componentToString(valueChartConfig, ChartTooltipContent, { labelKey: 'label' }),
                    }"
                  />
                </VisSingleContainer>
              </ChartContainer>
              <div class="mt-3 flex flex-wrap justify-center gap-x-3 gap-y-1 text-xs">
                <span v-for="item in severityChartData" :key="item.key" class="flex items-center gap-1">
                  <span class="size-2 rounded-xs" :style="{ backgroundColor: item.color }" />
                  {{ item.label }} {{ item.count }}
                </span>
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader><CardTitle class="text-base">使用场景</CardTitle></CardHeader>
            <CardContent>
              <ChartContainer :config="scenarioChartConfig" class="h-64">
                <VisSingleContainer :data="scenarioChartData">
                  <VisDonut
                    :value="(item: DimensionDatum) => item.count"
                    :color="(item: DimensionDatum) => item.color"
                    :arc-width="32"
                    :central-label="`${dashboard.insightCount}`"
                    central-sub-label="条问题"
                  />
                  <ChartTooltip
                    :triggers="{
                      [VisDonutSelectors.segment]: componentToString(valueChartConfig, ChartTooltipContent, { labelKey: 'label' }),
                    }"
                  />
                </VisSingleContainer>
              </ChartContainer>
              <div class="mt-3 flex max-h-16 flex-wrap justify-center gap-x-3 gap-y-1 overflow-y-auto text-xs">
                <span v-for="item in scenarioChartData" :key="item.key" class="flex items-center gap-1">
                  <span class="size-2 rounded-xs" :style="{ backgroundColor: item.color }" />
                  {{ item.label }} {{ item.count }}
                </span>
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader><CardTitle class="text-base">产品模块</CardTitle></CardHeader>
            <CardContent>
              <ChartContainer :config="moduleChartConfig" class="h-72">
                <VisXYContainer :data="moduleChartData" y-direction="south">
                  <VisGroupedBar
                    :x="(_item: DimensionDatum, index: number) => index"
                    :y="(item: DimensionDatum) => item.count"
                    color="var(--color-count)"
                    orientation="horizontal"
                    :rounded-corners="4"
                  />
                  <VisXYLabels
                    :x="(item: DimensionDatum) => item.count + moduleChartLabelOffset"
                    :y="(_item: DimensionDatum, index: number) => index"
                    :label="(item: DimensionDatum) => item.count.toString()"
                    color="var(--foreground)"
                    :label-font-size="11"
                    :clustering="false"
                  />
                  <VisAxis
                    type="y"
                    :tick-values="moduleChartData.map((_item, index) => index)"
                    :tick-format="(index: number) => moduleChartData[index]?.label ?? ''"
                    :tick-line="false"
                    :domain-line="false"
                    :grid-line="false"
                  />
                  <VisAxis type="x" :tick-line="false" :domain-line="false" :grid-line="true" />
                  <ChartTooltip />
                  <ChartCrosshair
                    :template="componentToString(valueChartConfig, ChartTooltipContent, { labelKey: 'label' })"
                    color="var(--color-count)"
                  />
                </VisXYContainer>
              </ChartContainer>
            </CardContent>
          </Card>
        </div>
        <Card>
          <CardHeader><CardTitle>优先改进机会</CardTitle><CardDescription>由问题频率、严重程度、退货风险和购买影响综合排序，最终取舍由用户判断。</CardDescription></CardHeader>
          <CardContent>
            <Table>
              <TableHeader><TableRow><TableHead>机会</TableHead><TableHead>模块 / 场景</TableHead><TableHead>证据</TableHead><TableHead>优先级</TableHead><TableHead class="text-right">操作</TableHead></TableRow></TableHeader>
              <TableBody>
                <TableRow v-for="item in opportunities" :key="item.id">
                  <TableCell><p class="font-medium">{{ item.title }}</p><p class="max-w-xl text-xs text-muted-foreground">{{ item.recommendedAction }}</p></TableCell>
                  <TableCell><Badge variant="outline">{{ displayLabel(item.productModule) }}</Badge><p class="mt-1 text-xs">{{ displayLabel(item.usageScenario) }}</p></TableCell>
                  <TableCell>{{ item.insightCount }} 条评论</TableCell>
                  <TableCell class="font-semibold">{{ item.priorityScore }}</TableCell>
                  <TableCell class="text-right"><Button size="sm" variant="outline" @click="openOpportunity(item)">查看</Button></TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </template>

      <Card v-if="props.section === 'evidence' && run && !isAnalyzing">
        <CardHeader><CardTitle>评论原文与洞察</CardTitle><CardDescription>分析结论使用中文，评论原文和证据保持 Amazon 原始语言。</CardDescription></CardHeader>
        <CardContent class="flex flex-col gap-4 [&_[data-slot=table-container]]:overflow-x-hidden">
          <div class="flex flex-wrap gap-2">
            <Input v-model="keyword" class="min-w-64 flex-1" placeholder="搜索用户问题、改进动作或评论原文" @keyup.enter="loadResults" />
            <Select v-model="ratingSort">
              <SelectTrigger class="w-40"><SelectValue /></SelectTrigger>
              <SelectContent><SelectGroup>
                <SelectItem value="asc">评分从低到高</SelectItem>
                <SelectItem value="desc">评分从高到低</SelectItem>
              </SelectGroup></SelectContent>
            </Select>
            <Button variant="outline" @click="loadResults"><Search data-icon="inline-start" />搜索</Button>
          </div>
          <Table class="table-fixed">
            <TableHeader><TableRow><TableHead class="w-[12%]">ASIN / 评分</TableHead><TableHead class="w-[20%]">用户问题</TableHead><TableHead class="w-[14%]">分类</TableHead><TableHead class="w-[22%]">评论证据</TableHead><TableHead class="w-[22%]">改进建议</TableHead><TableHead class="w-[10%]">操作</TableHead></TableRow></TableHeader>
            <TableBody>
              <TableRow v-for="item in sortedInsights" :key="item.id">
                <TableCell class="overflow-hidden">{{ item.asin }}<p class="text-xs text-muted-foreground">{{ item.rating ?? '-' }} 星</p></TableCell>
                <TableCell class="overflow-hidden">
                  <TooltipProvider><Tooltip><TooltipTrigger as-child><p class="line-clamp-2 cursor-help break-words">{{ truncate(item.userProblem, 36) }}</p></TooltipTrigger><TooltipContent class="max-w-md whitespace-normal">{{ item.userProblem }}</TooltipContent></Tooltip></TooltipProvider>
                </TableCell>
                <TableCell class="overflow-hidden"><Badge :variant="item.severity === 'critical' ? 'destructive' : 'secondary'">{{ displayLabel(item.severity) }}</Badge><p class="mt-1 line-clamp-2 break-words text-xs">{{ displayLabel(item.productModule) }}</p></TableCell>
                <TableCell class="overflow-hidden">
                  <TooltipProvider><Tooltip><TooltipTrigger as-child><p class="line-clamp-2 cursor-help break-words">“{{ truncate(item.evidenceQuote, 60) }}”</p></TooltipTrigger><TooltipContent class="max-w-lg whitespace-normal">{{ item.reviewText }}</TooltipContent></Tooltip></TooltipProvider>
                </TableCell>
                <TableCell class="overflow-hidden">
                  <TooltipProvider><Tooltip><TooltipTrigger as-child><p class="line-clamp-2 cursor-help break-words">{{ truncate(item.improvementAction, 48) }}</p></TooltipTrigger><TooltipContent class="max-w-md whitespace-normal">{{ item.improvementAction }}</TooltipContent></Tooltip></TooltipProvider>
                </TableCell>
                <TableCell class="whitespace-nowrap"><Button size="sm" variant="outline" @click="openInsight(item)">查看</Button></TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Card v-if="selectedProject && !isBusy && (!run || run.status === 'failed' || collection?.status === 'failed')">
        <CardContent class="flex flex-col items-center gap-3 py-10 text-center">
          <BarChart3 class="size-10 text-muted-foreground" />
          <p class="font-medium">本次分析尚未生成结果</p>
          <Button variant="outline" @click="retryTask"><RefreshCw data-icon="inline-start" />重新分析</Button>
        </CardContent>
      </Card>
    </template>

    <Dialog v-model:open="createOpen">
      <DialogContent class="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>新建评论分析</DialogTitle>
          <DialogDescription>可粘贴 ASIN、Amazon 商品链接或包含 ASIN 的文本，系统会自动识别并去重。</DialogDescription>
        </DialogHeader>
        <form id="review-analysis-form" class="flex flex-col gap-4" @submit.prevent="createTask">
          <div class="flex flex-col gap-2">
            <Label for="asin-input">ASIN 列表</Label>
            <Textarea id="asin-input" v-model="asinInput" rows="8" placeholder="例如：&#10;B0F59PZN7B&#10;https://www.amazon.com/dp/B09KBH8R7F&#10;B0GZMTHX44" />
          </div>
          <div class="flex flex-wrap items-center gap-2">
            <Badge variant="secondary">已识别 {{ parsedAsins.length }} 个</Badge>
            <Badge v-for="asin in parsedAsins.slice(0, 8)" :key="asin" variant="outline">{{ asin }}</Badge>
            <span v-if="parsedAsins.length > 8" class="text-xs text-muted-foreground">另有 {{ parsedAsins.length - 8 }} 个</span>
          </div>
        </form>
        <DialogFooter>
          <Button variant="outline" @click="createOpen = false">取消</Button>
          <Button type="submit" form="review-analysis-form" :disabled="loading || !parsedAsins.length">
            <LoaderCircle v-if="loading" class="animate-spin" data-icon="inline-start" />
            <Sparkles v-else data-icon="inline-start" />
            开始分析
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="detailOpen">
      <DialogContent v-if="selectedInsight" class="sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>评论洞察详情</DialogTitle>
          <DialogDescription>{{ selectedInsight.asin }} · {{ selectedInsight.rating ?? '-' }} 星</DialogDescription>
        </DialogHeader>
        <div class="grid max-h-[70vh] gap-5 overflow-y-auto md:grid-cols-2">
          <div class="flex flex-col gap-4">
            <div><p class="text-xs text-muted-foreground">用户问题</p><p class="mt-1 font-medium">{{ selectedInsight.userProblem }}</p></div>
            <div class="flex flex-wrap gap-2"><Badge :variant="selectedInsight.severity === 'critical' ? 'destructive' : 'secondary'">{{ displayLabel(selectedInsight.severity) }}</Badge><Badge variant="outline">{{ displayLabel(selectedInsight.productModule) }}</Badge><Badge variant="outline">{{ displayLabel(selectedInsight.usageScenario) }}</Badge></div>
            <div><p class="text-xs text-muted-foreground">改进建议</p><p class="mt-1 leading-6">{{ selectedInsight.improvementAction }}</p></div>
            <div class="grid grid-cols-3 gap-3 text-sm">
              <div><p class="text-xs text-muted-foreground">退货风险</p><p class="mt-1 font-medium">{{ selectedInsight.returnRisk }} / 5</p></div>
              <div><p class="text-xs text-muted-foreground">购买影响</p><p class="mt-1 font-medium">{{ selectedInsight.conversionRisk }} / 5</p></div>
              <div><p class="text-xs text-muted-foreground">置信度</p><p class="mt-1 font-medium">{{ Math.round(selectedInsight.confidence * 100) }}%</p></div>
            </div>
          </div>
          <div class="flex flex-col gap-4">
            <div><p class="text-xs text-muted-foreground">原文证据</p><q class="mt-1 block font-medium leading-6">{{ selectedInsight.evidenceQuote }}</q></div>
            <div><p class="text-xs text-muted-foreground">完整评论原文</p><p class="mt-1 whitespace-pre-wrap rounded-md bg-muted p-4 text-sm leading-6">{{ selectedInsight.reviewText }}</p></div>
          </div>
        </div>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="opportunityDetailOpen">
      <DialogContent v-if="selectedOpportunity" class="sm:max-w-4xl">
        <DialogHeader>
          <DialogTitle>{{ selectedOpportunity.title }}</DialogTitle>
          <DialogDescription>
            {{ displayLabel(selectedOpportunity.productModule) }} ·
            {{ displayLabel(selectedOpportunity.usageScenario) }} ·
            {{ selectedOpportunity.insightCount }} 条评论证据
          </DialogDescription>
        </DialogHeader>
        <div class="flex max-h-[72vh] flex-col gap-5 overflow-y-auto pr-2">
          <div class="grid gap-4 md:grid-cols-2">
            <div><p class="text-xs text-muted-foreground">改进建议</p><p class="mt-1 leading-6">{{ selectedOpportunity.recommendedAction }}</p></div>
            <div><p class="text-xs text-muted-foreground">商业价值与优先级</p><p class="mt-1 leading-6">{{ selectedOpportunity.rationale || '暂无补充说明' }}</p><p class="mt-2 font-semibold">优先级 {{ selectedOpportunity.priorityScore }}</p></div>
          </div>
          <div>
            <p class="mb-3 font-medium">评论证据原文</p>
            <div v-if="opportunityDetailLoading" class="flex items-center gap-2 text-sm text-muted-foreground">
              <LoaderCircle class="animate-spin" />正在加载评论证据
            </div>
            <div v-else class="flex flex-col gap-3">
              <Card v-for="evidence in opportunityInsights" :key="evidence.id" size="sm">
                <CardHeader>
                  <CardTitle class="text-sm">{{ evidence.asin }} · {{ evidence.rating ?? '-' }} 星</CardTitle>
                  <CardDescription>{{ evidence.userProblem }}</CardDescription>
                </CardHeader>
                <CardContent class="flex flex-col gap-3">
                  <q class="font-medium leading-6">{{ evidence.evidenceQuote }}</q>
                  <p class="whitespace-pre-wrap rounded-md bg-muted p-3 text-sm leading-6">{{ evidence.reviewText }}</p>
                </CardContent>
              </Card>
              <p v-if="!opportunityInsights.length" class="text-sm text-muted-foreground">暂无可展示的评论证据。</p>
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  </div>
</template>
