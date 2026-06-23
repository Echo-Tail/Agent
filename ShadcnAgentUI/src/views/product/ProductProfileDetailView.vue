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
  type ProductProfile,
  type ProductProfileVersion,
  type ProductProfileImage,
} from '@/api/product-profiles'
import {
  ArrowLeft,
  Loader2,
  Save,
  CheckCircle,
  RefreshCw,
  Upload,
  Trash2,
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
  } finally {
    loading.value = false
  }
}

function defaultFactsJson() {
  return JSON.stringify({
    identity: { product_name: '', brand: '', manufacturer: '', model_number: '', sku: '', target_asin: '', category: 'car stereo' },
    physical_specs: { screen_size: '', form_factor: '', product_dimensions: '', color: '', material: '' },
    technical_specs: { controller_type: '', connectivity: [], connector_types: [], control_methods: [], audio_output_mode: '', supported_media: [] },
    features: { carplay: '', android_auto: '', bluetooth: '', wifi: '', backup_camera: '', gps_navigation: '', steering_wheel_control: '', fm_am_radio: '', subwoofer_support: '' },
    compatibility: { compatible_devices: [], vehicle_fitment: [], unsupported_or_unknown: [] },
    included_items: [],
    warranty: '',
    claims_to_avoid: [],
    review: { status: 'needs_human_review', notes: '' },
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
