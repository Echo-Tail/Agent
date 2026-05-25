<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import SearchInput from '@/components/SearchInput.vue'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
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
  completeTicketApi,
  listAdminTicketsApi,
  listTicketChangesApi,
  startTicketApi,
} from '@/api/ticket'
import type { Ticket, TicketChangeRecord, TicketFilters } from '@/types/ticket'
import {
  affectedMenuKeys,
  ticketPriorityKeys,
  ticketPriorityOptions,
  ticketStatusKeys,
  ticketStatusOptions,
  submitterAffectedMenuOptions as affectedMenuOptions,
} from '@/types/ticket'
import {
  Loader2,
  Eye,
  Play,
  CheckCircle,
  TicketCheck,
} from 'lucide-vue-next'
import { toast } from 'sonner'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const loading = ref(false)
const handling = ref(false)
const tickets = ref<Ticket[]>([])
const selectedTicket = ref<Ticket | null>(null)
const changes = ref<TicketChangeRecord[]>([])
const showDetail = ref(false)
const showComplete = ref(false)
const handlingNote = ref('')

const filters = reactive<TicketFilters>({
  status: null,
  affectedMenu: null,
  priority: null,
  title: '',
  submitterId: null,
})

async function fetchTickets() {
  loading.value = true
  try {
    const params = Object.fromEntries(
      Object.entries(filters).filter(([, v]) => v !== null && v !== '' && v !== undefined),
    )
    tickets.value = (await listAdminTicketsApi(params as never)) ?? []
  } catch {
    toast.error(t('error.loadTicketsFailed'))
  } finally {
    loading.value = false
  }
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

async function start(ticket: Ticket) {
  handling.value = true
  try {
    const data = await startTicketApi(ticket.id)
    toast.success(t('toast.updateSuccess'))
    await fetchTickets()
    selectedTicket.value = data
  } catch { /* interceptor handles toast */ } finally {
    handling.value = false
  }
}

function openComplete(ticket: Ticket) {
  selectedTicket.value = ticket
  handlingNote.value = ticket.handlingNote ?? ''
  showComplete.value = true
}

async function complete() {
  if (!selectedTicket.value || !handlingNote.value.trim()) {
    toast.warning(t('ticketManage.detail.handlingNoteRequired'))
    return
  }
  handling.value = true
  try {
    const data = await completeTicketApi(selectedTicket.value.id, handlingNote.value.trim())
    toast.success(t('toast.updateSuccess'))
    showComplete.value = false
    await fetchTickets()
    selectedTicket.value = data
  } catch { /* interceptor handles toast */ } finally {
    handling.value = false
  }
}

function formatTime(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN') : '-'
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
    <PageHeader :title="$t('ticketManage.title')" :description="$t('pageDesc.ticketManage')" />

    <!-- Filters -->
    <div class="flex items-center gap-2 flex-wrap">
      <Select v-model="filters.status">
        <SelectTrigger class="w-[120px]"><SelectValue :placeholder="$t('ticketManage.filters.status')" /></SelectTrigger>
        <SelectContent>
          <SelectItem v-for="opt in ticketStatusOptions" :key="opt.value" :value="opt.value">{{ $t(opt.label) }}</SelectItem>
        </SelectContent>
      </Select>
      <Select v-model="filters.affectedMenu">
        <SelectTrigger class="w-[150px]"><SelectValue :placeholder="$t('ticketManage.filters.affectedMenu')" /></SelectTrigger>
        <SelectContent>
          <SelectItem v-for="opt in affectedMenuOptions" :key="opt.value" :value="opt.value">{{ $t(opt.label) }}</SelectItem>
        </SelectContent>
      </Select>
      <Select v-model="filters.priority">
        <SelectTrigger class="w-[110px]"><SelectValue :placeholder="$t('ticketManage.filters.priority')" /></SelectTrigger>
        <SelectContent>
          <SelectItem v-for="opt in ticketPriorityOptions" :key="opt.value" :value="opt.value">{{ $t(opt.label) }}</SelectItem>
        </SelectContent>
      </Select>
      <Input id="admin-ticket-submitter-filter" name="admin-ticket-submitter-filter" :model-value="filters.submitterId?.toString() ?? ''" type="number" :placeholder="$t('common.submitter')" class="w-[120px] h-9" @update:model-value="(v: string | number) => { const n = Number(v); filters.submitterId = isNaN(n) ? null : n }" />
      <SearchInput id="admin-ticket-title-filter" name="admin-ticket-title-filter" :model-value="filters.title ?? ''" @update:model-value="v => filters.title = v" :placeholder="$t('ticketManage.filters.titleSearch')" input-class="w-[200px] h-9 pl-8" />
      <Button variant="secondary" size="default" class="w-[86px]" :disabled="loading" @click="fetchTickets">
        <Loader2 v-if="loading" class="mr-1 h-3 w-3 animate-spin" />
        {{ $t('common.search') }}
      </Button>
    </div>

    <!-- Ticket list -->
    <div v-if="loading && tickets.length === 0" class="space-y-2">
      <Skeleton v-for="i in 5" :key="i" class="h-12 w-full" />
    </div>

    <EmptyState v-else-if="tickets.length === 0" :icon="TicketCheck" :title="$t('common.noData')" :description="$t('ticketManage.allProcessed')" />

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
              {{ $t(ticketStatusKeys[ticket.status]) }}
            </Badge>
            <span class="text-xs text-muted-foreground">{{ ticket.submitterName || `#${ticket.submitterId}` }}</span>
            <span class="text-xs text-muted-foreground">{{ $t(affectedMenuKeys[ticket.affectedMenu]) }}</span>
            <span class="text-xs text-muted-foreground">{{ $t(ticketPriorityKeys[ticket.priority]) }}</span>
          </div>
        </div>
        <div class="flex items-center gap-1 shrink-0">
          <Button v-if="ticket.status === 'PENDING'" variant="default" size="sm" class="h-7 text-xs" @click.stop="start(ticket)">
            <Play class="mr-1 h-3 w-3" />{{ $t('ticketManage.detail.startProcess') }}
          </Button>
          <Button v-if="ticket.status === 'IN_PROGRESS'" variant="default" size="sm" class="h-7 text-xs" @click.stop="openComplete(ticket)">
            <CheckCircle class="mr-1 h-3 w-3" />{{ $t('ticketManage.detail.complete') }}
          </Button>
          <Button variant="ghost" size="icon" class="h-7 w-7" @click.stop="openDetail(ticket)">
            <Eye class="h-3.5 w-3.5" />
          </Button>
        </div>
      </div>
    </div>

    <!-- Detail Sheet -->
    <Sheet :open="showDetail" @update:open="showDetail = $event">
      <SheetContent side="right" class="sm:max-w-lg w-full overflow-y-auto">
        <SheetHeader v-if="selectedTicket" class="mb-4">
          <SheetTitle>{{ selectedTicket.ticketNumber }}</SheetTitle>
          <SheetDescription>{{ selectedTicket.title }}</SheetDescription>
        </SheetHeader>

        <template v-if="selectedTicket">
          <div class="space-y-4">
            <div class="border border-border rounded-lg divide-y divide-border text-sm">
              <div class="flex justify-between px-3 py-2">
                <span class="text-muted-foreground">{{ $t('ticketManage.detail.status') }}</span>
                <Badge
                  variant="outline"
                  class="text-xs"
                  :class="{
                    'text-amber-600 border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-950': selectedTicket.status === 'PENDING',
                    'text-blue-600 border-blue-200 bg-blue-50 dark:border-blue-800 dark:bg-blue-950': selectedTicket.status === 'IN_PROGRESS',
                    'text-green-600 border-green-200 bg-green-50 dark:border-green-800 dark:bg-green-950': selectedTicket.status === 'COMPLETED',
                  }"
                >
                  {{ $t(ticketStatusKeys[selectedTicket.status]) }}
                </Badge>
              </div>
              <div class="flex justify-between px-3 py-2">
                <span class="text-muted-foreground">{{ $t('ticketManage.detail.submitter') }}</span>
                <span>{{ selectedTicket.submitterName || selectedTicket.submitterId }}</span>
              </div>
              <div class="flex justify-between px-3 py-2">
                <span class="text-muted-foreground">{{ $t('ticketManage.detail.handler') }}</span>
                <span>{{ selectedTicket.handlerName || '-' }}</span>
              </div>
              <div class="flex justify-between px-3 py-2">
                <span class="text-muted-foreground">{{ $t('ticketManage.detail.affectedMenu') }}</span>
                <span>{{ $t(affectedMenuKeys[selectedTicket.affectedMenu]) }}</span>
              </div>
              <div class="flex justify-between px-3 py-2">
                <span class="text-muted-foreground">{{ $t('ticketManage.detail.priority') }}</span>
                <span>{{ $t(ticketPriorityKeys[selectedTicket.priority]) }}</span>
              </div>
              <div class="flex justify-between px-3 py-2">
                <span class="text-muted-foreground">{{ $t('ticketManage.detail.createdAt') }}</span>
                <span>{{ formatTime(selectedTicket.createdAt) }}</span>
              </div>
              <div v-if="selectedTicket.handlingNote" class="flex justify-between px-3 py-2">
                <span class="text-muted-foreground">{{ $t('ticketManage.detail.handlingNote') }}</span>
                <span>{{ selectedTicket.handlingNote }}</span>
              </div>
            </div>

            <div>
              <h4 class="text-sm font-medium mb-1">{{ $t('ticketManage.detail.content') }}</h4>
              <p class="text-sm text-muted-foreground whitespace-pre-wrap rounded-md border border-border p-3">
                {{ selectedTicket.content }}
              </p>
            </div>

            <div v-if="selectedTicket.attachments.length">
              <h4 class="text-sm font-medium mb-1">{{ $t('ticketManage.detail.attachments') }}</h4>
              <div class="flex flex-col gap-1">
                <a
                  v-for="file in selectedTicket.attachments"
                  :key="file.id"
                  :href="`/v1/files/${file.id}/download`"
                  target="_blank"
                  class="text-sm text-primary hover:underline flex items-center gap-1"
                >
                  {{ file.originalName }} ({{ formatFileSize(file.fileSize) }})
                </a>
              </div>
            </div>

            <div class="flex gap-2">
              <Button v-if="selectedTicket.status === 'PENDING'" class="flex-1" @click="start(selectedTicket)">
                <Play class="mr-1 h-4 w-4" />{{ $t('ticketManage.detail.startProcess') }}
              </Button>
              <Button v-if="selectedTicket.status === 'IN_PROGRESS'" class="flex-1" @click="openComplete(selectedTicket)">
                <CheckCircle class="mr-1 h-4 w-4" />{{ $t('ticketManage.detail.complete') }}
              </Button>
            </div>

            <div v-if="changes.length">
              <h4 class="text-sm font-medium mb-2">{{ $t('ticketManage.detail.changeHistory') }}</h4>
              <div class="space-y-2">
                <div
                  v-for="record in changes"
                  :key="record.id"
                  class="text-xs text-muted-foreground border-l-2 border-border pl-3 py-1"
                >
                  <span class="font-medium text-foreground">{{ record.changedByName || record.changedBy }}</span>
                  {{ $t('common.edit') }} {{ record.fieldName }}：{{ record.oldValue || '-' }} → {{ record.newValue || '-' }}
                  <div class="text-[10px] text-muted-foreground/60">{{ formatTime(record.changedAt) }}</div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </SheetContent>
    </Sheet>

    <!-- Complete Modal -->
    <Dialog :open="showComplete" @update:open="showComplete = $event">
      <DialogContent class="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{{ $t('ticketManage.detail.complete') }}</DialogTitle>
        </DialogHeader>
        <textarea
          id="ticket-handling-note"
          name="ticket-handling-note"
          v-model="handlingNote"
          :placeholder="$t('ticketManage.detail.handlingNotePlaceholder')"
          class="flex min-h-[100px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        />
        <DialogFooter>
          <Button variant="outline" @click="showComplete = false">{{ $t('common.cancel') }}</Button>
          <Button :loading="handling" @click="complete">{{ $t('ticketManage.detail.complete') }}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
