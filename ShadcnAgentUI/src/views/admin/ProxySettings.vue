<script setup lang="ts">
import { onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Field,
  FieldContent,
  FieldDescription,
  FieldGroup,
  FieldLabel,
} from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import {
  detectProxyApi,
  getProxySettingsApi,
  testProxyApi,
  updateProxySettingsApi,
  type ProxyCandidate,
} from '@/api/proxy-settings'
import {
  CheckCircle2,
  Loader2,
  Network,
  RefreshCw,
  Save,
  Search,
  XCircle,
} from 'lucide-vue-next'
import { toast } from 'vue-sonner'

const enabled = ref(false)
const proxyUrl = ref('')
const loading = ref(true)
const saving = ref(false)
const detecting = ref(false)
const testing = ref(false)
const candidates = ref<ProxyCandidate[]>([])
const suggestion = ref<string | null>(null)
const testResult = ref<{ success: boolean; message: string; durationMs: number } | null>(null)

async function load() {
  loading.value = true
  try {
    const settings = await getProxySettingsApi()
    enabled.value = settings.enabled
    proxyUrl.value = settings.proxyUrl ?? ''
  } catch {
    toast.error('代理设置加载失败')
  } finally {
    loading.value = false
  }
}

async function detect() {
  detecting.value = true
  testResult.value = null
  try {
    const result = await detectProxyApi()
    candidates.value = result.candidates
    suggestion.value = result.suggestedProxyUrl
    if (result.suggestedProxyUrl) {
      proxyUrl.value = result.suggestedProxyUrl
      toast.success('已发现可用的本机代理')
    } else {
      toast.info('未发现可用的系统代理，请手动填写')
    }
  } catch {
    toast.error('系统代理探测失败')
  } finally {
    detecting.value = false
  }
}

async function testConnection() {
  if (!proxyUrl.value.trim()) {
    toast.error('请先填写代理地址')
    return
  }
  testing.value = true
  try {
    const result = await testProxyApi(proxyUrl.value.trim())
    testResult.value = result
    if (result.success) toast.success(result.message)
    else toast.error(result.message)
  } catch {
    toast.error('代理连接测试失败')
  } finally {
    testing.value = false
  }
}

async function save() {
  if (enabled.value && !proxyUrl.value.trim()) {
    toast.error('启用代理时必须填写代理地址')
    return
  }
  saving.value = true
  try {
    const settings = await updateProxySettingsApi(enabled.value, proxyUrl.value.trim() || null)
    enabled.value = settings.enabled
    proxyUrl.value = settings.proxyUrl ?? ''
    toast.success('代理设置已保存')
  } catch {
    toast.error('代理设置保存失败')
  } finally {
    saving.value = false
  }
}

function useCandidate(candidate: ProxyCandidate) {
  proxyUrl.value = candidate.proxyUrl
  testResult.value = null
}

onMounted(load)
</script>

<template>
  <div class="flex flex-col gap-6">
    <PageHeader title="代理设置" description="配置图片供应商及远程图片下载使用的出站 HTTP 代理">
      <Button variant="outline" :disabled="detecting" @click="detect">
        <Loader2 v-if="detecting" data-icon="inline-start" class="animate-spin" />
        <Search v-else data-icon="inline-start" />
        自动探测
      </Button>
    </PageHeader>

    <template v-if="loading">
      <Skeleton class="h-72 w-full" />
    </template>

    <template v-else>
      <Alert v-if="suggestion">
        <Network />
        <AlertTitle>发现可用代理</AlertTitle>
        <AlertDescription>
          已检测到 {{ suggestion }}，并自动填入下方配置。保存后图片供应商请求将使用此代理。
        </AlertDescription>
      </Alert>

      <Card>
        <CardHeader>
          <CardTitle>出站 HTTP 代理</CardTitle>
          <CardDescription>
            支持 HTTP CONNECT 代理。推荐使用本机地址，例如 http://127.0.0.1:15236。
          </CardDescription>
        </CardHeader>
        <CardContent>
          <FieldGroup>
            <Field orientation="horizontal">
              <FieldContent>
                <FieldLabel for="proxy-enabled">启用代理</FieldLabel>
                <FieldDescription>
                  开启后，OpenAI、百炼图片接口以及供应商图片下载将通过代理访问。
                </FieldDescription>
              </FieldContent>
              <Switch id="proxy-enabled" v-model="enabled" />
            </Field>

            <Field :data-invalid="enabled && !proxyUrl.trim()">
              <FieldLabel for="proxy-url">代理地址</FieldLabel>
              <Input
                id="proxy-url"
                v-model="proxyUrl"
                placeholder="http://127.0.0.1:15236"
                autocomplete="off"
                :aria-invalid="enabled && !proxyUrl.trim()"
                @input="testResult = null"
              />
              <FieldDescription>
                未填写协议时不会自动保存，请使用完整的 http://主机:端口 格式。
              </FieldDescription>
            </Field>
          </FieldGroup>
        </CardContent>
        <CardFooter class="flex flex-wrap justify-end gap-2">
          <Button variant="outline" :disabled="testing" @click="testConnection">
            <Loader2 v-if="testing" data-icon="inline-start" class="animate-spin" />
            <RefreshCw v-else data-icon="inline-start" />
            测试连接
          </Button>
          <Button :disabled="saving" @click="save">
            <Loader2 v-if="saving" data-icon="inline-start" class="animate-spin" />
            <Save v-else data-icon="inline-start" />
            保存设置
          </Button>
        </CardFooter>
      </Card>

      <Alert v-if="testResult" :variant="testResult.success ? 'default' : 'destructive'">
        <CheckCircle2 v-if="testResult.success" />
        <XCircle v-else />
        <AlertTitle>{{ testResult.success ? '连接正常' : '连接失败' }}</AlertTitle>
        <AlertDescription>
          {{ testResult.message }}（耗时 {{ testResult.durationMs }} ms）
        </AlertDescription>
      </Alert>

      <Card v-if="candidates.length">
        <CardHeader>
          <CardTitle>探测结果</CardTitle>
          <CardDescription>点击候选地址可快速填入代理配置。</CardDescription>
        </CardHeader>
        <CardContent class="flex flex-col gap-3">
          <Button
            v-for="candidate in candidates"
            :key="candidate.proxyUrl"
            type="button"
            variant="outline"
            class="h-auto justify-between p-3 text-left"
            @click="useCandidate(candidate)"
          >
            <span class="flex flex-col gap-1">
              <span class="font-medium">{{ candidate.proxyUrl }}</span>
              <span class="text-sm text-muted-foreground">{{ candidate.source }}</span>
            </span>
            <Badge :variant="candidate.reachable ? 'secondary' : 'outline'">
              {{ candidate.reachable ? '可用' : '不可用' }}
            </Badge>
          </Button>
        </CardContent>
      </Card>
    </template>
  </div>
</template>
