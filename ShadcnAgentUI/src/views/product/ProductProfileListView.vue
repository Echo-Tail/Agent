<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { toast } from 'sonner'
import PageHeader from '@/components/PageHeader.vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import {
  listProductProfiles,
  createProductProfile,
  createProductProfileFromFile,
  createProductProfileFromAsin,
  deleteProductProfile,
  type ProductProfile,
} from '@/api/product-profiles'
import { Plus, RefreshCw, Loader2, Trash2, Eye, FileText, Upload, Search } from 'lucide-vue-next'

const router = useRouter()
const profiles = ref<ProductProfile[]>([])
const loading = ref(false)
const totalElements = ref(0)
const page = ref(0)
const keyword = ref('')
const filterStatus = ref('__all__')

const createOpen = ref(false)
const creating = ref(false)
const createMethod = ref<'file' | 'asin' | 'manual'>('file')
const createProductName = ref('')
const createMarkdown = ref('')
const createAsin = ref('')
const createFile = ref<File | null>(null)

const statusLabels: Record<string, string> = {
  PENDING_PARSE: '待解析',
  PENDING_CONFIRM: '待确认',
  PENDING_CONFIRM_VERSION: '待确认（新版本）',
  CONFIRMED: '已确认',
  PARSE_FAILED: '解析失败',
}

const statusColors: Record<string, string> = {
  PENDING_PARSE: 'bg-yellow-100 text-yellow-800',
  PENDING_CONFIRM: 'bg-blue-100 text-blue-800',
  PENDING_CONFIRM_VERSION: 'bg-blue-100 text-blue-800',
  CONFIRMED: 'bg-green-100 text-green-800',
  PARSE_FAILED: 'bg-red-100 text-red-800',
}

async function loadProfiles() {
  loading.value = true
  try {
    const res = await listProductProfiles({
      page: page.value,
      size: 20,
      status: filterStatus.value === '__all__' ? undefined : filterStatus.value,
      keyword: keyword.value || undefined,
    })
    profiles.value = res.content ?? []
    totalElements.value = res.page?.totalElements ?? profiles.value.length
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  creating.value = true
  try {
    if (createMethod.value === 'file') {
      if (!createFile.value) {
        toast.error('请选择 Markdown 文件')
        return
      }
      await createProductProfileFromFile(createFile.value)
    } else if (createMethod.value === 'asin') {
      if (!createAsin.value.trim()) {
        toast.error('请输入 ASIN')
        return
      }
      await createProductProfileFromAsin(createAsin.value.trim())
    } else {
      if (!createProductName.value.trim()) {
        toast.error('请填写产品名称')
        return
      }
      await createProductProfile(createProductName.value.trim(), createMarkdown.value || undefined)
    }
    toast.success('产品资料已创建')
    createOpen.value = false
    createProductName.value = ''
    createMarkdown.value = ''
    createAsin.value = ''
    createFile.value = null
    await loadProfiles()
  } catch (e: any) {
    toast.error(e?.response?.data?.message || '创建失败')
  } finally {
    creating.value = false
  }
}

function handleFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  createFile.value = input.files?.[0] || null
}

async function handleDelete(profile: ProductProfile) {
  if (!window.confirm(`删除产品「${profile.productName}」？此操作不可恢复。`)) return
  await deleteProductProfile(profile.id)
  toast.success('已删除')
  await loadProfiles()
}

function viewDetail(id: number) {
  router.push({ name: 'ProductProfileDetail', params: { id } })
}

onMounted(loadProfiles)
</script>

<template>
  <div class="space-y-5">
    <PageHeader title="产品资料" description="维护自有产品档案和默认产品图片">
      <Button size="sm" @click="createOpen = true">
        <Plus class="h-4 w-4 mr-1" />新建产品
      </Button>
    </PageHeader>

    <Card>
      <CardHeader class="pb-3">
        <div class="flex items-center justify-between gap-3">
          <CardTitle class="text-base">产品列表</CardTitle>
          <div class="flex items-center gap-2">
            <Input v-model="keyword" placeholder="搜索产品名称/SKU/品牌" class="w-56" @keyup.enter="loadProfiles" />
            <Button variant="outline" size="icon" :disabled="loading" @click="loadProfiles">
              <RefreshCw :class="['h-4 w-4', loading ? 'animate-spin' : '']" />
            </Button>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <div v-if="loading" class="py-12 text-center text-sm text-muted-foreground">加载中...</div>
        <div v-else-if="profiles.length === 0" class="py-12 text-center text-sm text-muted-foreground">
          暂无产品资料，点击右上角「新建产品」开始
        </div>
        <div v-else class="space-y-2">
          <div
            v-for="profile in profiles"
            :key="profile.id"
            class="flex items-center justify-between gap-3 rounded-md border p-3 transition-colors hover:bg-accent/50"
          >
            <div class="flex-1 min-w-0 cursor-pointer" @click="viewDetail(profile.id)">
              <div class="flex items-center gap-2">
                <FileText class="h-4 w-4 text-muted-foreground" />
                <span class="font-medium truncate">{{ profile.productName }}</span>
              </div>
              <div class="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                <span v-if="profile.brand">{{ profile.brand }}</span>
                <span v-if="profile.sku">SKU: {{ profile.sku }}</span>
                <span v-if="profile.modelNumber">型号: {{ profile.modelNumber }}</span>
                <span>{{ profile.category }}</span>
              </div>
            </div>
            <div class="flex items-center gap-2 shrink-0">
              <Badge :class="statusColors[profile.status] || 'bg-gray-100'">
                {{ statusLabels[profile.status] || profile.status }}
              </Badge>
              <Button variant="ghost" size="icon" class="h-8 w-8 text-muted-foreground" @click="viewDetail(profile.id)">
                <Eye class="h-4 w-4" />
              </Button>
              <Button variant="ghost" size="icon" class="h-8 w-8 text-muted-foreground hover:text-destructive" @click="handleDelete(profile)">
                <Trash2 class="h-4 w-4" />
              </Button>
            </div>
          </div>
        </div>
        <div v-if="totalElements > 0" class="mt-3 text-xs text-muted-foreground">
          共 {{ totalElements }} 个产品
        </div>
      </CardContent>
    </Card>

    <Dialog v-model:open="createOpen">
      <DialogContent class="sm:max-w-[620px]">
        <DialogHeader>
          <DialogTitle>新建产品资料</DialogTitle>
        </DialogHeader>

        <div class="flex gap-1 rounded-md border p-1 bg-muted/30 mb-3">
          <button
            :class="['flex-1 px-3 py-2 text-sm rounded-md transition-colors', createMethod === 'file' ? 'bg-background shadow-sm font-medium' : 'hover:bg-background/50']"
            @click="createMethod = 'file'"
          >
            <Upload class="h-4 w-4 inline mr-1" />上传 Markdown 文件
          </button>
          <button
            :class="['flex-1 px-3 py-2 text-sm rounded-md transition-colors', createMethod === 'asin' ? 'bg-background shadow-sm font-medium' : 'hover:bg-background/50']"
            @click="createMethod = 'asin'"
          >
            <Search class="h-4 w-4 inline mr-1" />通过 ASIN 导入
          </button>
          <button
            :class="['flex-1 px-3 py-2 text-sm rounded-md transition-colors', createMethod === 'manual' ? 'bg-background shadow-sm font-medium' : 'hover:bg-background/50']"
            @click="createMethod = 'manual'"
          >
            <FileText class="h-4 w-4 inline mr-1" />手动输入
          </button>
        </div>

        <!-- 方法 1：上传 Markdown 文件 -->
        <div v-if="createMethod === 'file'" class="space-y-3">
          <div class="rounded-md border border-dashed bg-muted/20 p-6 text-center">
            <Upload class="h-8 w-8 mx-auto mb-2 text-muted-foreground" />
            <p class="text-sm text-muted-foreground mb-2">选择本地的 Markdown 产品参数文档</p>
            <Input type="file" accept=".md,.markdown,.txt" @change="handleFileSelect" />
            <p v-if="createFile" class="mt-2 text-xs text-muted-foreground">
              已选择: {{ createFile.name }} ({{ (createFile.size / 1024).toFixed(1) }} KB)
            </p>
            <p class="mt-2 text-xs text-muted-foreground">上传后系统将通过 LLM 自动解析为结构化产品参数</p>
          </div>
        </div>

        <!-- 方法 2：通过 ASIN 导入 -->
        <div v-if="createMethod === 'asin'" class="space-y-3">
          <div>
            <label class="text-sm font-medium">Amazon ASIN <span class="text-destructive">*</span></label>
            <Input v-model="createAsin" placeholder="B0XXXXXXXXXX" class="mt-1 font-mono" />
            <p class="mt-1 text-xs text-muted-foreground">输入 Amazon US 站点的 ASIN，系统将通过 Bright Data 采集商品信息并用 LLM 解析为产品参数。</p>
          </div>
        </div>

        <!-- 方法 3：手动输入 -->
        <div v-if="createMethod === 'manual'" class="space-y-3">
          <div>
            <label class="text-sm font-medium">产品名称 <span class="text-destructive">*</span></label>
            <Input v-model="createProductName" placeholder="例如 7寸车载CarPlay显示屏" />
          </div>
          <div>
            <label class="text-sm font-medium">产品参数 Markdown <span class="text-muted-foreground text-xs">可选</span></label>
            <Textarea v-model="createMarkdown" class="mt-1 min-h-[200px] font-mono text-xs" placeholder="支持自由格式 Markdown，上传后系统会自动尝试解析为结构化 JSON。&#10;&#10;建议包含：产品名称、品牌、型号、SKU、屏幕尺寸、外形规格、连接方式、接口类型、功能列表、兼容车型、包装清单等。" />
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" @click="createOpen = false">取消</Button>
          <Button :disabled="creating" @click="handleCreate">
            <Loader2 v-if="creating" class="h-4 w-4 mr-1 animate-spin" />{{ creating ? '正在解析...' : '创建' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
