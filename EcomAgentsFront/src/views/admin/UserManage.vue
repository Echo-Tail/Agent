<script setup lang="ts">
import { h, ref, computed, onMounted } from 'vue'
import type { DataTableColumn } from 'naive-ui'
import { useMessage, NButton } from 'naive-ui'
import { useAuthStore } from '../../stores/auth'
import { listUsersApi, toggleUserStatusApi } from '../../api/user'
import { listInviteCodesApi, batchGenerateApi, deleteInviteCodeApi } from '../../api/invite'
import type { UserDTO, InviteCode, UserStats } from '../../types/api'
import { UserRoleLabels, UserStatusLabels } from '../../types/enums'

const message = useMessage()
const authStore = useAuthStore()

const users = ref<UserDTO[]>([])
const loading = ref(false)
const toggling = ref<Set<number>>(new Set())

// Invite code modal
const showInviteModal = ref(false)
const inviteCodes = ref<InviteCode[]>([])
const inviteLoading = ref(false)
const inviteTab = ref<'unused' | 'used'>('unused')
const batchCount = ref(5)
const batchLoading = ref(false)
const deletingCode = ref<Set<string>>(new Set())

const stats = computed<UserStats>(() => {
  const total = users.value.length
  const active = users.value.filter((u) => u.status === 'active').length
  const disabled = users.value.filter((u) => u.status === 'disabled').length
  const admin = users.value.filter((u) => u.role === 'admin').length
  return { total, active, disabled, admin }
})

const unusedCodes = computed(() => inviteCodes.value.filter((c) => !c.used))
const usedCodes = computed(() => inviteCodes.value.filter((c) => c.used))

onMounted(fetchUsers)

async function fetchUsers() {
  loading.value = true
  try {
    const res = await listUsersApi()
    if (res.data.code === 200) {
      users.value = res.data.data ?? []
    }
  } finally {
    loading.value = false
  }
}

async function handleToggleStatus(user: UserDTO) {
  toggling.value.add(user.id)
  try {
    const res = await toggleUserStatusApi(user.id)
    if (res.data.code === 200) {
      message.success(`用户 ${user.username} 状态已切换`)
      await fetchUsers()
    } else {
      message.error(res.data.message || '操作失败')
    }
  } catch {
    message.error('网络异常')
  } finally {
    toggling.value.delete(user.id)
  }
}

// Invite code management
async function openInviteModal() {
  showInviteModal.value = true
  await fetchInviteCodes()
}

async function fetchInviteCodes() {
  inviteLoading.value = true
  try {
    const res = await listInviteCodesApi()
    if (res.data.code === 200) {
      inviteCodes.value = res.data.data ?? []
    }
  } finally {
    inviteLoading.value = false
  }
}

async function handleBatchGenerate() {
  batchLoading.value = true
  try {
    const res = await batchGenerateApi(batchCount.value)
    if (res.data.code === 200) {
      message.success(`已生成 ${batchCount.value} 个邀请码`)
      await fetchInviteCodes()
    } else {
      message.error(res.data.message || '生成失败')
    }
  } catch {
    message.error('网络异常')
  } finally {
    batchLoading.value = false
  }
}

async function handleDeleteCode(code: string) {
  deletingCode.value.add(code)
  try {
    const res = await deleteInviteCodeApi(code)
    if (res.data.code === 200) {
      message.success('已删除')
      await fetchInviteCodes()
    } else {
      message.error(res.data.message || '删除失败')
    }
  } catch {
    message.error('网络异常')
  } finally {
    deletingCode.value.delete(code)
  }
}

function formatDate(dateStr: string) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return d.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

const columns: DataTableColumn<UserDTO>[] = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '用户名', key: 'username', width: 120 },
  {
    title: '角色',
    key: 'role',
    width: 90,
    render: (row: UserDTO) =>
      row.role === 'admin'
        ? h('span', { style: 'display:inline-block;padding:2px 12px;border-radius:10px;font-size:12px;background:#2080f0;color:#fff;line-height:22px;' }, UserRoleLabels[row.role] || row.role)
        : h('span', { style: 'display:inline-block;padding:2px 12px;border-radius:10px;font-size:12px;background:rgba(128,128,128,0.15);color:#888;line-height:22px;' }, UserRoleLabels[row.role] || row.role),
  },
  {
    title: '状态',
    key: 'status',
    width: 90,
    render: (row: UserDTO) =>
      row.status === 'active'
        ? h('span', { style: 'display:inline-block;padding:2px 12px;border-radius:10px;font-size:12px;background:#18a058;color:#fff;line-height:22px;' }, UserStatusLabels[row.status])
        : h('span', { style: 'display:inline-block;padding:2px 12px;border-radius:10px;font-size:12px;background:#d03050;color:#fff;line-height:22px;' }, UserStatusLabels[row.status]),
  },
  { title: '注册时间', key: 'createdAt', width: 110, render: (row: UserDTO) => formatDate(row.createdAt) },
  { title: '邀请码', key: 'inviteCode', width: 120, render: (row: UserDTO) => row.inviteCode || '-' },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    render: (row: UserDTO) => {
      const isSelf = row.id === authStore.currentUser?.id
      const isAdmin = row.role === 'admin'
      const disabled = isSelf || isAdmin
      return row.status === 'active'
        ? h(NButton, {
            size: 'tiny',
            type: 'warning',
            disabled,
            loading: toggling.value.has(row.id),
            onClick: () => handleToggleStatus(row),
          }, { default: () => '禁用' })
        : h(NButton, {
            size: 'tiny',
            type: 'success',
            disabled: isAdmin,
            loading: toggling.value.has(row.id),
            onClick: () => handleToggleStatus(row),
          }, { default: () => '启用' })
    },
  },
]
</script>

<template>
  <n-space vertical size="large">
    <!-- Stats -->
    <n-grid :cols="4" :x-gap="16">
      <n-gi>
        <n-card :bordered="true" size="small">
          <n-statistic label="总用户" :value="stats.total">
            <template #prefix>
              <n-icon color="#C8815F">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z"/>
                </svg>
              </n-icon>
            </template>
          </n-statistic>
        </n-card>
      </n-gi>
      <n-gi>
        <n-card :bordered="true" size="small">
          <n-statistic label="活跃" :value="stats.active">
            <template #prefix>
              <n-icon color="#C8815F">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/>
                </svg>
              </n-icon>
            </template>
          </n-statistic>
        </n-card>
      </n-gi>
      <n-gi>
        <n-card :bordered="true" size="small">
          <n-statistic label="已禁用" :value="stats.disabled">
            <template #prefix>
              <n-icon color="#f59e0b">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
                </svg>
              </n-icon>
            </template>
          </n-statistic>
        </n-card>
      </n-gi>
      <n-gi>
        <n-card :bordered="true" size="small">
          <n-statistic label="管理员" :value="stats.admin">
            <template #prefix>
              <n-icon color="#879EAF">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm0 10.99h7c-.53 4.12-3.28 7.79-7 8.94V12H5V6.3l7-3.11v8.8z"/>
                </svg>
              </n-icon>
            </template>
          </n-statistic>
        </n-card>
      </n-gi>
    </n-grid>

    <!-- Toolbar -->
    <div style="display: flex; justify-content: space-between; align-items: center;">
      <n-h3 style="margin: 0;">用户列表</n-h3>
      <n-button @click="openInviteModal">
        <template #icon>
          <n-icon>
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
              <path d="M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 14H4V8l8 5 8-5v10zm-8-7L4 6h16l-8 5z"/>
            </svg>
          </n-icon>
        </template>
        邀请码管理
      </n-button>
    </div>

    <!-- User Table -->
    <n-data-table
      :columns="columns"
      :data="users"
      :loading="loading"
      :bordered="true"
      :single-line="false"
      :row-key="(row: UserDTO) => row.id"
      striped
    />

    <!-- Invite Code Modal -->
    <n-modal v-model:show="showInviteModal" title="邀请码管理" preset="card" style="width: 640px; max-width: 90vw;">
      <n-space vertical size="large">
        <!-- Batch generate -->
        <n-card title="批量生成" size="small" :bordered="true">
          <n-space align="center">
            <n-input-number v-model:value="batchCount" :min="1" :max="100" style="width: 100px;" />
            <n-button type="primary" :loading="batchLoading" @click="handleBatchGenerate">
              生成
            </n-button>
          </n-space>
        </n-card>

        <!-- Tabs -->
        <n-tabs v-model:value="inviteTab">
          <n-tab-pane name="unused" :tab="`未使用 (${unusedCodes.length})`">
            <n-spin :show="inviteLoading">
              <n-list v-if="unusedCodes.length" style="max-height: 300px; overflow-y: auto;">
                <n-list-item v-for="code in unusedCodes" :key="code.code">
                  <n-thing :title="code.code">
                    <template #description>
                      创建于 {{ formatDate(code.createdAt) }}
                    </template>
                    <template #action>
                      <n-button size="tiny" type="error"
                        :loading="deletingCode.has(code.code)"
                        @click="handleDeleteCode(code.code)">
                        删除
                      </n-button>
                    </template>
                  </n-thing>
                </n-list-item>
              </n-list>
              <n-empty v-else description="没有未使用的邀请码" />
            </n-spin>
          </n-tab-pane>
          <n-tab-pane name="used" :tab="`已使用 (${usedCodes.length})`">
            <n-spin :show="inviteLoading">
              <n-list v-if="usedCodes.length" style="max-height: 300px; overflow-y: auto;">
                <n-list-item v-for="code in usedCodes" :key="code.code">
                  <n-thing :title="code.code">
                    <template #description>
                      使用者：{{ code.usedBy || '-' }} · 使用于 {{ formatDate(code.createdAt) }}
                    </template>
                    <template #action>
                      <n-button size="tiny" type="error"
                        :loading="deletingCode.has(code.code)"
                        @click="handleDeleteCode(code.code)">
                        删除
                      </n-button>
                    </template>
                  </n-thing>
                </n-list-item>
              </n-list>
              <n-empty v-else description="没有已使用的邀请码" />
            </n-spin>
          </n-tab-pane>
        </n-tabs>
      </n-space>
    </n-modal>
  </n-space>
</template>

<style scoped>
.n-data-table :deep(.n-data-table-th) {
  font-weight: 600;
}
</style>
