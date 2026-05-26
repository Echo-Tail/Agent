<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import SearchInput from '@/components/SearchInput.vue'
import { Badge } from '@/components/ui/badge'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from '@/components/ui/sheet'
import {
  createTicketApi,
  listMyTicketsApi,
  listTicketChangesApi,
  updateTicketApi,
} from '@/api/ticket'
import { uploadFileApi } from '@/api/file'
import { useAuthStore } from '@/stores/auth'
import {
  ticketStatusOptions,
  ticketPriorityOptions,
  allAffectedMenuOptions,
  submitterAffectedMenuOptions,
} from '@/types/ticket'
import type { Ticket, TicketChangeRecord, TicketRequest } from '@/types/ticket'
import type { FileRecord } from '@/types/api'
import {
  Plus,
  Ticket as TicketIcon,
  FileText,
  Loader2,
  Pencil,
  X,
  Upload,
  ChevronRight,
} from 'lucide-vue-next'
import { toast } from 'sonner'

const { t, locale } = useI18n()
const auth = useAuthStore()
const loading = ref(false)
const submitting = ref(false)
const tickets = ref<Ticket[]>([])
const selectedTicket = ref<Ticket | null>(null)
const changes = ref<TicketChangeRecord[]>([])
const showForm = ref(false)
const showDetail = ref(false)
const editingId = ref<number | null>(null)
const uploadInput = ref<HTMLInputElement | null>(null)

const filters = reactive({
  status: null as string | null,
  affectedMenu: null as string | null,
  priority: null as string | null,
  title: '',
})

const form = reactive<TicketRequest>({
  title: '',
  affectedMenu: 'OTHER',
  priority: 'MEDIUM',
  content: '',
  attachmentIds: [],
})
const attachments = ref<FileRecord[]>([])

const canEditSelected = computed(() => selectedTicket.value && selectedTicket.value.status !== 'COMPLETED')
const menuOptions = computed(() => auth.isAdmin ? allAffectedMenuOptions : submitterAffectedMenuOptions)

async function fetchTickets() {
  loading.value = true
  try {
    const params = Object.fromEntries(
      Object.entries(filters).filter(([, v]) => v !== null && v !== ''),
    )
    tickets.value = (await listMyTicketsApi(params as never)) ?? []
  } catch {
    toast.error(t('error.loadTicketsFailed'))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  resetForm()
  showForm.value = true
}

function openEdit(ticket: Ticket) {
  editingId.value = ticket.id
  form.title = ticket.title
  form.affectedMenu = ticket.affectedMenu
  form.priority = ticket.priority
  form.content = ticket.content
  attachments.value = [...ticket.attachments]
  form.attachmentIds = attachments.value.map((file) => file.id)
  showForm.value = true
}

async function openDetail(ticket: Ticket) {
  selectedTicket.value = ticket
  showDetail.value = true
  changes.value = []
  try {
    changes.value = (await listTicketChangesApi(ticket.id)) ?? []
  } catch {
    toast.error(t('error.loadChangesFailed'))
  }
}

async function submitForm() {
  if (!form.title.trim() || !form.content.trim() || !form.affectedMenu || !form.priority) {
    toast.warning(t('myTickets.form.fillRequired'))
    return
  }
  submitting.value = true
  try {
    const req = { ...form, title: form.title.trim(), content: form.content.trim() }
    const data = editingId.value ? await updateTicketApi(editingId.value, req) : await createTicketApi(req)
    toast.success(editingId.value ? t('toast.updateSuccess') : t('toast.createSuccess'))
    showForm.value = false
    await fetchTickets()
    if (selectedTicket.value?.id === data.id) selectedTicket.value = data
  } catch { /* interceptor handles toast */ } finally {
    submitting.value = false
  }
}

async function handleFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (!files.length) return
  const existingSize = attachments.value.reduce((sum, file) => sum + file.fileSize, 0)
  const newSize = files.reduce((sum, file) => sum + file.size, 0)
  if (existingSize + newSize > 20 * 1024 * 1024) {
    toast.warning(t('error.uploadFailed'))
    return
  }
  submitting.value = true
  try {
    for (const file of files) {
      const fileData = await uploadFileApi(file)
      attachments.value.push(fileData)
    }
    form.attachmentIds = attachments.value.map((file) => file.id)
  } catch { /* interceptor handles toast */ } finally {
    submitting.value = false
  }
}

function removeAttachment(id: number) {
  attachments.value = attachments.value.filter((file) => file.id !== id)
  form.attachmentIds = attachments.value.map((file) => file.id)
}

function resetForm() {
  form.title = ''
  form.affectedMenu = 'OTHER'
  form.priority = 'MEDIUM'
  form.content = ''
  form.attachmentIds = []
  attachments.value = []
}

function formatTime(value?: string) {
  return value ? new Date(value).toLocaleString(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US') : '-'
}

function formatFileSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

onMounted(fetchTickets)
</script>

<template>
  <div class="space-y-6">
    <PageHeader :title="$t('pageTitle.myTickets')" :description="$t('pageDesc.myTickets')">
      <Button @click="openCreate">
        <Plus class="mr-2 h-4 w-4" />{{ $t('myTickets.create') }}
      </Button>
    </PageHeader>

    <!-- Filters -->
    <div class="flex items-center gap-2 flex-wrap">
      <Select v-model="filters.status">
        <SelectTrigger class="w-[120px]">
          <SelectValue :placeholder="$t('ticketManage.filters.status')" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem v-for="opt in ticketStatusOptions" :key="opt.value" :value="opt.value">
            {{ $t(opt.label) }}
          </SelectItem>
        </SelectContent>
      </Select>
      <Select v-model="filters.affectedMenu">
        <SelectTrigger class="w-[150px]">
          <SelectValue :placeholder="$t('ticketManage.filters.affectedMenu')" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem v-for="opt in menuOptions" :key="opt.value" :value="opt.value">
            {{ $t(opt.label) }}
          </SelectItem>
        </SelectContent>
      </Select>
      <Select v-model="filters.priority">
        <SelectTrigger class="w-[110px]">
          <SelectValue :placeholder="$t('ticketManage.filters.priority')" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem v-for="opt in ticketPriorityOptions" :key="opt.value" :value="opt.value">
            {{ $t(opt.label) }}
          </SelectItem>
        </SelectContent>
      </Select>
      <SearchInput id="my-tickets-title-filter" name="my-tickets-title-filter" v-model="filters.title" :placeholder="$t('ticketManage.filters.titleSearch')" input-class="w-[200px] h-9 pl-8" />
      <Button variant="secondary" size="default" class="w-[86px]" @click="fetchTickets" :disabled="loading">
        <Loader2 v-if="loading" class="mr-1 h-3 w-3 animate-spin" />
        {{ $t('common.search') }}
      </Button>
    </div>

    <!-- Ticket List -->
    <EmptyState v-if="!loading && tickets.length === 0" :icon="TicketIcon" :title="$t('myTickets.noTickets')" :description="$t('myTickets.noTicketsDesc')" />

    <div v-else class="border border-border rounded-lg divide-y divide-border">
      <div
        v-for="ticket in tickets"
        :key="ticket.id"
        class="flex items-center gap-3 px-4 py-3 hover:bg-muted/30 transition-colors cursor-pointer"
        @click="openDetail(ticket)"
      >
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2">
            <span class="text-xs text-muted-foreground font-mono shrink-0">{{ ticket.ticketNumber }}</span>
            <span class="text-sm font-medium truncate">{{ ticket.title }}</span>
          </div>
          <div class="flex items-center gap-2 mt-1">
            <Badge
              variant="outline"
              class="text-xs"
              :class="{
                'text-amber-600 border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-950': ticket.status === 'PENDING',
                'text-blue-600 border-blue-200 bg-blue-50 dark:border-blue-800 dark:bg-blue-950': ticket.status === 'IN_PROGRESS',
                'text-green-600 border-green-200 bg-green-50 dark:border-green-800 dark:bg-green-950': ticket.status === 'COMPLETED',
              }"
            >
              {{ $t('ticket.status.' + ticket.status) }}
            </Badge>
            <span class="text-xs text-muted-foreground">{{ $t('ticket.menu.' + ticket.affectedMenu) }}</span>
            <span class="text-xs text-muted-foreground">{{ $t('ticket.priority.' + ticket.priority) }}</span>
          </div>
        </div>
        <div class="text-xs text-muted-foreground shrink-0">{{ formatTime(ticket.createdAt) }}</div>
        <ChevronRight class="h-4 w-4 text-muted-foreground shrink-0" />
      </div>
    </div>

    <!-- Create/Edit Form Modal -->
    <Dialog :open="showForm" @update:open="showForm = $event">
      <DialogContent class="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{{ editingId ? $t('myTickets.edit') : $t('myTickets.create') }}</DialogTitle>
        </DialogHeader>
        <div class="space-y-4">
          <div class="space-y-2">
            <label for="ticket-title" class="text-sm font-medium">{{ $t('myTickets.form.title') }} <span class="text-destructive">*</span></label>
            <Input id="ticket-title" name="ticket-title" v-model="form.title" maxlength="120" :placeholder="$t('myTickets.form.titlePlaceholder')" />
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div class="space-y-2">
              <label for="ticket-affected-menu" class="text-sm font-medium">{{ $t('myTickets.form.affectedMenu') }} <span class="text-destructive">*</span></label>
              <Select v-model="form.affectedMenu">
                <SelectTrigger id="ticket-affected-menu" name="ticket-affected-menu" class="w-[120px]">
                  <SelectValue :placeholder="$t('myTickets.form.affectedMenuPlaceholder')" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem v-for="opt in menuOptions" :key="opt.value" :value="opt.value">
                    {{ $t(opt.label) }}
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div class="space-y-2">
              <label for="ticket-priority" class="text-sm font-medium">{{ $t('myTickets.form.priority') }} <span class="text-destructive">*</span></label>
              <Select v-model="form.priority">
                <SelectTrigger id="ticket-priority" name="ticket-priority">
                  <SelectValue :placeholder="$t('myTickets.form.priorityPlaceholder')" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem v-for="opt in ticketPriorityOptions" :key="opt.value" :value="opt.value">
                    {{ $t(opt.label) }}
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <div class="space-y-2">
            <label for="ticket-content" class="text-sm font-medium">{{ $t('myTickets.form.content') }} <span class="text-destructive">*</span></label>
            <textarea
              id="ticket-content"
              name="ticket-content"
              v-model="form.content"
              :placeholder="$t('myTickets.form.contentPlaceholder')"
              class="flex min-h-[120px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            />
          </div>
          <div class="space-y-2">
            <label for="ticket-attachments" class="text-sm font-medium">{{ $t('myTickets.form.attachments') }}</label>
            <div class="flex flex-col gap-2">
              <input id="ticket-attachments" name="ticket-attachments" ref="uploadInput" type="file" multiple class="hidden" @change="handleFiles" />
              <Button variant="outline" size="sm" class="w-fit" @click="uploadInput?.click()" :disabled="submitting">
                <Upload class="mr-1 h-3.5 w-3.5" />{{ $t('myTickets.form.selectFiles') }}
              </Button>
              <div v-if="attachments.length" class="flex flex-wrap gap-1">
                <Badge
                  v-for="file in attachments"
                  :key="file.id"
                  variant="secondary"
                  class="gap-1 pr-1"
                >
                  <FileText class="h-3 w-3" />
                  {{ file.originalName }} ({{ formatFileSize(file.fileSize) }})
                  <button class="ml-1 hover:text-destructive" @click="removeAttachment(file.id)">
                    <X class="h-3 w-3" />
                  </button>
                </Badge>
              </div>
            </div>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="showForm = false">{{ $t('common.cancel') }}</Button>
          <Button :loading="submitting" @click="submitForm">{{ $t('common.submit') }}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Detail Sheet -->
    <Sheet :open="showDetail" @update:open="showDetail = $event">
      <SheetContent side="right" class="sm:max-w-lg w-full overflow-y-auto">
        <SheetHeader v-if="selectedTicket" class="mb-4">
          <SheetTitle>{{ selectedTicket.ticketNumber }}</SheetTitle>
          <SheetDescription>{{ selectedTicket.title }}</SheetDescription>
        </SheetHeader>

        <template v-if="selectedTicket">
          <div class="space-y-4">
            <!-- Basic Info -->
            <div class="border border-border rounded-lg divide-y divide-border text-sm">
              <div class="flex justify-between px-3 py-2">
                <span class="text-muted-foreground">{{ $t('myTickets.detail.status') }}</span>
                <Badge
                  variant="outline"
                  class="text-xs"
                  :class="{
                    'text-amber-600 border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-950': selectedTicket.status === 'PENDING',
                    'text-blue-600 border-blue-200 bg-blue-50 dark:border-blue-800 dark:bg-blue-950': selectedTicket.status === 'IN_PROGRESS',
                    'text-green-600 border-green-200 bg-green-50 dark:border-green-800 dark:bg-green-950': selectedTicket.status === 'COMPLETED',
                  }"
                >
                  {{ $t('ticket.status.' + selectedTicket.status) }}
                </Badge>
              </div>
              <div class="flex justify-between px-3 py-2">
                <span class="text-muted-foreground">{{ $t('myTickets.detail.affectedMenu') }}</span>
                <span>{{ $t('ticket.menu.' + selectedTicket.affectedMenu) }}</span>
              </div>
              <div class="flex justify-between px-3 py-2">
                <span class="text-muted-foreground">{{ $t('myTickets.detail.priority') }}</span>
                <span>{{ $t('ticket.priority.' + selectedTicket.priority) }}</span>
              </div>
              <div class="flex justify-between px-3 py-2">
                <span class="text-muted-foreground">{{ $t('myTickets.detail.createdAt') }}</span>
                <span>{{ formatTime(selectedTicket.createdAt) }}</span>
              </div>
              <div v-if="selectedTicket.handlerName" class="flex justify-between px-3 py-2">
                <span class="text-muted-foreground">{{ $t('myTickets.detail.handler') }}</span>
                <span>{{ selectedTicket.handlerName }}</span>
              </div>
              <div v-if="selectedTicket.handlingNote" class="flex justify-between px-3 py-2">
                <span class="text-muted-foreground">{{ $t('myTickets.detail.handlingNote') }}</span>
                <span>{{ selectedTicket.handlingNote }}</span>
              </div>
            </div>

            <!-- Content -->
            <div>
              <h4 class="text-sm font-medium mb-1">{{ $t('myTickets.detail.content') }}</h4>
              <p class="text-sm text-muted-foreground whitespace-pre-wrap rounded-md border border-border p-3">
                {{ selectedTicket.content }}
              </p>
            </div>

            <!-- Attachments -->
            <div v-if="selectedTicket.attachments.length">
              <h4 class="text-sm font-medium mb-1">{{ $t('myTickets.detail.attachments') }}</h4>
              <div class="flex flex-col gap-1">
                <a
                  v-for="file in selectedTicket.attachments"
                  :key="file.id"
                  :href="`/v1/files/${file.id}/download`"
                  target="_blank"
                  class="text-sm text-primary hover:underline flex items-center gap-1"
                >
                  <FileText class="h-3.5 w-3.5" />
                  {{ file.originalName }} ({{ formatFileSize(file.fileSize) }})
                </a>
              </div>
            </div>

            <!-- Edit button -->
            <Button v-if="canEditSelected" variant="outline" class="w-full" @click="showDetail = false; openEdit(selectedTicket)">
              <Pencil class="mr-1 h-4 w-4" />{{ $t('myTickets.detail.editTicket') }}
            </Button>

            <!-- Change History -->
            <div v-if="changes.length">
              <h4 class="text-sm font-medium mb-2">{{ $t('myTickets.detail.changeHistory') }}</h4>
              <div class="space-y-2">
                <div
                  v-for="record in changes"
                  :key="record.id"
                  class="text-xs text-muted-foreground border-l-2 border-border pl-3 py-1"
                >
                  <div>
                    <span class="font-medium text-foreground">{{ $t('ticketManage.detail.changedBy', { name: record.changedByName || record.changedBy, field: record.fieldName }) }}</span>
                  </div>
                  <div>{{ $t('ticketManage.detail.fromTo', { oldVal: record.oldValue || $t('common.noData'), newVal: record.newValue || $t('common.noData') }) }}</div>
                  <div class="text-[10px] text-muted-foreground/60">{{ formatTime(record.changedAt) }}</div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </SheetContent>
    </Sheet>
  </div>
</template>
