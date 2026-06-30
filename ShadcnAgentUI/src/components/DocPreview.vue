<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import type { KnowledgeDocument } from '@/types/knowledge'

useI18n()
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Badge } from '@/components/ui/badge'

const props = defineProps<{
  doc: KnowledgeDocument
  show: boolean
}>()

const emit = defineEmits<{
  'update:show': [v: boolean]
}>()

const mdHtml = ref('')

const fileType = computed(() => props.doc.fileType?.toLowerCase() || '')
const isMd = computed(() => fileType.value === 'md')
const isJson = computed(() => fileType.value === 'json')
const isCsv = computed(() => fileType.value === 'csv')
const isRichDoc = computed(() => ['pdf', 'docx', 'xlsx'].includes(fileType.value))
const hasContent = computed(() => !!props.doc.content)

watch(() => props.doc, async (doc) => {
  if (doc?.content && isMd.value) {
    const raw = await marked.parse(doc.content)
    mdHtml.value = DOMPurify.sanitize(raw)
  } else {
    mdHtml.value = ''
  }
}, { immediate: true })

const prettyJson = computed(() => {
  if (!isJson.value || !props.doc.content) return ''
  try {
    return JSON.stringify(JSON.parse(props.doc.content), null, 2)
  } catch {
    return props.doc.content
  }
})

const csvData = computed(() => {
  if (!isCsv.value || !props.doc.content) return { headers: [] as string[], rows: [] as string[][] }
  const lines = props.doc.content.trim().split('\n')
  if (lines.length === 0) return { headers: [], rows: [] }

  function parseLine(line: string): string[] {
    const row: string[] = []
    let cur = ''
    let inQuotes = false
    for (let i = 0; i < line.length; i++) {
      const ch = line[i]
      if (ch === '"') { inQuotes = !inQuotes; continue }
      if (ch === ',' && !inQuotes) { row.push(cur.trim()); cur = ''; continue }
      cur += ch
    }
    row.push(cur.trim())
    return row
  }

  const parsed = lines.map(parseLine)
  return { headers: parsed[0] || [], rows: parsed.slice(1) }
})
</script>

<template>
  <Dialog :open="show" @update:open="emit('update:show', $event)">
    <DialogContent class="max-w-3xl max-h-[80vh] overflow-y-auto">
      <DialogHeader class="flex flex-row items-center gap-3">
        <DialogTitle>{{ doc.fileName }}</DialogTitle>
        <Badge variant="outline" class="text-xs">{{ doc.fileType?.toUpperCase() || $t('knowledge.previewUnknownType') }}</Badge>
        <span class="text-xs text-muted-foreground">{{ doc.charCount?.toLocaleString() || 0 }} {{ $t('common.chars') }}</span>
      </DialogHeader>

      <!-- Markdown rendered -->
      <div v-if="isMd && mdHtml" class="md-preview" v-html="mdHtml" />

      <!-- JSON pretty-print -->
      <pre v-else-if="isJson && prettyJson" class="code-content json">{{ prettyJson }}</pre>

      <!-- CSV table -->
      <template v-else-if="isCsv && csvData.headers.length">
        <div class="border border-border rounded-md overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="bg-muted/50">
                <th v-for="h in csvData.headers" :key="h" class="px-3 py-2 text-left font-medium border-b border-border">{{ h }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, ri) in csvData.rows" :key="ri" class="even:bg-muted/20">
                <td v-for="(cell, ci) in row" :key="ci" class="px-3 py-1.5 border-b border-border">{{ cell }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>

      <!-- Rich document extracted text -->
      <template v-else-if="isRichDoc">
        <div class="bg-muted/30 text-sm text-muted-foreground px-3 py-2 rounded-md mb-2">
          {{ $t('knowledge.previewRichNotSupported', { type: (doc.fileType?.toUpperCase() || '') }) }}
        </div>
        <pre class="code-content">{{ doc.content || $t('knowledge.previewEmptyText') }}</pre>
      </template>

      <!-- Code-like formats -->
      <pre v-else-if="hasContent" class="code-content">{{ doc.content }}</pre>

      <!-- Empty state -->
      <div v-else class="text-center py-8 text-muted-foreground text-sm">{{ $t('knowledge.previewNoContent') }}</div>
    </DialogContent>
  </Dialog>
</template>

<style scoped>
.code-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.7;
}

.md-preview {
  font-size: 14px;
  line-height: 1.8;
  padding: 4px 0;
}

.md-preview :deep(h1) { font-size: 1.6em; border-bottom: 1px solid hsl(var(--border)); padding-bottom: 6px; margin: 20px 0 12px; }
.md-preview :deep(h2) { font-size: 1.35em; border-bottom: 1px solid hsl(var(--border)); padding-bottom: 4px; margin: 18px 0 10px; }
.md-preview :deep(h3) { font-size: 1.2em; margin: 16px 0 8px; }
.md-preview :deep(p) { margin: 8px 0; }
.md-preview :deep(ul), .md-preview :deep(ol) { padding-left: 24px; margin: 8px 0; }
.md-preview :deep(li) { margin: 4px 0; }
.md-preview :deep(code) {
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
  background: hsl(var(--muted));
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 0.9em;
}
.md-preview :deep(pre) {
  background: hsl(var(--muted));
  padding: 12px 16px;
  border-radius: 6px;
  overflow-x: auto;
}
.md-preview :deep(pre code) {
  background: none;
  padding: 0;
  border-radius: 0;
}
.md-preview :deep(blockquote) {
  border-left: 4px solid hsl(var(--primary));
  margin: 12px 0;
  padding: 4px 16px;
  color: hsl(var(--muted-foreground));
}
.md-preview :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
}
.md-preview :deep(th), .md-preview :deep(td) {
  border: 1px solid hsl(var(--border));
  padding: 6px 12px;
  text-align: left;
}
.md-preview :deep(th) {
  background: hsl(var(--muted));
  font-weight: 600;
}
.md-preview :deep(a) { color: hsl(var(--primary)); }
.md-preview :deep(img) { max-width: 100%; border-radius: 4px; margin: 8px 0; }
.md-preview :deep(hr) { border: none; border-top: 1px solid hsl(var(--border)); margin: 20px 0; }
</style>
