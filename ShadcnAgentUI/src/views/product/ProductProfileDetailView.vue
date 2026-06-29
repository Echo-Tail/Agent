<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  getProductProfile,
  updateProductProfileFacts,
  confirmProductProfile,
  reparseProductProfile,
  getProductProfileVersions,
  getProductProfileImages,
  uploadProductProfileImage,
  deleteProductProfileImage,
  generateSellingPointCognitions,
  getCurrentSellingPointCognition,
  getSellingPointCognitionVersions,
  updateSellingPointCognition,
  confirmSellingPointCognition,
  generateVisualStrategy,
  getCurrentVisualStrategy,
  getVisualStrategyVersions,
  updateVisualStrategy,
  confirmVisualStrategy,
  type ProductProfile,
  type ProductProfileVersion,
  type ProductProfileImage,
  type SellingPointCognitionVersion,
  type SellingPointCognitionJson,
  type VisualStrategyVersion,
  type VisualStrategyJson,
} from '@/api/product-profiles'
import {
  ArrowLeft,
  Loader2,
  Save,
  CheckCircle,
  RefreshCw,
  Upload,
  Trash2,
  Sparkles,
  Copy,
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const profileId = Number(route.params.id)

const profile = ref({} as ProductProfile)
const versions = ref<ProductProfileVersion[]>([])
const images = ref<ProductProfileImage[]>([])
const loading = ref(true)
const loaded = ref(false)
const saving = ref(false)
const confirming = ref(false)
const reparsing = ref(false)
const uploading = ref(false)

const editFactsJson = ref('')
const selectedFile = ref<File | null>(null)
const uploadTag = ref('other')

const cognitionVersion = ref<SellingPointCognitionVersion | null>(null)
const cognitionVersions = ref<SellingPointCognitionVersion[]>([])
const cognitionData = ref<SellingPointCognitionJson | null>(null)
const cognitionRawJson = ref('')
const cognitionGlobalConstraintsText = ref('')
const cognitionClaimsToAvoidText = ref('')
const cognitionLoading = ref(false)
const cognitionGenerating = ref(false)
const cognitionSaving = ref(false)
const cognitionConfirming = ref(false)
const cognitionError = ref('')

const cognitionTypeOptions = ['compatibility', 'connection', 'display', 'safety', 'navigation', 'audio', 'performance', 'installation', 'risk_constraint']
const visualModelOptions = ['connection', 'scenario', 'comparison', 'infographic']


const visualStrategyVersion = ref<VisualStrategyVersion | null>(null)
const visualStrategyVersions = ref<VisualStrategyVersion[]>([])
const visualStrategyData = ref<VisualStrategyJson | null>(null)
const visualStrategyRawJson = ref('')
const visualStrategyScope = ref<'all' | 'gallery' | 'aplus'>('all')
const visualStrategyLoading = ref(false)
const visualStrategyGenerating = ref(false)
const visualStrategySaving = ref(false)
const visualStrategyConfirming = ref(false)
const visualStrategyError = ref('')


const statusLabels: Record<string, string> = {
  PENDING_PARSE: '待解析',
  PENDING_CONFIRM: '待确认',
  PENDING_CONFIRM_VERSION: '待确认（新版本）',
  CONFIRMED: '已确认',
  PARSE_FAILED: '解析失败',
}

const tagOptions = [
  { value: 'front', label: '正面图' },
  { value: 'back', label: '背面图' },
  { value: 'ports', label: '接口图' },
  { value: 'wiring', label: '线束图' },
  { value: 'package', label: '包装/配件图' },
  { value: 'installation', label: '安装效果图' },
  { value: 'other', label: '其他' },
]

const canConfirm = computed(() =>
  loaded.value && (profile.value.status === 'PENDING_CONFIRM' || profile.value.status === 'PENDING_CONFIRM_VERSION')
)

const canParse = computed(() =>
  loaded.value && (profile.value.status === 'PARSE_FAILED' || profile.value.status === 'PENDING_PARSE')
)

async function loadAll() {
  loading.value = true
  try {
    const [profileRes, versionsRes, imagesRes] = await Promise.all([
      getProductProfile(profileId),
      getProductProfileVersions(profileId),
      getProductProfileImages(profileId),
    ])
    profile.value = profileRes
    versions.value = versionsRes
    images.value = imagesRes
    editFactsJson.value = profileRes.productFactsJson || defaultFactsJson()
    loaded.value = true
    await loadCognition(true)
    await loadVisualStrategy(true)
  } finally {
    loading.value = false
  }
}

function defaultFactsJson() {
  return JSON.stringify({
    identity: { product_name: '', brand: '', manufacturer: '', model_number: '', sku: '', target_asin: '', category: 'car stereo' },
    amazon_listing: {
      title: '',
      bullet_points: [],
      product_description: '',
      product_details: {},
      technical_details: {},
      included_components_raw: [],
      important_information: '',
    },
    physical_specs: { screen_size: '', form_factor: '', product_dimensions: '', item_weight: '', color: '', material: '' },
    technical_specs: {
      operating_system: '',
      ram: '',
      storage: '',
      resolution: '',
      controller_type: '',
      connectivity: [],
      connector_types: [],
      control_methods: [],
      audio_output_mode: '',
      supported_media: [],
    },
    features: {
      carplay: '',
      android_auto: '',
      bluetooth: '',
      wifi: '',
      backup_camera: '',
      gps_navigation: '',
      steering_wheel_control: '',
      fm_am_radio: '',
      subwoofer_support: '',
      split_screen: '',
      mirror_link: '',
    },
    compatibility: { vehicle_fitment: [], compatible_devices: [], not_compatible: [], unsupported_or_unknown: [], fitment_notes: '' },
    included_items: [],
    warranty: '',
    selling_points: [],
    image_prompt_facts: {
      primary_visual_claims: [],
      installation_scene_facts: [],
      comparison_points: [],
      package_content_points: [],
      compatibility_points: [],
    },
    claims_to_avoid: [],
    review: { status: 'needs_human_review', missing_fields: [], low_confidence_fields: [], notes: '' },
  }, null, 2)
}

async function saveFacts() {
  saving.value = true
  try {
    const updated = await updateProductProfileFacts(profileId, editFactsJson.value)
    profile.value = updated
    toast.success('产品事实已保存')
  } finally {
    saving.value = false
  }
}

async function handleConfirm() {
  confirming.value = true
  try {
    const updated = await confirmProductProfile(profileId)
    profile.value = updated
    toast.success('产品资料已确认')
    versions.value = await getProductProfileVersions(profileId)
  } finally {
    confirming.value = false
  }
}

async function handleReparse() {
  reparsing.value = true
  try {
    const updated = await reparseProductProfile(profileId)
    profile.value = updated
    editFactsJson.value = updated.productFactsJson || defaultFactsJson()
    toast.success('重新解析完成')
  } finally {
    reparsing.value = false
  }
}


function emptyCognitionJson(): SellingPointCognitionJson {
  return {
    category: 'car_stereo',
    category_strategy_version: 'car_stereo_v1',
    status: 'draft',
    buyer_cognitions: [],
    global_constraints: [],
    claims_to_avoid: [],
    review: { status: 'needs_human_review', missing_fields: [], low_confidence_items: [], notes: '' },
  }
}

function splitLines(value: string): string[] {
  return value.split('\n').map(v => v.trim()).filter(Boolean)
}

function applyCognitionVersion(version: SellingPointCognitionVersion) {
  cognitionVersion.value = version
  const parsed = version.cognitionJson ? JSON.parse(version.cognitionJson) as SellingPointCognitionJson : emptyCognitionJson()
  parsed.buyer_cognitions ||= []
  parsed.global_constraints ||= []
  parsed.claims_to_avoid ||= []
  cognitionData.value = parsed
  cognitionGlobalConstraintsText.value = parsed.global_constraints.join('\n')
  cognitionClaimsToAvoidText.value = parsed.claims_to_avoid.join('\n')
  cognitionRawJson.value = JSON.stringify(parsed, null, 2)
}

function refreshCognitionRawJson() {
  if (!cognitionData.value) return
  cognitionData.value.global_constraints = splitLines(cognitionGlobalConstraintsText.value)
  cognitionData.value.claims_to_avoid = splitLines(cognitionClaimsToAvoidText.value)
  cognitionRawJson.value = JSON.stringify(cognitionData.value, null, 2)
}

function applyRawCognitionJson() {
  try {
    const parsed = JSON.parse(cognitionRawJson.value) as SellingPointCognitionJson
    parsed.buyer_cognitions ||= []
    parsed.global_constraints ||= []
    parsed.claims_to_avoid ||= []
    cognitionData.value = parsed
    cognitionGlobalConstraintsText.value = parsed.global_constraints.join('\n')
    cognitionClaimsToAvoidText.value = parsed.claims_to_avoid.join('\n')
    toast.success('Cognition JSON applied')
  } catch (error) {
    toast.error('Invalid cognition JSON')
  }
}

async function loadCognition(silent = false) {
  cognitionLoading.value = true
  cognitionError.value = ''
  try {
    const [current, list] = await Promise.all([
      getCurrentSellingPointCognition(profileId),
      getSellingPointCognitionVersions(profileId),
    ])
    applyCognitionVersion(current)
    cognitionVersions.value = list
  } catch (error: any) {
    cognitionVersion.value = null
    cognitionData.value = null
    cognitionRawJson.value = ''
    cognitionGlobalConstraintsText.value = ''
    cognitionClaimsToAvoidText.value = ''
    cognitionVersions.value = []
    if (!silent) {
      cognitionError.value = error?.message || 'Failed to load selling point cognitions'
      toast.error(cognitionError.value)
    }
  } finally {
    cognitionLoading.value = false
  }
}

async function generateCognitions() {
  cognitionGenerating.value = true
  cognitionError.value = ''
  try {
    const version = await generateSellingPointCognitions(profileId)
    applyCognitionVersion(version)
    cognitionVersions.value = await getSellingPointCognitionVersions(profileId)
    toast.success('Selling point cognitions generated')
  } finally {
    cognitionGenerating.value = false
  }
}

async function saveCognition() {
  if (!cognitionVersion.value || !cognitionData.value) return
  cognitionSaving.value = true
  try {
    refreshCognitionRawJson()
    const version = await updateSellingPointCognition(profileId, cognitionVersion.value.id, cognitionRawJson.value)
    applyCognitionVersion(version)
    cognitionVersions.value = await getSellingPointCognitionVersions(profileId)
    toast.success('Selling point cognitions saved')
  } finally {
    cognitionSaving.value = false
  }
}

async function confirmCognition() {
  if (!cognitionVersion.value) return
  cognitionConfirming.value = true
  try {
    refreshCognitionRawJson()
    await updateSellingPointCognition(profileId, cognitionVersion.value.id, cognitionRawJson.value)
    const version = await confirmSellingPointCognition(profileId, cognitionVersion.value.id)
    applyCognitionVersion(version)
    cognitionVersions.value = await getSellingPointCognitionVersions(profileId)
    toast.success('Selling point cognition version confirmed')
  } finally {
    cognitionConfirming.value = false
  }
}


function emptyVisualStrategyJson(): VisualStrategyJson {
  return {
    category: 'car_stereo',
    category_strategy_version: 'car_stereo_v1',
    status: 'draft',
    content_scope: [],
    global_constraints: [],
    claims_to_avoid: [],
    review: { status: 'needs_human_review', missing_assets: [], low_confidence_prompts: [], notes: '' },
  }
}

function applyVisualStrategyVersion(version: VisualStrategyVersion) {
  visualStrategyVersion.value = version
  const parsed = version.strategyJson ? JSON.parse(version.strategyJson) as VisualStrategyJson : emptyVisualStrategyJson()
  parsed.content_scope ||= []
  parsed.global_constraints ||= []
  parsed.claims_to_avoid ||= []
  if (parsed.gallery_strategy) parsed.gallery_strategy.images ||= []
  if (parsed.aplus_strategy) parsed.aplus_strategy.modules ||= []
  visualStrategyData.value = parsed
  visualStrategyRawJson.value = JSON.stringify(parsed, null, 2)
}

function refreshVisualStrategyRawJson() {
  if (!visualStrategyData.value) return
  visualStrategyRawJson.value = JSON.stringify(visualStrategyData.value, null, 2)
}

function applyRawVisualStrategyJson() {
  try {
    const parsed = JSON.parse(visualStrategyRawJson.value) as VisualStrategyJson
    parsed.content_scope ||= []
    parsed.global_constraints ||= []
    parsed.claims_to_avoid ||= []
    if (parsed.gallery_strategy) parsed.gallery_strategy.images ||= []
    if (parsed.aplus_strategy) parsed.aplus_strategy.modules ||= []
    visualStrategyData.value = parsed
    toast.success('Visual strategy JSON applied')
  } catch (error) {
    toast.error('Invalid visual strategy JSON')
  }
}

async function loadVisualStrategy(silent = false) {
  visualStrategyLoading.value = true
  visualStrategyError.value = ''
  try {
    const [current, list] = await Promise.all([
      getCurrentVisualStrategy(profileId),
      getVisualStrategyVersions(profileId),
    ])
    applyVisualStrategyVersion(current)
    visualStrategyVersions.value = list
  } catch (error: any) {
    visualStrategyVersion.value = null
    visualStrategyData.value = null
    visualStrategyRawJson.value = ''
    visualStrategyVersions.value = []
    if (!silent) {
      visualStrategyError.value = error?.message || 'Failed to load visual strategy'
      toast.error(visualStrategyError.value)
    }
  } finally {
    visualStrategyLoading.value = false
  }
}

function selectedVisualScope(): string[] | undefined {
  if (visualStrategyScope.value === 'gallery') return ['gallery']
  if (visualStrategyScope.value === 'aplus') return ['aplus']
  return ['gallery', 'aplus']
}

async function generateVisualStrategyDraft() {
  visualStrategyGenerating.value = true
  visualStrategyError.value = ''
  try {
    const version = await generateVisualStrategy(profileId, {
      cognition_version_id: cognitionVersion.value?.id ?? null,
      content_scope: selectedVisualScope(),
    })
    applyVisualStrategyVersion(version)
    visualStrategyVersions.value = await getVisualStrategyVersions(profileId)
    toast.success('Visual strategy generated')
  } finally {
    visualStrategyGenerating.value = false
  }
}

async function saveVisualStrategy() {
  if (!visualStrategyVersion.value || !visualStrategyData.value) return
  visualStrategySaving.value = true
  try {
    refreshVisualStrategyRawJson()
    const version = await updateVisualStrategy(profileId, visualStrategyVersion.value.id, visualStrategyRawJson.value)
    applyVisualStrategyVersion(version)
    visualStrategyVersions.value = await getVisualStrategyVersions(profileId)
    toast.success('Visual strategy saved')
  } finally {
    visualStrategySaving.value = false
  }
}

async function confirmVisualStrategyVersion() {
  if (!visualStrategyVersion.value) return
  visualStrategyConfirming.value = true
  try {
    refreshVisualStrategyRawJson()
    await updateVisualStrategy(profileId, visualStrategyVersion.value.id, visualStrategyRawJson.value)
    const version = await confirmVisualStrategy(profileId, visualStrategyVersion.value.id)
    applyVisualStrategyVersion(version)
    visualStrategyVersions.value = await getVisualStrategyVersions(profileId)
    toast.success('Visual strategy version confirmed')
  } finally {
    visualStrategyConfirming.value = false
  }
}

async function copyText(value: string | undefined) {
  if (!value) return
  await navigator.clipboard.writeText(value)
  toast.success('Copied')
}

function handleFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  selectedFile.value = input.files?.[0] || null
}

async function uploadImage() {
  if (!selectedFile.value) {
    toast.error('请先选择图片文件')
    return
  }
  uploading.value = true
  try {
    await uploadProductProfileImage(profileId, selectedFile.value, uploadTag.value)
    toast.success('图片已上传')
    images.value = await getProductProfileImages(profileId)
    selectedFile.value = null
    uploadTag.value = 'other'
  } finally {
    uploading.value = false
  }
}

async function deleteImage(imageId: number) {
  if (!window.confirm('确认删除该图片？')) return
  await deleteProductProfileImage(imageId)
  toast.success('图片已删除')
  images.value = await getProductProfileImages(profileId)
}

function goBack() {
  router.push({ name: 'ProductProfileList' })
}

function imageUrl(path: string): string {
  if (!path) return ''
  if (/^https?:\/\//i.test(path) || path.startsWith('/uploads/')) return path
  const normalized = path.replace(/\\/g, '/').replace(/^\.\//, '')
  return '/uploads/' + normalized
}

onMounted(loadAll)
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center gap-2">
      <Button variant="ghost" size="icon" @click="goBack">
        <ArrowLeft class="h-4 w-4" />
      </Button>
      <div v-if="loaded">
        <h2 class="text-lg font-semibold">{{ profile.productName }}</h2>
        <p class="text-sm text-muted-foreground">{{ profile.brand || '' }} {{ profile.sku ? 'SKU: ' + profile.sku : '' }}</p>
      </div>
    </div>

    <div v-if="loading" class="py-16 text-center text-sm text-muted-foreground">加载中...</div>

    <template v-else-if="loaded">
      <Tabs default-value="facts">
        <TabsList>
          <TabsTrigger value="facts">产品事实</TabsTrigger>
          <TabsTrigger value="cognitions">卖点认知</TabsTrigger>
          <TabsTrigger value="visual-strategy">视觉策略</TabsTrigger>
          <TabsTrigger value="versions">版本历史</TabsTrigger>
          <TabsTrigger value="images">默认自有产品图</TabsTrigger>
        </TabsList>

        <TabsContent value="facts" class="space-y-4 pt-4">
          <div class="flex items-center justify-between gap-2">
            <div class="flex items-center gap-2">
              <Badge :class="{
                'bg-yellow-100 text-yellow-800': profile.status.startsWith('PENDING'),
                'bg-green-100 text-green-800': profile.status === 'CONFIRMED',
                'bg-red-100 text-red-800': profile.status === 'PARSE_FAILED',
              }">
                {{ statusLabels[profile.status] || profile.status }}
              </Badge>
              <span v-if="profile.status === 'CONFIRMED' && profile.currentVersionId" class="text-xs text-muted-foreground">
                当前版本: #{{ versions.find(v => v.id === profile.currentVersionId)?.versionNumber || '-' }}
              </span>
            </div>
            <div class="flex items-center gap-2">
              <Button v-if="canParse" variant="outline" size="sm" :disabled="reparsing" @click="handleReparse">
                <RefreshCw :class="['h-4 w-4 mr-1', reparsing ? 'animate-spin' : '']" />重新解析
              </Button>
              <Button v-if="canConfirm" size="sm" :disabled="confirming" @click="handleConfirm">
                <Loader2 v-if="confirming" class="h-4 w-4 mr-1 animate-spin" />
                <CheckCircle v-else class="h-4 w-4 mr-1" />确认
              </Button>
            </div>
          </div>

          <div v-if="profile.parseError" class="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">
            {{ profile.parseError }}
          </div>

          <div>
            <label class="text-sm font-medium">目标产品事实 JSON</label>
            <p class="text-xs text-muted-foreground mb-2">人工编辑自有产品事实，确认后将生成版本快照。</p>
            <Textarea v-model="editFactsJson" class="min-h-[400px] font-mono text-xs" />
          </div>

          <Button :disabled="saving" @click="saveFacts">
            <Loader2 v-if="saving" class="h-4 w-4 mr-1 animate-spin" />
            <Save v-else class="h-4 w-4 mr-1" />保存产品事实
          </Button>
        </TabsContent>


        <TabsContent value="cognitions" class="space-y-4 pt-4">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div class="space-y-1">
              <div class="flex items-center gap-2">
                <h3 class="text-sm font-semibold">卖点认知</h3>
                <Badge v-if="cognitionVersion" :class="cognitionVersion.status === 'CONFIRMED' ? 'bg-green-100 text-green-800' : 'bg-yellow-100 text-yellow-800'">
                  {{ cognitionVersion.status }} #{{ cognitionVersion.versionNumber }}
                </Badge>
              </div>
              <p class="text-xs text-muted-foreground">将产品事实转化为买家认知、证据和约束，为视觉策略生成做准备。</p>
            </div>
            <div class="flex flex-wrap items-center gap-2">
              <Button variant="outline" size="sm" :disabled="cognitionLoading" @click="loadCognition(false)">
                <RefreshCw :class="['h-4 w-4 mr-1', cognitionLoading ? 'animate-spin' : '']" />刷新
              </Button>
              <Button size="sm" :disabled="cognitionGenerating" @click="generateCognitions">
                <Loader2 v-if="cognitionGenerating" class="h-4 w-4 mr-1 animate-spin" />
                <Sparkles v-else class="h-4 w-4 mr-1" />生成
              </Button>
              <Button v-if="cognitionVersion && cognitionVersion.status !== 'CONFIRMED'" variant="outline" size="sm" :disabled="cognitionSaving" @click="saveCognition">
                <Loader2 v-if="cognitionSaving" class="h-4 w-4 mr-1 animate-spin" />
                <Save v-else class="h-4 w-4 mr-1" />保存草稿
              </Button>
              <Button v-if="cognitionVersion && cognitionVersion.status !== 'CONFIRMED'" size="sm" :disabled="cognitionConfirming" @click="confirmCognition">
                <Loader2 v-if="cognitionConfirming" class="h-4 w-4 mr-1 animate-spin" />
                <CheckCircle v-else class="h-4 w-4 mr-1" />确认
              </Button>
            </div>
          </div>

          <div v-if="cognitionError" class="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">
            {{ cognitionError }}
          </div>

          <div v-if="cognitionLoading" class="py-10 text-center text-sm text-muted-foreground">加载卖点认知...</div>

          <div v-else-if="!cognitionData" class="rounded-md border border-dashed p-8 text-center space-y-3">
            <div class="text-sm font-medium">暂无卖点认知版本</div>
            <p class="text-xs text-muted-foreground">产品事实确认后即可生成草稿，生成后可审核编辑再用于视觉策略。</p>
            <Button :disabled="cognitionGenerating" @click="generateCognitions">
              <Loader2 v-if="cognitionGenerating" class="h-4 w-4 mr-1 animate-spin" />
              <Sparkles v-else class="h-4 w-4 mr-1" />生成卖点认知
            </Button>
          </div>

          <div v-else class="space-y-4">
            <div class="grid gap-3 md:grid-cols-2">
              <div>
                <label class="text-xs font-medium">全局约束</label>
                <Textarea v-model="cognitionGlobalConstraintsText" class="min-h-28 text-xs" placeholder="每行一条约束" @blur="refreshCognitionRawJson" />
              </div>
              <div>
                <label class="text-xs font-medium">需避免的宣称</label>
                <Textarea v-model="cognitionClaimsToAvoidText" class="min-h-28 text-xs" placeholder="每行一条" @blur="refreshCognitionRawJson" />
              </div>
            </div>

            <div class="space-y-3">
              <div class="flex items-center justify-between">
                <h4 class="text-sm font-semibold">买家认知</h4>
                <span class="text-xs text-muted-foreground">{{ cognitionData.buyer_cognitions.length }} 条</span>
              </div>

              <div
                v-for="(item, index) in cognitionData.buyer_cognitions"
                :key="item.id || index"
                class="rounded-md border bg-card p-3 space-y-3"
              >
                <div class="grid gap-3 lg:grid-cols-[80px_90px_150px_150px_1fr]">
                  <label class="flex items-center gap-2 text-xs">
                    <input v-model="item.enabled" type="checkbox" class="h-4 w-4" @change="refreshCognitionRawJson" />
                    启用
                  </label>
                  <div>
                    <label class="text-xs font-medium">优先级</label>
                    <Input v-model.number="item.priority" type="number" class="h-8" @blur="refreshCognitionRawJson" />
                  </div>
                  <div>
                    <label class="text-xs font-medium">类型</label>
                    <select v-model="item.type" class="h-8 w-full rounded-md border border-input bg-background px-2 text-xs" @change="refreshCognitionRawJson">
                      <option v-for="opt in cognitionTypeOptions" :key="opt" :value="opt">{{ opt }}</option>
                    </select>
                  </div>
                  <div>
                    <label class="text-xs font-medium">视觉模型</label>
                    <select v-model="item.visual_model" class="h-8 w-full rounded-md border border-input bg-background px-2 text-xs" @change="refreshCognitionRawJson">
                      <option v-for="opt in visualModelOptions" :key="opt" :value="opt">{{ opt }}</option>
                    </select>
                  </div>
                  <div>
                    <label class="text-xs font-medium">特征</label>
                    <Input v-model="item.feature" class="h-8" @blur="refreshCognitionRawJson" />
                  </div>
                </div>

                <div class="grid gap-3 md:grid-cols-2">
                  <div>
                    <label class="text-xs font-medium">买家认知（中文）</label>
                    <Textarea v-model="item.buyer_cognition_cn" class="min-h-20 text-xs" @blur="refreshCognitionRawJson" />
                  </div>
                  <div>
                    <label class="text-xs font-medium">买家认知（英文）</label>
                    <Textarea v-model="item.buyer_cognition_en" class="min-h-20 text-xs" @blur="refreshCognitionRawJson" />
                  </div>
                </div>

                <details class="rounded border bg-muted/20 p-2">
                  <summary class="cursor-pointer text-xs font-medium">证据 / 风险说明</summary>
                  <div class="mt-2 space-y-2 text-xs">
                    <div v-if="!item.evidence?.length" class="text-muted-foreground">无证据</div>
                    <div v-for="(ev, evIndex) in item.evidence" :key="evIndex" class="rounded bg-background p-2">
                      <div class="font-mono text-[11px] text-muted-foreground">{{ ev.source_path }}</div>
                      <div class="mt-1">{{ ev.source_text }}</div>
                    </div>
                    <div v-if="item.risk_notes?.length" class="text-muted-foreground">风险：{{ item.risk_notes.join('; ') }}</div>
                  </div>
                </details>
              </div>
            </div>

            <details class="rounded-md border p-3">
              <summary class="cursor-pointer text-sm font-medium">高级 JSON</summary>
              <div class="mt-3 space-y-2">
                <Textarea v-model="cognitionRawJson" class="min-h-[260px] font-mono text-xs" />
                <Button variant="outline" size="sm" @click="applyRawCognitionJson">应用 JSON 到表单</Button>
              </div>
            </details>
          </div>
        </TabsContent>


        <TabsContent value="visual-strategy" class="space-y-4 pt-4">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div class="space-y-1">
              <div class="flex items-center gap-2">
                <h3 class="text-sm font-semibold">视觉策略</h3>
                <Badge v-if="visualStrategyVersion" :class="visualStrategyVersion.status === 'CONFIRMED' ? 'bg-green-100 text-green-800' : 'bg-yellow-100 text-yellow-800'">
                  {{ visualStrategyVersion.status }} #{{ visualStrategyVersion.versionNumber }} ? {{ visualStrategyVersion.contentScope }}
                </Badge>
              </div>
              <p class="text-xs text-muted-foreground">根据已确认的卖点认知生成可编辑的 Gallery 和 A+ 视觉脚本。</p>
            </div>
            <div class="flex flex-wrap items-center gap-2">
              <select v-model="visualStrategyScope" class="h-9 rounded-md border border-input bg-background px-3 text-sm">
                <option value="all">Gallery + A+</option>
                <option value="gallery">仅 Gallery</option>
                <option value="aplus">仅 A+</option>
              </select>
              <Button variant="outline" size="sm" :disabled="visualStrategyLoading" @click="loadVisualStrategy(false)">
                <RefreshCw :class="['h-4 w-4 mr-1', visualStrategyLoading ? 'animate-spin' : '']" />刷新
              </Button>
              <Button size="sm" :disabled="visualStrategyGenerating" @click="generateVisualStrategyDraft">
                <Loader2 v-if="visualStrategyGenerating" class="h-4 w-4 mr-1 animate-spin" />
                <Sparkles v-else class="h-4 w-4 mr-1" />生成
              </Button>
              <Button v-if="visualStrategyVersion && visualStrategyVersion.status !== 'CONFIRMED'" variant="outline" size="sm" :disabled="visualStrategySaving" @click="saveVisualStrategy">
                <Loader2 v-if="visualStrategySaving" class="h-4 w-4 mr-1 animate-spin" />
                <Save v-else class="h-4 w-4 mr-1" />保存草稿
              </Button>
              <Button v-if="visualStrategyVersion && visualStrategyVersion.status !== 'CONFIRMED'" size="sm" :disabled="visualStrategyConfirming" @click="confirmVisualStrategyVersion">
                <Loader2 v-if="visualStrategyConfirming" class="h-4 w-4 mr-1 animate-spin" />
                <CheckCircle v-else class="h-4 w-4 mr-1" />确认
              </Button>
            </div>
          </div>

          <div v-if="visualStrategyError" class="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">
            {{ visualStrategyError }}
          </div>

          <div v-if="visualStrategyLoading" class="py-10 text-center text-sm text-muted-foreground">加载视觉策略...</div>

          <div v-else-if="!visualStrategyData" class="rounded-md border border-dashed p-8 text-center space-y-3">
            <div class="text-sm font-medium">暂无视觉策略版本</div>
            <p class="text-xs text-muted-foreground">请先确认卖点认知，再生成 Gallery 和 A+ 视觉脚本。</p>
            <Button :disabled="visualStrategyGenerating" @click="generateVisualStrategyDraft">
              <Loader2 v-if="visualStrategyGenerating" class="h-4 w-4 mr-1 animate-spin" />
              <Sparkles v-else class="h-4 w-4 mr-1" />生成视觉策略
            </Button>
          </div>

          <div v-else class="space-y-5">
            <div class="grid gap-3 md:grid-cols-2">
              <div class="rounded-md border bg-muted/20 p-3">
                <div class="text-xs font-medium text-muted-foreground">全局约束</div>
                <ul class="mt-2 list-disc space-y-1 pl-4 text-xs">
                  <li v-for="(item, index) in visualStrategyData.global_constraints" :key="index">{{ item }}</li>
                  <li v-if="!visualStrategyData.global_constraints.length" class="list-none text-muted-foreground">无</li>
                </ul>
              </div>
              <div class="rounded-md border bg-muted/20 p-3">
                <div class="text-xs font-medium text-muted-foreground">需避免的宣称</div>
                <ul class="mt-2 list-disc space-y-1 pl-4 text-xs">
                  <li v-for="(item, index) in visualStrategyData.claims_to_avoid" :key="index">{{ item }}</li>
                  <li v-if="!visualStrategyData.claims_to_avoid.length" class="list-none text-muted-foreground">无</li>
                </ul>
              </div>
            </div>

            <section v-if="visualStrategyData.gallery_strategy" class="space-y-3">
              <div class="flex items-center justify-between">
                <h4 class="text-sm font-semibold">Gallery 图片</h4>
                <span class="text-xs text-muted-foreground">{{ visualStrategyData.gallery_strategy.images.length }} 张</span>
              </div>
              <div class="grid gap-3 xl:grid-cols-2">
                <div v-for="image in visualStrategyData.gallery_strategy.images" :key="image.slot" class="rounded-md border bg-card p-3 space-y-3">
                  <div class="flex flex-wrap items-center justify-between gap-2">
                    <div class="flex items-center gap-2">
                      <Badge>图片 {{ image.slot }}</Badge>
                      <span class="text-sm font-medium">{{ image.role }}</span>
                    </div>
                    <Badge variant="outline">{{ image.visual_model }}</Badge>
                  </div>
                  <div class="grid gap-3 md:grid-cols-2">
                    <div>
                      <label class="text-xs font-medium">标题（英文）</label>
                      <Input v-model="image.text_overlays_en.headline" class="h-8" @blur="refreshVisualStrategyRawJson" />
                    </div>
                    <div>
                      <label class="text-xs font-medium">副标题（英文）</label>
                      <Input v-model="image.text_overlays_en.subhead" class="h-8" @blur="refreshVisualStrategyRawJson" />
                    </div>
                  </div>
                  <div>
                    <label class="text-xs font-medium">视觉结构（英文）</label>
                    <Textarea v-model="image.visual_structure_en" class="min-h-20 text-xs" @blur="refreshVisualStrategyRawJson" />
                  </div>
                  <div>
                    <div class="mb-1 flex items-center justify-between">
                      <label class="text-xs font-medium">提示词（英文）</label>
                      <Button variant="outline" size="sm" class="h-7" @click="copyText(image.prompt_en)">
                        <Copy class="h-3.5 w-3.5 mr-1" />复制
                      </Button>
                    </div>
                    <Textarea v-model="image.prompt_en" class="min-h-28 text-xs" @blur="refreshVisualStrategyRawJson" />
                  </div>
                  <details class="rounded border bg-muted/20 p-2">
                    <summary class="cursor-pointer text-xs font-medium">中文提示词 / 约束</summary>
                    <div class="mt-2 space-y-2">
                      <Textarea v-model="image.prompt_cn" class="min-h-20 text-xs" @blur="refreshVisualStrategyRawJson" />
                      <div class="text-xs text-muted-foreground">负面约束：{{ image.negative_constraints.join('; ') || '无' }}</div>
                    </div>
                  </details>
                </div>
              </div>
            </section>

            <section v-if="visualStrategyData.aplus_strategy" class="space-y-3">
              <div class="flex items-center justify-between">
                <h4 class="text-sm font-semibold">A+ 模块</h4>
                <span class="text-xs text-muted-foreground">{{ visualStrategyData.aplus_strategy.modules.length }} 个</span>
              </div>
              <div class="grid gap-3 xl:grid-cols-2">
                <div v-for="module in visualStrategyData.aplus_strategy.modules" :key="module.module_index" class="rounded-md border bg-card p-3 space-y-3">
                  <div class="flex flex-wrap items-center justify-between gap-2">
                    <div class="flex items-center gap-2">
                      <Badge>模块 {{ module.module_index }}</Badge>
                      <span class="text-sm font-medium">{{ module.module_type }}</span>
                    </div>
                    <Badge variant="outline">{{ module.visual_model }}</Badge>
                  </div>
                  <div class="grid gap-3 md:grid-cols-2">
                    <div>
                      <label class="text-xs font-medium">标题（英文）</label>
                      <Input v-model="module.headline_en" class="h-8" @blur="refreshVisualStrategyRawJson" />
                    </div>
                    <div>
                      <label class="text-xs font-medium">素材</label>
                      <div class="rounded-md border bg-muted/20 px-2 py-1 text-xs text-muted-foreground">{{ module.required_assets.join(', ') || '无' }}</div>
                    </div>
                  </div>
                  <div>
                    <label class="text-xs font-medium">正文（英文）</label>
                    <Textarea v-model="module.body_copy_en" class="min-h-20 text-xs" @blur="refreshVisualStrategyRawJson" />
                  </div>
                  <div>
                    <div class="mb-1 flex items-center justify-between">
                      <label class="text-xs font-medium">图片提示词（英文）</label>
                      <Button variant="outline" size="sm" class="h-7" @click="copyText(module.image_prompt_en)">
                        <Copy class="h-3.5 w-3.5 mr-1" />复制
                      </Button>
                    </div>
                    <Textarea v-model="module.image_prompt_en" class="min-h-28 text-xs" @blur="refreshVisualStrategyRawJson" />
                  </div>
                  <details class="rounded border bg-muted/20 p-2">
                    <summary class="cursor-pointer text-xs font-medium">中文文案 / 约束</summary>
                    <div class="mt-2 space-y-2">
                      <Input v-model="module.headline_cn" class="h-8" @blur="refreshVisualStrategyRawJson" />
                      <Textarea v-model="module.body_copy_cn" class="min-h-20 text-xs" @blur="refreshVisualStrategyRawJson" />
                      <Textarea v-model="module.image_prompt_cn" class="min-h-20 text-xs" @blur="refreshVisualStrategyRawJson" />
                      <div class="text-xs text-muted-foreground">负面约束：{{ module.negative_constraints.join('; ') || '无' }}</div>
                    </div>
                  </details>
                </div>
              </div>
            </section>

            <details class="rounded-md border p-3">
              <summary class="cursor-pointer text-sm font-medium">高级 JSON</summary>
              <div class="mt-3 space-y-2">
                <Textarea v-model="visualStrategyRawJson" class="min-h-[320px] font-mono text-xs" />
                <Button variant="outline" size="sm" @click="applyRawVisualStrategyJson">应用 JSON 到表单</Button>
              </div>
            </details>
          </div>
        </TabsContent>

        <TabsContent value="versions" class="space-y-3 pt-4">
          <div v-if="versions.length === 0" class="py-12 text-center text-sm text-muted-foreground">
            暂无版本历史，确认产品事实后将创建第一个版本。
          </div>
          <div v-else class="space-y-2">
            <div
              v-for="ver in versions"
              :key="ver.id"
              :class="[
                'rounded-md border p-3',
                ver.id === profile.currentVersionId ? 'border-green-300 bg-green-50' : '',
              ]"
            >
              <div class="flex items-center justify-between gap-2">
                <div class="flex items-center gap-2">
                  <span class="font-medium">版本 #{{ ver.versionNumber }}</span>
                  <Badge v-if="ver.id === profile.currentVersionId" class="bg-green-100 text-green-800">当前</Badge>
                </div>
                <span class="text-xs text-muted-foreground">{{ ver.confirmedAt ? new Date(ver.confirmedAt).toLocaleString() : '' }}</span>
              </div>
              <details class="mt-2">
                <summary class="cursor-pointer text-xs text-muted-foreground hover:text-foreground">查看 JSON</summary>
                <pre class="mt-1 rounded bg-muted p-2 text-xs overflow-auto max-h-60">{{ ver.productFactsJson }}</pre>
              </details>
            </div>
          </div>
        </TabsContent>

        <TabsContent value="images" class="space-y-4 pt-4">
          <div class="rounded-md border bg-muted/20 p-4 space-y-3">
            <div class="text-sm font-medium">上传默认自有产品图</div>
            <div class="grid gap-3 md:grid-cols-3">
              <div>
                <label class="text-xs font-medium">图片分类</label>
                <select v-model="uploadTag" class="w-full rounded-md border border-input bg-background px-3 py-2 text-sm">
                  <option v-for="opt in tagOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                </select>
              </div>
              <div>
                <label class="text-xs font-medium">选择图片</label>
                <Input type="file" accept="image/*" @change="handleFileSelect" />
              </div>
              <div class="flex items-end">
                <Button :disabled="uploading || !selectedFile" @click="uploadImage">
                  <Upload class="h-4 w-4 mr-1" />上传
                </Button>
              </div>
            </div>
          </div>

          <div v-if="images.length === 0" class="py-12 text-center text-sm text-muted-foreground">
            暂无默认产品图，上传后可在生成任务中直接选择。
          </div>
          <div v-else class="grid gap-3 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-4">
            <div v-for="img in images" :key="img.id" class="group relative overflow-hidden rounded-md border bg-card">
              <div class="aspect-square">
                <img :src="imageUrl(img.filePath)" :alt="img.fileName" class="h-full w-full object-cover" />
              </div>
              <div class="absolute inset-0 flex items-start justify-end p-2 opacity-0 group-hover:opacity-100 transition-opacity">
                <Button variant="destructive" size="icon" class="h-7 w-7" @click="deleteImage(img.id)">
                  <Trash2 class="h-4 w-4" />
                </Button>
              </div>
              <div class="p-2 text-xs">
                <div class="truncate">{{ img.fileName }}</div>
                <div class="text-muted-foreground">{{ tagOptions.find(t => t.value === img.tag)?.label || img.tag }}</div>
              </div>
            </div>
          </div>
        </TabsContent>
      </Tabs>
    </template>
  </div>
</template>
