<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import type { KnowledgeDocument } from '../types/knowledge'

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
  <n-modal
    :show="show"
    @update:show="emit('update:show', $event)"
    preset="card"
    :title="doc.fileName"
    style="width: 800px; max-width: 90vw;"
    :segmented="true"
  >
    <template #header-extra>
      <n-tag size="tiny" :bordered="false">{{ doc.fileType?.toUpperCase() || '未知' }}</n-tag>
      <n-text depth="3" style="margin-left: 8px; font-size: 13px;">
        {{ doc.charCount?.toLocaleString() || 0 }} 字符
      </n-text>
    </template>

    <n-scrollbar style="max-height: 60vh;">
      <!-- Markdown rendered -->
      <div v-if="isMd && mdHtml" class="md-preview" v-html="mdHtml" />

      <!-- JSON pretty-print -->
      <pre v-else-if="isJson && prettyJson" class="code-content json">{{ prettyJson }}</pre>

      <!-- CSV table -->
      <template v-else-if="isCsv && csvData.headers.length">
        <n-table size="small" striped>
          <thead>
            <tr>
              <th v-for="h in csvData.headers" :key="h">{{ h }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, ri) in csvData.rows" :key="ri">
              <td v-for="(cell, ci) in row" :key="ci">{{ cell }}</td>
            </tr>
          </tbody>
        </n-table>
      </template>

      <!-- Rich document extracted text -->
      <template v-else-if="isRichDoc">
        <n-alert type="info" :bordered="false" style="margin-bottom: 12px;">
          {{ doc.fileType?.toUpperCase() }} 文件暂不支持富文本渲染，以下为后端提取的文本内容
        </n-alert>
        <pre class="code-content">{{ doc.content || '(无文本内容)' }}</pre>
      </template>

      <!-- Code-like formats (XML, YAML, Properties, Log, TXT) -->
      <pre v-else-if="hasContent" class="code-content">{{ doc.content }}</pre>

      <!-- Empty state -->
      <n-empty v-else description="无内容" />
    </n-scrollbar>
  </n-modal>
</template>

<style scoped>
.code-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-color, #333);
}

.code-content.json {
  color: var(--text-color, #333);
}

.md-preview {
  font-size: 14px;
  line-height: 1.8;
  padding: 4px 0;
}

.md-preview :deep(h1) { font-size: 1.6em; border-bottom: 1px solid var(--border-color, #eee); padding-bottom: 6px; margin: 20px 0 12px; }
.md-preview :deep(h2) { font-size: 1.35em; border-bottom: 1px solid var(--border-color, #eee); padding-bottom: 4px; margin: 18px 0 10px; }
.md-preview :deep(h3) { font-size: 1.2em; margin: 16px 0 8px; }
.md-preview :deep(h4) { font-size: 1.1em; margin: 14px 0 6px; }
.md-preview :deep(p) { margin: 8px 0; }
.md-preview :deep(ul), .md-preview :deep(ol) { padding-left: 24px; margin: 8px 0; }
.md-preview :deep(li) { margin: 4px 0; }
.md-preview :deep(code) {
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
  background: var(--code-bg, #f5f5f5);
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 0.9em;
}
.md-preview :deep(pre) {
  background: var(--code-bg, #f5f5f5);
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
  border-left: 4px solid var(--primary-color, #C8815F);
  margin: 12px 0;
  padding: 4px 16px;
  color: var(--text-color-3, #888);
}
.md-preview :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
}
.md-preview :deep(th), .md-preview :deep(td) {
  border: 1px solid var(--border-color, #ddd);
  padding: 6px 12px;
  text-align: left;
}
.md-preview :deep(th) {
  background: var(--code-bg, #f5f5f5);
  font-weight: 600;
}
.md-preview :deep(a) { color: var(--primary-color, #C8815F); }
.md-preview :deep(img) { max-width: 100%; border-radius: 4px; margin: 8px 0; }
.md-preview :deep(hr) { border: none; border-top: 1px solid var(--border-color, #eee); margin: 20px 0; }
</style>
