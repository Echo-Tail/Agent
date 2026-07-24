<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, shallowRef, ref, watch } from 'vue'
import { VueFlow, type Edge, type EdgeChange, type Node, type NodeChange, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import { ArrowLeft, Check, Focus, Loader2, Palette, Redo2, Save, Undo2 } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { getImageModelsApi } from '@/api/model'
import { createSuperResolutionJob, getImageJob, getImageJobResults, listSuperResolutionJobs, submitImageToImageJob, submitTextImageJob, type SuperResolutionJob } from '@/api/image'
import { imageJobPollDelay, isImageJobTerminal } from '@/utils/imageJobRuntime'
import type { AiModel } from '@/types/api'
import { toast } from 'vue-sonner'
import GenerationNode from './canvas/GenerationNode.vue'
import ResultNode from './canvas/ResultNode.vue'
import MaskEditorDialog from './canvas/MaskEditorDialog.vue'
import type { ImageWorkflowNodeData } from '@/types/image-workflow'
import { getImageCanvasWorkspace, saveImageCanvas, uploadImageCanvasAsset } from '@/api/image-workflow'
import { normalizeWorkflowImageUrl, validateWorkflowUpscale } from '@/utils/imageWorkflow'
import { importFromRecord, listSpaces, type AssetSpace } from '@/api/assets'
import { createPrompt, setCoverRef } from '@/api/prompts'

const router = useRouter()
const route = useRoute()
const sessionId = Number(route.params.sessionId)
const { findNode, getViewport, setViewport, zoomIn, zoomOut } = useVueFlow()
const workspaceLoading = ref(true)
const canvasRevision = ref(0)
const sessionTitle = ref('图像创作画布')
const saveStatus = ref<'saved' | 'dirty' | 'saving' | 'error'>('saved')
let saveTimer: ReturnType<typeof setTimeout> | undefined
let saveInFlight = false
let saveAgain = false
let dirtySince = 0
const AUTO_SAVE_DEBOUNCE_MS = 5_000
const AUTO_SAVE_MAX_WAIT_MS = 30_000
const backgroundOptions = [
  { name: '雾白', color: '#f8fafc', pattern: '#cbd5e1' },
  { name: '暖灰', color: '#fafaf9', pattern: '#d6d3d1' },
  { name: '浅灰', color: '#f9fafb', pattern: '#d1d5db' },
  { name: '浅红', color: '#fef2f2', pattern: '#fecaca' },
  { name: '浅橙', color: '#fff7ed', pattern: '#fed7aa' },
  { name: '浅琥珀', color: '#fffbeb', pattern: '#fde68a' },
  { name: '浅绿', color: '#f0fdf4', pattern: '#bbf7d0' },
  { name: '浅翠', color: '#ecfdf5', pattern: '#a7f3d0' },
  { name: '浅青', color: '#ecfeff', pattern: '#a5f3fc' },
  { name: '浅蓝', color: '#eff6ff', pattern: '#bfdbfe' },
  { name: '浅靛', color: '#eef2ff', pattern: '#c7d2fe' },
  { name: '浅紫', color: '#f5f3ff', pattern: '#ddd6fe' },
  { name: '浅粉', color: '#fff1f2', pattern: '#fecdd3' },
] as const
const canvasBackgroundColor = ref<string>(backgroundOptions[0].color)
const selectedBackground = computed(() =>
  backgroundOptions.find(option => option.color === canvasBackgroundColor.value) ?? backgroundOptions[0],
)
const textModels = ref<AiModel[]>([])
const imageModels = ref<AiModel[]>([])
const modelLoading = ref(true)
const busyNodeIds = ref(new Set<string>())
const upscalingNodeIds = ref(new Set<string>())
type GenerationPayload = {
  prompt: string
  modelId: number
  images: File[]
  remoteImages: Array<{ url: string; name: string; recordId?: number }>
  maskImage?: { url: string; name: string; assetId?: number }
  size: string
  quality: string
  imageCount: number
}
const generationInputs = new Map<string, GenerationPayload>()
const maskEditorTarget = ref<{ kind: 'result' | 'generation'; nodeId: string; imageUrl: string }>()
const assetDialogOpen = ref(false)
const assetTargetNodeId = ref<string>()
const assetSpaces = ref<AssetSpace[]>([])
const selectedAssetSpaceId = ref<number>()
const savingAsset = ref(false)
const promptDialogOpen = ref(false)
const promptTargetNodeId = ref<string>()
const promptCategory = ref('车载主机')
const promptTags = ref('')
const savingPrompt = ref(false)
const promptCategories = ['车载主机', '扬声器', '低音炮', '功放', 'DSP', '显示屏', '摄像头', '线材配件', '安装支架']
const nodes = shallowRef<Node<ImageWorkflowNodeData>[]>([
  {
    id: 'generation-root',
    type: 'generation',
    position: { x: 120, y: 160 },
    data: {
      kind: 'generation',
      title: '新的创作',
      status: 'draft',
      imageCount: 1,
    },
  },
])
const edges = shallowRef<Edge[]>([])
type CanvasHistoryState = {
  nodes: Node<ImageWorkflowNodeData>[]
  edges: Edge[]
  backgroundColor: string
}
const undoStack = shallowRef<CanvasHistoryState[]>([])
const redoStack = shallowRef<CanvasHistoryState[]>([])
const canUndo = computed(() => undoStack.value.length > 0)
const canRedo = computed(() => redoStack.value.length > 0)
let currentHistoryState: CanvasHistoryState | undefined
let historyReady = false
let applyingHistory = false
let historyTimer: ReturnType<typeof setTimeout> | undefined
let lastHistorySignature = ''

function cloneNodeData(data: ImageWorkflowNodeData | undefined): ImageWorkflowNodeData | undefined {
  return data === undefined ? undefined : JSON.parse(JSON.stringify(data)) as ImageWorkflowNodeData
}

function captureHistory(): CanvasHistoryState {
  return {
    nodes: nodes.value.map(node => ({
      id: node.id,
      type: node.type,
      position: { ...node.position },
      data: cloneNodeData(node.data),
    })),
    edges: edges.value.map(edge => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
    })),
    backgroundColor: canvasBackgroundColor.value,
  }
}

function historySignature(state: CanvasHistoryState) {
  return JSON.stringify(state)
}

function initializeHistory() {
  const initial = captureHistory()
  currentHistoryState = initial
  undoStack.value = []
  redoStack.value = []
  lastHistorySignature = historySignature(initial)
  historyReady = true
}

function commitHistory() {
  if (!historyReady || applyingHistory) return
  const state = captureHistory()
  const signature = historySignature(state)
  if (signature === lastHistorySignature) return
  if (currentHistoryState) {
    undoStack.value = [...undoStack.value, currentHistoryState].slice(-50)
  }
  currentHistoryState = state
  redoStack.value = []
  lastHistorySignature = signature
}

function scheduleHistory() {
  if (!historyReady || applyingHistory) return
  if (historyTimer) clearTimeout(historyTimer)
  historyTimer = setTimeout(commitHistory, 180)
}

function applyHistory(state: CanvasHistoryState) {
  applyingHistory = true
  nodes.value = state.nodes.map(node => ({
    ...node,
    position: { ...node.position },
    data: cloneNodeData(node.data),
  }))
  edges.value = state.edges.map(edge => ({ ...edge }))
  canvasBackgroundColor.value = state.backgroundColor
  lastHistorySignature = historySignature(state)
  scheduleSave()
  void nextTick().then(() => {
    applyingHistory = false
  })
}

function undoCanvas() {
  if (historyTimer) {
    clearTimeout(historyTimer)
    historyTimer = undefined
    commitHistory()
  }
  if (!undoStack.value.length || !currentHistoryState) return
  const current = currentHistoryState
  const previous = undoStack.value.at(-1)!
  undoStack.value = undoStack.value.slice(0, -1)
  redoStack.value = [current, ...redoStack.value]
  currentHistoryState = previous
  applyHistory(previous)
}

function redoCanvas() {
  if (!redoStack.value.length || !currentHistoryState) return
  const next = redoStack.value[0]
  redoStack.value = redoStack.value.slice(1)
  undoStack.value = [...undoStack.value, currentHistoryState].slice(-50)
  currentHistoryState = next
  applyHistory(next)
}

function selectCanvasBackground(color: string) {
  if (canvasBackgroundColor.value === color) return
  canvasBackgroundColor.value = color
  scheduleHistory()
  scheduleSave()
}

function handleNodeChanges(changes: NodeChange[]) {
  scheduleSave()
  if (changes.some(change => change.type === 'position' || change.type === 'add' || change.type === 'remove')) {
    void nextTick().then(scheduleHistory)
  }
}

function handleEdgeChanges(changes: EdgeChange[]) {
  scheduleSave()
  if (changes.some(change => change.type === 'add' || change.type === 'remove')) {
    void nextTick().then(scheduleHistory)
  }
}

function handleZoomShortcut(event: KeyboardEvent) {
  if (!event.ctrlKey || event.altKey || event.metaKey) return
  const target = event.target as HTMLElement | null
  if (target?.matches('input, textarea, select, [contenteditable="true"]')) return
  const key = event.key.toLowerCase()
  if (key === 'z' && !event.shiftKey) {
    event.preventDefault()
    undoCanvas()
  } else if (key === 'y' || (key === 'z' && event.shiftKey)) {
    event.preventDefault()
    redoCanvas()
  } else if (event.key === '+' || event.key === '=') {
    event.preventDefault()
    void zoomIn({ duration: 120 })
  } else if (event.key === '-' || event.key === '_') {
    event.preventDefault()
    void zoomOut({ duration: 120 })
  }
}

onMounted(async () => {
  window.addEventListener('keydown', handleZoomShortcut)
  try {
    const [text, image, workspace] = await Promise.all([
      getImageModelsApi('TEXT_TO_IMAGE'),
      getImageModelsApi('IMAGE_TO_IMAGE'),
      getImageCanvasWorkspace(sessionId),
    ])
    textModels.value = text
    imageModels.value = image
    sessionTitle.value = workspace.session.title
    if (workspace.canvas?.snapshot) {
      restoreSnapshot(workspace.canvas.snapshot)
      canvasRevision.value = workspace.canvas.revision
    }
    await nextTick()
    const viewport = workspace.canvas?.snapshot.viewport as { x?: number; y?: number; zoom?: number } | undefined
    if (viewport && typeof viewport.x === 'number' && typeof viewport.y === 'number' && typeof viewport.zoom === 'number') {
      await setViewport({ x: viewport.x, y: viewport.y, zoom: viewport.zoom })
    }
    restoreRuntimeInputs()
    resumeActiveGenerationJobs()
    resumeActiveUpscaleJobs()
  } catch {
    toast.error('画布工作区加载失败')
  } finally {
    modelLoading.value = false
    workspaceLoading.value = false
    initializeHistory()
  }
})

watch([nodes, edges], () => {
  scheduleSave()
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleZoomShortcut)
  if (saveTimer) clearTimeout(saveTimer)
  if (historyTimer) clearTimeout(historyTimer)
  if (saveStatus.value === 'dirty') void persistCanvas()
})

function restoreSnapshot(snapshot: Record<string, unknown>) {
  if (typeof snapshot.backgroundColor === 'string'
    && backgroundOptions.some(option => option.color === snapshot.backgroundColor)) {
    canvasBackgroundColor.value = snapshot.backgroundColor
  }
  if (Array.isArray(snapshot.nodes)) {
    nodes.value = snapshot.nodes.filter(isStoredNode).map(node => ({
      id: node.id,
      type: node.type,
      position: node.position,
      data: node.data,
    }))
  }
  if (Array.isArray(snapshot.edges)) {
    edges.value = snapshot.edges.filter(isStoredEdge).map(edge => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
    }))
  }
}

function isStoredNode(value: unknown): value is Node<ImageWorkflowNodeData> {
  if (!value || typeof value !== 'object') return false
  const node = value as Partial<Node<ImageWorkflowNodeData>>
  return typeof node.id === 'string'
    && (node.type === 'generation' || node.type === 'result')
    && Boolean(node.position)
    && Boolean(node.data)
}

function isStoredEdge(value: unknown): value is Edge {
  if (!value || typeof value !== 'object') return false
  const edge = value as Partial<Edge>
  return typeof edge.id === 'string' && typeof edge.source === 'string' && typeof edge.target === 'string'
}

function restoreRuntimeInputs() {
  for (const node of nodes.value) {
    const data = node.data as ImageWorkflowNodeData | undefined
    if (node.type !== 'generation' || !data?.modelId || !data.prompt) continue
    generationInputs.set(node.id, {
      prompt: data.prompt,
      modelId: data.modelId,
      images: [],
      remoteImages: data.referenceImages ?? [],
      maskImage: data.maskImage,
      size: data.size ?? '1024x1024',
      quality: data.quality ?? 'high',
      imageCount: data.imageCount ?? 1,
    })
  }
}

function scheduleSave() {
  if (workspaceLoading.value || !Number.isInteger(sessionId)) return
  saveStatus.value = 'dirty'
  const now = Date.now()
  if (!dirtySince) dirtySince = now
  const remainingMaxWait = Math.max(0, AUTO_SAVE_MAX_WAIT_MS - (now - dirtySince))
  const delay = Math.min(AUTO_SAVE_DEBOUNCE_MS, remainingMaxWait)
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(() => void persistCanvas(), delay)
}

async function persistCanvas() {
  if (saveInFlight) {
    saveAgain = true
    return
  }
  saveInFlight = true
  saveAgain = false
  dirtySince = 0
  saveStatus.value = 'saving'
  try {
    const viewport = getViewport()
    const saved = await saveImageCanvas(sessionId, {
      revision: canvasRevision.value,
      schemaVersion: 1,
      snapshot: {
        version: 1,
        backgroundColor: canvasBackgroundColor.value,
        nodes: nodes.value.map(node => ({
          id: node.id,
          type: node.type,
          position: node.position,
          data: node.data,
        })),
        edges: edges.value.map(edge => ({
          id: edge.id,
          source: edge.source,
          target: edge.target,
        })),
        viewport,
      },
    })
    canvasRevision.value = saved.revision
    saveStatus.value = 'saved'
  } catch {
    saveStatus.value = 'error'
  } finally {
    saveInFlight = false
    if (saveAgain) {
      saveStatus.value = 'dirty'
      scheduleSave()
    }
  }
}

function updateNodeData(nodeId: string, patch: Partial<ImageWorkflowNodeData>) {
  nodes.value = nodes.value.map(node => node.id === nodeId
    ? { ...node, data: { ...(node.data as ImageWorkflowNodeData), ...patch } as ImageWorkflowNodeData }
    : node)
}

function updateGenerationDraft(nodeId: string, patch: Partial<ImageWorkflowNodeData>) {
  updateNodeData(nodeId, patch)
  const node = nodes.value.find(item => item.id === nodeId)
  const data = node?.data as ImageWorkflowNodeData | undefined
  if (!data?.modelId || !data.prompt) return
  generationInputs.set(nodeId, {
    prompt: data.prompt,
    modelId: data.modelId,
    images: [],
    remoteImages: data.referenceImages ?? [],
    maskImage: data.maskImage,
    size: data.size ?? '1024x1024',
    quality: data.quality ?? 'high',
    imageCount: data.imageCount ?? 1,
  })
}

async function runGeneration(nodeId: string, payload: GenerationPayload) {
  if (busyNodeIds.value.has(nodeId)) return
  generationInputs.set(nodeId, payload)
  busyNodeIds.value = new Set(busyNodeIds.value).add(nodeId)
  updateNodeData(nodeId, {
    prompt: payload.prompt,
    modelId: payload.modelId,
    size: payload.size,
    quality: payload.quality,
    imageCount: payload.imageCount,
    referenceImages: payload.remoteImages,
    maskImage: payload.maskImage,
    status: 'pending',
    statusText: '正在提交',
  })
  try {
    const request = {
      modelId: payload.modelId,
      prompt: payload.prompt,
      targetCount: payload.imageCount,
      optionsJson: JSON.stringify({ size: payload.size, quality: payload.quality }),
    }
    const remoteFiles = await Promise.all(payload.remoteImages.map(reference => imageUrlToFile(reference.url, reference.name)))
    const referenceFiles = [...remoteFiles, ...payload.images]
    const mask = payload.maskImage
      ? await imageUrlToFile(payload.maskImage.url, payload.maskImage.name)
      : undefined
    if (mask && !referenceFiles.length) {
      throw new Error('Mask 必须配合参考图使用')
    }
    let job = referenceFiles.length
      ? await submitImageToImageJob({ ...request, images: referenceFiles, mask })
      : await submitTextImageJob(request)
    updateNodeData(nodeId, { jobId: job.id, status: 'running', statusText: '生成中' })
    await monitorGenerationJob(nodeId, job)
  } catch {
    updateNodeData(nodeId, { status: 'failed', statusText: '任务提交失败' })
    toast.error('图像生成任务提交失败')
  } finally {
    const next = new Set(busyNodeIds.value)
    next.delete(nodeId)
    busyNodeIds.value = next
  }
}

async function monitorGenerationJob(nodeId: string, initialJob: Awaited<ReturnType<typeof getImageJob>>) {
  let job = initialJob
  let attempts = 0
  while (!isImageJobTerminal(job.status)) {
    await new Promise(resolve => window.setTimeout(resolve, imageJobPollDelay(attempts++)))
    job = await getImageJob(job.id)
    updateNodeData(nodeId, { statusText: phaseLabel(job.executionPhase) })
  }

  if (job.status === 'SUCCEEDED' || job.status === 'PARTIALLY_SUCCEEDED') {
    updateNodeData(nodeId, { status: 'succeeded', statusText: job.status === 'SUCCEEDED' ? '生成完成' : '部分完成' })
    const records = await getImageJobResults(job.id)
    const startedAt = new Date(job.startedAt ?? job.createdAt).getTime()
    const completedAt = new Date(job.completedAt ?? new Date().toISOString()).getTime()
    const elapsedSeconds = Math.max(1, Math.round((completedAt - startedAt) / 1000))
    appendResultNodes(nodeId, records
      .filter(record => record.status !== 'FAILED' && Boolean(record.resultPath))
      .map(record => ({
        recordId: record.id,
        imageUrl: normalizeImageUrl(record.resultPath),
        width: record.width,
        height: record.height,
        elapsedSeconds,
      })))
    toast.success('生成任务已完成')
  } else {
    updateNodeData(nodeId, { status: 'failed', statusText: job.safeErrorMessage || '生成失败' })
    toast.error(job.safeErrorMessage || '生成失败')
  }
}

function resumeActiveGenerationJobs() {
  for (const node of nodes.value) {
    const data = node.data as ImageWorkflowNodeData | undefined
    if (node.type !== 'generation' || !data?.jobId || !['pending', 'running'].includes(data.status ?? '')) continue
    busyNodeIds.value = new Set(busyNodeIds.value).add(node.id)
    void getImageJob(data.jobId)
      .then(job => monitorGenerationJob(node.id, job))
      .catch(() => updateNodeData(node.id, { status: 'failed', statusText: '任务恢复失败' }))
      .finally(() => {
        const next = new Set(busyNodeIds.value)
        next.delete(node.id)
        busyNodeIds.value = next
      })
  }
}

function nodeWidth(node: Node<ImageWorkflowNodeData>): number {
  const measuredWidth = findNode(node.id)?.dimensions.width
  return measuredWidth || (typeof node.width === 'number' ? node.width : (node.type === 'generation' ? 560 : 280))
}

function positionToRight(node: Node<ImageWorkflowNodeData>, gap = 80) {
  return node.position.x + nodeWidth(node) + gap
}

function nodeHeight(node: Node<ImageWorkflowNodeData>): number {
  return findNode(node.id)?.dimensions.height || (node.type === 'generation' ? 560 : 540)
}

function availablePositionToRight(
  source: Node<ImageWorkflowNodeData>,
  targetType: 'generation' | 'result',
  occupiedNodes: Node<ImageWorkflowNodeData>[] = nodes.value,
) {
  const baseX = positionToRight(source)
  const width = targetType === 'generation' ? 560 : 280
  const height = targetType === 'generation' ? 560 : 540
  const horizontalGap = 80
  const verticalGap = 40

  for (let column = 0; column < 8; column++) {
    const x = baseX + column * (width + horizontalGap)
    for (let row = 0; row < 41; row++) {
      const offset = row === 0 ? 0 : Math.ceil(row / 2) * (row % 2 ? 1 : -1)
      const y = source.position.y + offset * (height + verticalGap)
      const overlaps = occupiedNodes.some(node => {
        const margin = 24
        return x < node.position.x + nodeWidth(node) + margin
          && x + width + margin > node.position.x
          && y < node.position.y + nodeHeight(node) + margin
          && y + height + margin > node.position.y
      })
      if (!overlaps) return { x, y }
    }
  }
  return { x: baseX, y: source.position.y + (height + verticalGap) * occupiedNodes.length }
}

function deleteCanvasNode(nodeId: string) {
  if (nodeId === 'generation-root') return
  const beforeDelete = captureHistory()
  nodes.value = nodes.value.filter(node => node.id !== nodeId)
  edges.value = edges.value.filter(edge => edge.source !== nodeId && edge.target !== nodeId)
  busyNodeIds.value = new Set([...busyNodeIds.value].filter(id => id !== nodeId))
  upscalingNodeIds.value = new Set([...upscalingNodeIds.value].filter(id => id !== nodeId))
  const afterDelete = captureHistory()

  // Deletion is a command boundary: record its inverse synchronously and directly.
  // It must not depend on autosave, Vue Flow change-event timing, or history debounce.
  if (historyTimer) {
    clearTimeout(historyTimer)
    historyTimer = undefined
  }
  undoStack.value = [...undoStack.value, beforeDelete].slice(-50)
  redoStack.value = []
  currentHistoryState = afterDelete
  lastHistorySignature = historySignature(afterDelete)
  historyReady = true
}

function appendResultNodes(sourceNodeId: string, results: Array<{
  recordId: number
  imageUrl: string
  width?: number | null
  height?: number | null
  elapsedSeconds?: number
}>) {
  const source = nodes.value.find(node => node.id === sourceNodeId)
  if (!source) return
  const existingRecordIds = new Set(nodes.value.map(node => (node.data as ImageWorkflowNodeData | undefined)?.recordId).filter(Boolean))
  const newResults = results.filter(result => !existingRecordIds.has(result.recordId))
  if (!newResults.length) return
  const existingResults = nodes.value.filter(node => node.data?.parentNodeId === sourceNodeId && node.type === 'result')
  const existingCount = existingResults.length
  const occupied = [...nodes.value]
  const additions: Node<ImageWorkflowNodeData>[] = []
  newResults.forEach((result, index) => {
    const node: Node<ImageWorkflowNodeData> = {
      id: `result-${result.recordId}-${crypto.randomUUID()}`,
      type: 'result',
      position: availablePositionToRight(source, 'result', occupied),
      data: {
        kind: 'result',
        title: `生成结果 ${existingCount + index + 1}`,
        status: 'succeeded',
        imageUrl: result.imageUrl,
        recordId: result.recordId,
        width: result.width,
        height: result.height,
        elapsedSeconds: result.elapsedSeconds,
        parentNodeId: sourceNodeId,
      },
    }
    additions.push(node)
    occupied.push(node)
  })
  nodes.value = [...nodes.value, ...additions]
  edges.value = [
    ...edges.value,
    ...additions.map(node => ({
      id: `edge-${sourceNodeId}-${node.id}`,
      source: sourceNodeId,
      target: node.id,
    })),
  ]
  scheduleHistory()
}

function resultNodeData(nodeId: string) {
  return nodes.value.find(node => node.id === nodeId)?.data as ImageWorkflowNodeData | undefined
}

function resultPrompt(nodeId: string) {
  const result = resultNodeData(nodeId)
  if (!result?.parentNodeId) return ''
  return (nodes.value.find(node => node.id === result.parentNodeId)?.data as ImageWorkflowNodeData | undefined)?.prompt ?? ''
}

async function copyResultPrompt(nodeId: string) {
  const prompt = resultPrompt(nodeId)
  if (!prompt) {
    toast.error('未找到该图片的生成提示词')
    return
  }
  try {
    await navigator.clipboard.writeText(prompt)
    toast.success('提示词已复制')
  } catch {
    toast.error('复制提示词失败')
  }
}

function downloadResult(nodeId: string) {
  const data = resultNodeData(nodeId)
  if (!data?.imageUrl) return
  const link = document.createElement('a')
  link.href = data.imageUrl
  link.download = data.imageUrl.split('/').pop()?.split('?')[0] || `image-${data.recordId ?? Date.now()}.png`
  document.body.appendChild(link)
  link.click()
  link.remove()
}

async function openAssetDialog(nodeId: string) {
  const data = resultNodeData(nodeId)
  if (!data?.recordId) {
    toast.error('该图片缺少生成记录，无法保存到素材库')
    return
  }
  assetTargetNodeId.value = nodeId
  selectedAssetSpaceId.value = undefined
  assetDialogOpen.value = true
  try {
    assetSpaces.value = await listSpaces()
  } catch {
    assetSpaces.value = []
  }
}

async function saveResultToAsset() {
  const data = assetTargetNodeId.value ? resultNodeData(assetTargetNodeId.value) : undefined
  if (!data?.recordId || savingAsset.value) return
  savingAsset.value = true
  try {
    await importFromRecord(data.recordId, selectedAssetSpaceId.value)
    assetDialogOpen.value = false
    toast.success('已保存到素材库')
  } finally {
    savingAsset.value = false
  }
}

function openPromptDialog(nodeId: string) {
  if (!resultPrompt(nodeId)) {
    toast.error('未找到该图片的生成提示词')
    return
  }
  promptTargetNodeId.value = nodeId
  promptCategory.value = '车载主机'
  promptTags.value = ''
  promptDialogOpen.value = true
}

function promptCoverPath(imageUrl: string) {
  return imageUrl.replace(/^https?:\/\/[^/]+/i, '')
    .replace(/\\/g, '/')
    .replace(/^\/?uploads\//, '')
    .replace(/^\.?\//, '')
}

async function saveResultPrompt() {
  if (!promptTargetNodeId.value || !promptCategory.value || savingPrompt.value) return
  const prompt = resultPrompt(promptTargetNodeId.value)
  const data = resultNodeData(promptTargetNodeId.value)
  if (!prompt || !data?.imageUrl) return
  savingPrompt.value = true
  try {
    const form = new FormData()
    form.append('prompt', prompt)
    form.append('category', promptCategory.value)
    if (promptTags.value.trim()) form.append('tags', promptTags.value.trim())
    const created = await createPrompt(form)
    const coverPath = promptCoverPath(data.imageUrl)
    if (created.id && coverPath) await setCoverRef(created.id, coverPath)
    promptDialogOpen.value = false
    toast.success('已上传到提示词库')
  } finally {
    savingPrompt.value = false
  }
}

async function upscaleResult(resultNodeId: string, factor: number) {
  const source = nodes.value.find(node => node.id === resultNodeId)
  const data = source?.data as ImageWorkflowNodeData | undefined
  const validationError = validateWorkflowUpscale(data, factor)
  if (validationError) {
    toast.error(validationError)
    return
  }
  if (!source || !data?.recordId || upscalingNodeIds.value.has(resultNodeId)) return
  upscalingNodeIds.value = new Set(upscalingNodeIds.value).add(resultNodeId)
  try {
    let job = await createSuperResolutionJob(data.recordId, factor, 'IMAGE_GENERATION')
    updateNodeData(resultNodeId, { upscaleJobId: job.id })
    toast.success('高清放大任务已加入队列')
    await monitorUpscaleJob(resultNodeId, job)
  } catch (error) {
    toast.error(error instanceof Error ? error.message : '高清放大失败')
  } finally {
    const next = new Set(upscalingNodeIds.value)
    next.delete(resultNodeId)
    upscalingNodeIds.value = next
  }
}

async function monitorUpscaleJob(resultNodeId: string, initialJob: SuperResolutionJob) {
  let job = initialJob
  while (job.status === 'PENDING' || job.status === 'RUNNING') {
    await new Promise(resolve => window.setTimeout(resolve, 1500))
    job = (await listSuperResolutionJobs('IMAGE_GENERATION')).find(item => item.id === job.id) ?? job
  }
  updateNodeData(resultNodeId, { upscaleJobId: undefined })
  if (job.status !== 'SUCCEEDED' || !job.resultPath || !job.resultRecordId) {
    throw new Error(job.errorMessage || '高清放大失败')
  }
  if (nodes.value.some(node => (node.data as ImageWorkflowNodeData | undefined)?.recordId === job.resultRecordId)) return
  const source = nodes.value.find(node => node.id === resultNodeId)
  if (!source) return
  const nodeId = `upscale-${job.id}-${crypto.randomUUID()}`
  nodes.value = [...nodes.value, {
    id: nodeId,
    type: 'result',
    position: availablePositionToRight(source, 'result'),
    data: {
      kind: 'result',
      title: `高清放大 ${job.upscaleFactor}×`,
      status: 'succeeded',
      imageUrl: normalizeImageUrl(job.resultPath),
      recordId: job.resultRecordId,
      width: job.width,
      height: job.height,
      upscaleFactor: job.upscaleFactor,
      parentNodeId: resultNodeId,
    },
  }]
  edges.value = [...edges.value, {
    id: `edge-${resultNodeId}-${nodeId}`,
    source: resultNodeId,
    target: nodeId,
  }]
  scheduleHistory()
  toast.success('高清放大完成')
}

function resumeActiveUpscaleJobs() {
  for (const node of nodes.value) {
    const data = node.data as ImageWorkflowNodeData | undefined
    if (node.type !== 'result' || !data?.upscaleJobId) continue
    upscalingNodeIds.value = new Set(upscalingNodeIds.value).add(node.id)
    void listSuperResolutionJobs('IMAGE_GENERATION')
      .then(jobs => {
        const job = jobs.find(item => item.id === data.upscaleJobId)
        if (!job) throw new Error('未找到高清放大任务')
        return monitorUpscaleJob(node.id, job)
      })
      .catch(error => toast.error(error instanceof Error ? error.message : '高清放大任务恢复失败'))
      .finally(() => {
        const next = new Set(upscalingNodeIds.value)
        next.delete(node.id)
        upscalingNodeIds.value = next
      })
  }
}

function continueFromResult(
  resultNodeId: string,
  title = '继续创作',
  patch: Partial<ImageWorkflowNodeData> = {},
) {
  const resultNode = nodes.value.find(node => node.id === resultNodeId)
  const data = resultNode?.data as ImageWorkflowNodeData | undefined
  if (!resultNode || !data?.imageUrl) return
  const parentInput = data.parentNodeId ? generationInputs.get(data.parentNodeId) : undefined
  const generationId = `generation-${crypto.randomUUID()}`
  nodes.value = [...nodes.value, {
    id: generationId,
    type: 'generation',
    position: availablePositionToRight(resultNode, 'generation'),
    data: {
      kind: 'generation',
      title,
      prompt: parentInput?.prompt ?? '',
      status: 'draft',
      imageCount: 1,
      parentNodeId: resultNodeId,
      referenceImages: [{
        url: data.imageUrl,
        name: `result-${data.recordId ?? 'image'}.png`,
        recordId: data.recordId,
      }],
      ...patch,
    },
  }]
  edges.value = [...edges.value, {
    id: `edge-${resultNodeId}-${generationId}`,
    source: resultNodeId,
    target: generationId,
  }]
  scheduleHistory()
  return generationId
}

function regenerateResult(resultNodeId: string) {
  const result = nodes.value.find(node => node.id === resultNodeId)
  const parentNodeId = (result?.data as ImageWorkflowNodeData | undefined)?.parentNodeId
  const payload = parentNodeId ? generationInputs.get(parentNodeId) : undefined
  if (!parentNodeId || !payload) {
    toast.error('当前创作参数已失效，请使用继续创作')
    return
  }
  void runGeneration(parentNodeId, payload)
}

function openMaskEditor(resultNodeId: string) {
  const imageUrl = resultNodeData(resultNodeId)?.imageUrl
  if (!imageUrl) return
  maskEditorTarget.value = { kind: 'result', nodeId: resultNodeId, imageUrl }
}

function openGenerationMaskEditor(nodeId: string) {
  const data = nodes.value.find(node => node.id === nodeId)?.data as ImageWorkflowNodeData | undefined
  const imageUrl = data?.referenceImages?.[0]?.url
  if (!imageUrl) {
    toast.error('请先添加参考图，Mask 会应用到第一张参考图')
    return
  }
  maskEditorTarget.value = { kind: 'generation', nodeId, imageUrl }
}

async function applyMask(file: File, mode: 'annotation' | 'mask') {
  const target = maskEditorTarget.value
  if (!target) return
  try {
    const asset = await uploadImageCanvasAsset(sessionId, file)
    const uploadedImage = { url: asset.url, name: file.name, assetId: asset.id }
    let generationId: string | undefined
    if (target.kind === 'result') {
      generationId = continueFromResult(target.nodeId, '局部修改', mode === 'mask'
        ? { maskImage: uploadedImage }
        : { referenceImages: [uploadedImage], maskImage: undefined })
    } else {
      generationId = target.nodeId
      if (mode === 'mask') {
        updateGenerationDraft(generationId, { maskImage: uploadedImage })
      } else {
        const data = nodes.value.find(node => node.id === generationId)?.data as ImageWorkflowNodeData | undefined
        const references = [...(data?.referenceImages ?? [])]
        if (references.length) references[0] = uploadedImage
        else references.push(uploadedImage)
        updateGenerationDraft(generationId, { referenceImages: references, maskImage: undefined })
      }
    }
    if (generationId) {
      scheduleHistory()
      scheduleSave()
    }
    maskEditorTarget.value = undefined
    toast.success(mode === 'mask'
      ? 'Mask 已应用，透明区域将在生成时被修改'
      : '标注已合并到参考图，请补充修改要求后生成')
  } catch {
    toast.error('编辑结果保存失败，请重试')
  }
}

async function imageUrlToFile(url: string, name: string) {
  const response = await fetch(url)
  if (!response.ok) throw new Error('参考图读取失败')
  const blob = await response.blob()
  return new File([blob], name, { type: blob.type || 'image/png' })
}

function normalizeImageUrl(url: string) {
  return normalizeWorkflowImageUrl(url)
}

function phaseLabel(phase: string | null) {
  return {
    PREPARING: '准备输入',
    SUBMITTING: '提交模型',
    POLLING: '等待模型',
    DOWNLOADING: '下载结果',
    PERSISTING: '保存结果',
  }[phase ?? ''] ?? '生成中'
}

</script>

<template>
  <div class="flex h-[calc(100vh-4rem)] min-h-[620px] flex-col overflow-hidden bg-muted/30">
    <header class="flex h-16 shrink-0 items-center justify-between border-b bg-background/95 px-5 backdrop-blur">
      <div class="flex items-center gap-3">
        <Button variant="ghost" size="icon" aria-label="返回画布会话" @click="router.push({ name: 'ImageCanvasSessions' })">
          <ArrowLeft class="h-4 w-4" />
        </Button>
        <div>
          <h1 class="text-base font-semibold">{{ sessionTitle }}</h1>
          <p class="text-xs text-muted-foreground">固定关系的生成、修改与放大工作流</p>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <DropdownMenu>
          <DropdownMenuTrigger as-child>
            <Button variant="outline" size="icon" title="调整画布背景色" aria-label="调整画布背景色">
              <Palette class="size-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" class="grid w-64 grid-cols-2 gap-1 p-2">
            <DropdownMenuLabel class="col-span-2 px-2 pb-1">画布背景色</DropdownMenuLabel>
            <DropdownMenuItem
              v-for="option in backgroundOptions"
              :key="option.color"
              class="gap-2"
              @select="selectCanvasBackground(option.color)"
            >
              <span class="size-4 shrink-0 rounded-full border" :style="{ backgroundColor: option.color }" />
              <span class="flex-1">{{ option.name }}</span>
              <Check v-if="canvasBackgroundColor === option.color" class="size-3.5" />
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
        <Button
          variant="outline"
          size="icon"
          :disabled="!canUndo"
          title="撤回（Ctrl+Z）"
          aria-label="撤回"
          @click="undoCanvas"
        >
          <Undo2 class="h-4 w-4" />
        </Button>
        <Button
          variant="outline"
          size="icon"
          :disabled="!canRedo"
          title="重做（Ctrl+Y）"
          aria-label="重做"
          @click="redoCanvas"
        >
          <Redo2 class="h-4 w-4" />
        </Button>
        <Button variant="outline" size="sm" :disabled="saveStatus === 'saving'" @click="persistCanvas">
          <Save class="mr-1 h-4 w-4" />
          {{ { saved: '已保存', dirty: '保存更改', saving: '保存中…', error: '重试保存' }[saveStatus] }}
        </Button>
      </div>
    </header>

    <div class="relative min-h-0 flex-1">
      <VueFlow
        v-model:nodes="nodes"
        v-model:edges="edges"
        :min-zoom="0.35"
        :max-zoom="1.8"
        :zoom-on-scroll="false"
        :zoom-on-pinch="true"
        :pan-on-scroll="true"
        zoom-activation-key-code="Control"
        :default-viewport="{ x: 80, y: 40, zoom: 0.9 }"
        :nodes-connectable="false"
        fit-view-on-init
        class="image-workflow-canvas"
        @nodes-change="handleNodeChanges"
        @edges-change="handleEdgeChanges"
        @viewport-change-end="scheduleSave"
      >
        <Background
          variant="dots"
          :gap="20"
          :size="1.2"
          :bg-color="canvasBackgroundColor"
          :pattern-color="selectedBackground.pattern"
        />
        <template #node-generation="{ id, data }">
          <GenerationNode
            :data="data"
            :text-models="textModels"
            :image-models="imageModels"
            :model-loading="modelLoading"
            :busy="busyNodeIds.has(id)"
            :deletable="id !== 'generation-root'"
            :session-id="sessionId"
            @submit="payload => runGeneration(id, payload)"
            @draft-change="patch => updateGenerationDraft(id, patch)"
            @edit-mask="openGenerationMaskEditor(id)"
            @delete="deleteCanvasNode(id)"
          />
        </template>
        <template #node-result="{ id, data }">
          <ResultNode
            :data="data"
            :upscaling="upscalingNodeIds.has(id)"
            :can-regenerate="generationInputs.has(data.parentNodeId ?? '')"
            @continue="continueFromResult(id)"
            @mask="openMaskEditor(id)"
            @copy-prompt="copyResultPrompt(id)"
            @download="downloadResult(id)"
            @save-asset="openAssetDialog(id)"
            @upload-prompt="openPromptDialog(id)"
            @regenerate="regenerateResult(id)"
            @upscale="factor => upscaleResult(id, factor)"
            @dimensions="(width, height) => updateNodeData(id, { width, height })"
            @delete="deleteCanvasNode(id)"
          />
        </template>
      </VueFlow>
      <div v-if="workspaceLoading" class="absolute inset-0 grid place-items-center bg-background/70 text-sm text-muted-foreground backdrop-blur-sm">正在恢复画布…</div>

      <div class="pointer-events-none absolute bottom-4 left-4 flex items-center gap-2 rounded-lg bg-background/90 px-3 py-2 text-xs text-muted-foreground shadow-sm ring-1 ring-black/5">
        <Focus class="h-3.5 w-3.5" />
        Ctrl + 滚轮缩放 · Ctrl + / − 缩放 · 普通滚轮移动画布
      </div>
    </div>

    <MaskEditorDialog
      v-if="maskEditorTarget"
      :image-url="maskEditorTarget.imageUrl"
      @close="maskEditorTarget = undefined"
      @apply="applyMask"
    />

    <Dialog v-model:open="assetDialogOpen">
      <DialogContent class="sm:max-w-[420px]">
        <DialogHeader>
          <DialogTitle>保存到素材库</DialogTitle>
          <DialogDescription>将生成图片保存到指定素材空间。</DialogDescription>
        </DialogHeader>
        <div class="space-y-1.5 py-2">
          <Label for="canvas-asset-space">目标素材空间</Label>
          <Select v-if="assetSpaces.length" v-model="selectedAssetSpaceId">
            <SelectTrigger id="canvas-asset-space">
              <SelectValue placeholder="选择空间（可选）" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem v-for="space in assetSpaces" :key="space.id" :value="space.id">{{ space.name }}</SelectItem>
            </SelectContent>
          </Select>
          <p v-else class="text-xs text-muted-foreground">暂无可用素材空间，将保存到默认空间。</p>
        </div>
        <DialogFooter>
          <Button variant="outline" :disabled="savingAsset" @click="assetDialogOpen = false">取消</Button>
          <Button :disabled="savingAsset" @click="saveResultToAsset">
            <Loader2 v-if="savingAsset" class="mr-2 h-4 w-4 animate-spin" />
            确定保存
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="promptDialogOpen">
      <DialogContent class="sm:max-w-[480px]">
        <DialogHeader>
          <DialogTitle>上传到提示词库</DialogTitle>
          <DialogDescription>保存生成提示词，并使用当前结果图片作为封面。</DialogDescription>
        </DialogHeader>
        <div class="space-y-4 py-2">
          <div class="space-y-1.5">
            <Label>提示词</Label>
            <Textarea
              :model-value="promptTargetNodeId ? resultPrompt(promptTargetNodeId) : ''"
              readonly
              rows="4"
              class="max-h-32 overflow-y-auto text-xs"
            />
          </div>
          <div class="space-y-1.5">
            <Label for="canvas-prompt-category">品类</Label>
            <Select v-model="promptCategory">
              <SelectTrigger id="canvas-prompt-category"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem v-for="category in promptCategories" :key="category" :value="category">{{ category }}</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div class="space-y-1.5">
            <Label for="canvas-prompt-tags">标签（可选）</Label>
            <Input id="canvas-prompt-tags" v-model="promptTags" placeholder="多个标签使用逗号分隔" />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" :disabled="savingPrompt" @click="promptDialogOpen = false">取消</Button>
          <Button :disabled="!promptCategory || savingPrompt" @click="saveResultPrompt">
            <Loader2 v-if="savingPrompt" class="mr-2 h-4 w-4 animate-spin" />
            确定上传
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<style>
.image-workflow-canvas {
  background-color: transparent;
}

.image-workflow-canvas .vue-flow__node {
  border: 0;
  background: transparent;
  padding: 0;
}

.image-workflow-canvas .vue-flow__node.selected > * {
  outline: 2px solid hsl(var(--primary) / 0.55);
  outline-offset: 3px;
}
</style>
