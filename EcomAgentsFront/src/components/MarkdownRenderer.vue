<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const COPY_BTN_ATTR = 'data-md-copy-btn'

const props = defineProps<{
  content: string
}>()

// Custom renderer: add a copy button to each fenced code block
const renderer = new marked.Renderer()
renderer.code = ({ text, lang }) => {
  const langAttr = lang ? ` class="language-${lang}"` : ''
  const btnSvg = '<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>'
  return `<pre><code${langAttr}>${text}</code><button class="md-copy-btn" ${COPY_BTN_ATTR} title="复制代码">${btnSvg}</button></pre>`
}

const renderedHtml = computed(() => {
  const raw = marked.parse(props.content, { async: false, renderer }) as string
  return DOMPurify.sanitize(raw)
})

const elRef = ref<HTMLElement | null>(null)

function handleClick(e: Event) {
  const btn = (e.target as HTMLElement).closest<HTMLElement>(`[${COPY_BTN_ATTR}]`)
  if (!btn) return

  const pre = btn.closest('pre')
  if (!pre) return

  const code = pre.querySelector('code')
  if (!code) return

  const text = code.textContent || ''
  navigator.clipboard.writeText(text).catch(() => {
    // fallback silently
  })

  // Show success feedback
  btn.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>'
  btn.classList.add('copied')
  setTimeout(() => {
    btn.classList.remove('copied')
    btn.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>'
  }, 1500)
}

onMounted(() => {
  elRef.value?.addEventListener('click', handleClick)
})

onUnmounted(() => {
  elRef.value?.removeEventListener('click', handleClick)
})
</script>

<template>
  <div ref="elRef" class="markdown-body" v-html="renderedHtml" />
</template>

<style scoped>
.markdown-body {
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  margin: 16px 0 8px;
  font-weight: 600;
  line-height: 1.4;
}

.markdown-body :deep(h1) { font-size: 1.4em; }
.markdown-body :deep(h2) { font-size: 1.25em; }
.markdown-body :deep(h3) { font-size: 1.1em; }

.markdown-body :deep(p) {
  margin: 0 0 8px;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 4px 0;
  padding-left: 20px;
}

.markdown-body :deep(li) {
  margin: 2px 0;
}

.markdown-body :deep(a) {
  color: #C8815F;
  text-decoration: underline;
}

.markdown-body :deep(a:hover) {
  opacity: 0.8;
}

.markdown-body :deep(blockquote) {
  margin: 8px 0;
  padding: 4px 12px;
  border-left: 3px solid #C8815F;
  color: #666;
  background: rgba(200, 129, 95, 0.06);
  border-radius: 0 4px 4px 0;
}

.markdown-body :deep(code) {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 13px;
  padding: 2px 6px;
  background: rgba(0, 0, 0, 0.06);
  border-radius: 4px;
}

.markdown-body :deep(pre) {
  margin: 8px 0;
  padding: 12px 14px;
  background: #1E1E1E;
  border-radius: 8px;
  overflow-x: auto;
  position: relative;
}

.markdown-body :deep(pre code) {
  padding: 0;
  background: none;
  border-radius: 0;
  font-size: 13px;
  color: #D4D4D4;
  line-height: 1.5;
  tab-size: 2;
}

/* Copy button in code block */
.markdown-body :deep(.md-copy-btn) {
  position: absolute;
  top: 6px;
  right: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.5);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s, background 0.15s, color 0.15s;
}

.markdown-body :deep(pre:hover .md-copy-btn) {
  opacity: 1;
}

.markdown-body :deep(.md-copy-btn:hover) {
  background: rgba(255, 255, 255, 0.15);
  color: rgba(255, 255, 255, 0.85);
}

.markdown-body :deep(.md-copy-btn.copied) {
  opacity: 1;
  color: #4CAF50;
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
  width: 100%;
  font-size: 13px;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #ddd;
  padding: 6px 10px;
  text-align: left;
}

.markdown-body :deep(th) {
  background: rgba(0, 0, 0, 0.04);
  font-weight: 600;
}

.markdown-body :deep(img) {
  max-width: 100%;
  border-radius: 6px;
  margin: 8px 0;
}

.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid #ddd;
  margin: 16px 0;
}
</style>
