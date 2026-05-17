<script setup lang="ts">
import { h, ref, onMounted, onUnmounted } from 'vue'
import { useMessage, useDialog } from 'naive-ui'
import type { DataTableColumn } from 'naive-ui'
import { listSkillsApi, importFromUrlApi, uploadSkillZipApi, deleteSkillApi } from '../../api/skill'
import type { SkillDefinition } from '../../types/api'

const message = useMessage()
const dialog = useDialog()

const skills = ref<SkillDefinition[]>([])
const loading = ref(false)

/* ====== URL Import Modal ====== */
const showUrlModal = ref(false)
const importUrl = ref('')
const importingUrl = ref(false)

/* ====== Download animation state ====== */
const statusMessages = [
  '正在解析技能 URL...',
  '正在连接 GitHub...',
  '正在下载技能文件...',
  '正在解压...',
  '正在写入 workspace...',
]
const currentStatusIndex = ref(0)
const elapsedSeconds = ref(0)
let statusTimer: ReturnType<typeof setInterval> | null = null
let elapsedTimer: ReturnType<typeof setInterval> | null = null
let tipTimer: ReturnType<typeof setInterval> | null = null

const tips = [
  '技能文件托管在 GitHub，下载时间取决于网络状况',
  '首次下载需要安装 npx 工具，后续会快很多',
  '从 skills.sh 导入的技能会自动保持最新',
  '一个技能包就是一个独立的操作指南',
  '导入后所有 Agent 自动获得该技能',
]
const currentTip = ref(tips[0])

const supportedSources = [
  'https://skills.sh/...',
  'https://clawhub.ai/...',
  'https://skillsmp.com/...',
  'https://lobehub.com/...',
  'https://market.lobehub.com/...（LobeHub 直链下载地址）',
  'https://github.com/...',
  'https://modelscope.cn/skills/...',
]

/* ====== ZIP Upload Modal ====== */
const showUploadModal = ref(false)
const uploadFile = ref<File | null>(null)
const uploading = ref(false)

/* ====== Category helpers ====== */
const categoryLabels: Record<string, string> = {
  browser: '浏览器',
  development: '开发工具',
  data: '数据分析',
  automation: '自动化',
  communication: '通讯',
  other: '其他',
}

const categoryColors: Record<string, string> = {
  browser: '#f0a020',
  development: '#2080f0',
  data: '#18a058',
  automation: '#d03050',
  communication: '#8050c0',
  other: '#888',
}

async function fetchSkills() {
  loading.value = true
  try {
    const res = await listSkillsApi()
    if (res.data.code === 200) {
      skills.value = res.data.data ?? []
    }
  } catch {
    message.error('加载技能列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(fetchSkills)

/* ====== Import animation helpers ====== */
function startImportAnimation() {
  currentStatusIndex.value = 0
  elapsedSeconds.value = 0
  currentTip.value = tips[Math.floor(Math.random() * tips.length)]

  // Rotate status messages every 8 seconds
  statusTimer = setInterval(() => {
    currentStatusIndex.value = (currentStatusIndex.value + 1) % statusMessages.length
  }, 8000)

  // Update elapsed time every second
  elapsedTimer = setInterval(() => {
    elapsedSeconds.value++
  }, 1000)

  // Rotate tips every 15 seconds, starting after first 15s
  setTimeout(() => {
    tipTimer = setInterval(() => {
      const next = tips[Math.floor(Math.random() * tips.length)]
      if (next !== currentTip.value) currentTip.value = next
    }, 15000)
  }, 15000)
}

function stopImportAnimation() {
  if (statusTimer) { clearInterval(statusTimer); statusTimer = null }
  if (elapsedTimer) { clearInterval(elapsedTimer); elapsedTimer = null }
  if (tipTimer) { clearInterval(tipTimer); tipTimer = null }
}

onUnmounted(stopImportAnimation)

const formatElapsed = (seconds: number) => {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
}

/* ====== URL Import ====== */
function openUrlImport() {
  importUrl.value = ''
  showUrlModal.value = true
}

async function handleUrlImport() {
  if (!importUrl.value.trim()) {
    message.warning('请输入技能 URL')
    return
  }
  importingUrl.value = true
  startImportAnimation()
  try {
    const res = await importFromUrlApi(importUrl.value.trim())
    if (res.data.code === 200) {
      message.success('技能导入成功')
      showUrlModal.value = false
      await fetchSkills()
    } else {
      message.error(res.data.message || '导入失败')
    }
  } catch {
    message.error('网络异常')
  } finally {
    stopImportAnimation()
    importingUrl.value = false
  }
}

/* ====== ZIP Upload ====== */
function openUpload() {
  uploadFile.value = null
  showUploadModal.value = true
}

function handleFileChange(data: { file: { file?: File; name?: string } }) {
  if (data.file.file) {
    uploadFile.value = data.file.file
  }
}

async function handleUpload() {
  if (!uploadFile.value) {
    message.warning('请选择 ZIP 文件')
    return
  }
  if (!uploadFile.value.name.toLowerCase().endsWith('.zip')) {
    message.warning('仅支持 ZIP 文件')
    return
  }
  uploading.value = true
  try {
    const res = await uploadSkillZipApi(uploadFile.value)
    if (res.data.code === 200) {
      message.success('技能上传成功')
      showUploadModal.value = false
      await fetchSkills()
    } else {
      message.error(res.data.message || '上传失败')
    }
  } catch {
    message.error('网络异常')
  } finally {
    uploading.value = false
  }
}

/* ====== Delete ====== */
function handleDelete(skill: SkillDefinition) {
  dialog.warning({
    title: '确认删除',
    content: `确定要删除技能「${skill.name}」吗？该操作不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await deleteSkillApi(skill.name)
        if (res.data.code === 200) {
          message.success('删除成功')
          await fetchSkills()
        } else {
          message.error(res.data.message || '删除失败')
        }
      } catch {
        message.error('网络异常')
      }
    },
  })
}

/* ====== Columns ====== */
const columns: DataTableColumn<SkillDefinition>[] = [
  {
    title: '名称',
    key: 'name',
    width: 140,
    ellipsis: { tooltip: true },
  },
  {
    title: '描述',
    key: 'description',
    ellipsis: { tooltip: true },
    minWidth: 160,
  },
  {
    title: '类别',
    key: 'category',
    width: 100,
    render: (row) =>
      row.category
        ? h('span', {
            style: `display:inline-block;padding:1px 10px;border-radius:10px;font-size:12px;background:${categoryColors[row.category] || '#888'};color:#fff;line-height:20px;`,
          }, categoryLabels[row.category] || row.category)
        : h('span', { style: 'color:#999;' }, '未分类'),
  },
  {
    title: '操作',
    key: 'actions',
    width: 120,
    render: (row) =>
      h('button', {
        class: 'n-button n-button--tiny',
        style: 'padding:2px 8px;border:none;border-radius:4px;cursor:pointer;background:#d03050;color:#fff;',
        onClick: () => handleDelete(row),
      }, '删除'),
  },
]
</script>

<template>
  <n-space vertical size="large">
    <div style="display: flex; justify-content: space-between; align-items: center;">
      <n-h3 style="margin: 0;">技能管理</n-h3>
      <n-space>
        <n-button @click="openUrlImport">URL 导入</n-button>
        <n-button type="primary" @click="openUpload">上传 ZIP</n-button>
      </n-space>
    </div>

    <n-data-table
      :columns="columns"
      :data="skills"
      :loading="loading"
      :bordered="true"
      :single-line="false"
      :row-key="(row: SkillDefinition) => row.name"
      striped
    />

    <!-- No skills placeholder -->
    <n-empty v-if="!loading && skills.length === 0" description="暂无技能，点击上方按钮导入" />

    <!-- URL Import Modal -->
    <n-modal
      v-model:show="showUrlModal"
      title="URL 导入"
      preset="card"
      style="width: 580px; max-width: 90vw;"
      :mask-closable="false"
      :segmented="true"
      :closable="!importingUrl"
    >
      <!-- Before import — input form -->
      <template v-if="!importingUrl">
        <n-form>
          <div style="margin-bottom: 12px; font-size: 13px; color: #888; line-height: 1.8;">
            支持从以下 URL 来源导入：
            <br>
            <span v-for="src in supportedSources" :key="src" style="display:block;padding-left:12px;">
              {{ src }}
            </span>
          </div>
          <n-form-item label="技能 URL" required>
            <n-input
              v-model:value="importUrl"
              placeholder="https://www.skills.sh/vercel-labs/skills/find-skills"
            />
          </n-form-item>
          <div style="display: flex; gap: 12px; justify-content: flex-end; margin-top: 16px;">
            <n-button @click="showUrlModal = false">取消</n-button>
            <n-button type="primary" @click="handleUrlImport">
              导入
            </n-button>
          </div>
        </n-form>
      </template>

      <!-- During import — animated progress -->
      <template v-else>
        <div style="text-align:center;padding:20px 0;">
          <!-- Large animated spinner -->
          <n-spin size="large" style="display:inline-block;" />

          <!-- Elapsed timer -->
          <div style="margin-top:16px; font-size:24px; font-weight:700; font-variant-numeric:tabular-nums; color:#666;">
            {{ formatElapsed(elapsedSeconds) }}
          </div>

          <!-- Rotating status -->
          <div style="margin-top:12px; font-size:15px; color:#333; transition:opacity 0.3s;">
            <n-gradient-text type="info">
              {{ statusMessages[currentStatusIndex] }}
            </n-gradient-text>
          </div>

          <!-- Indeterminate progress bar -->
          <div style="margin:20px auto; max-width:320px;">
            <n-progress type="line" :percentage="100" :indicator-placement="'inside'" :processing="true" />
          </div>

          <!-- Info box -->
          <div style="background:#f5f7fa;border-radius:8px;padding:12px 16px;text-align:left;font-size:13px;color:#888;margin-top:8px;">
            <div style="font-weight:600;color:#666;margin-bottom:4px;">💡 {{ currentTip }}</div>
            <div style="margin-top:6px;color:#999;font-size:12px;">
              技能文件从 GitHub 下载，5 分钟超时自动取消。<br>
              首次使用 npx 会额外花点时间安装工具。
            </div>
          </div>

          <!-- Cancel hint (disabled since we can't cancel server-side easily) -->
          <div style="margin-top:16px;font-size:12px;color:#bbb;">
            导入中请勿关闭窗口...
          </div>
        </div>
      </template>
    </n-modal>

    <!-- ZIP Upload Modal -->
    <n-modal
      v-model:show="showUploadModal"
      title="上传 ZIP"
      preset="card"
      style="width: 500px; max-width: 90vw;"
      :mask-closable="false"
      :segmented="true"
    >
      <n-form>
        <n-form-item label="选择 ZIP 文件" required>
          <n-upload
            :custom-request="() => {}"
            :show-file-list="false"
            @change="handleFileChange"
            accept=".zip"
          >
            <n-button>{{ uploadFile ? uploadFile.name : '选择文件' }}</n-button>
          </n-upload>
        </n-form-item>
        <div style="font-size: 13px; color: #888; margin-bottom: 16px;">
          ZIP 解压后的一级目录即技能名称，每个一级目录内必须包含 SKILL.md。
        </div>

        <div style="display: flex; gap: 12px; justify-content: flex-end; margin-top: 16px;">
          <n-button @click="showUploadModal = false" :disabled="uploading">取消</n-button>
          <n-button type="primary" :loading="uploading" @click="handleUpload">
            上传
          </n-button>
        </div>
      </n-form>
    </n-modal>
  </n-space>
</template>
