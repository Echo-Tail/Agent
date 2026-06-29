import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  listKnowledgeBasesApi,
  getKnowledgeBaseApi,
  createKnowledgeBaseApi,
  updateKnowledgeBaseApi,
  deleteKnowledgeBaseApi,
  listDocumentsApi,
  uploadDocumentApi,
  uploadDocumentsApi,
  deleteDocumentApi,
  searchDocumentsApi,
} from '@/api/knowledge'
import type { KnowledgeBase, KnowledgeDocument } from '@/types/knowledge'

export const useKnowledgeStore = defineStore('knowledge', () => {
  const kbs = ref<KnowledgeBase[]>([])
  const currentKb = ref<KnowledgeBase | null>(null)
  const documents = ref<KnowledgeDocument[]>([])
  const loading = ref(false)
  const searchQuery = ref('')
  const searchResults = ref<KnowledgeDocument[]>([])

  const isSearching = computed(() => searchQuery.value.trim().length > 0)

  async function fetchKbs() {
    loading.value = true
    try {
      kbs.value = (await listKnowledgeBasesApi()) ?? []
    } catch { /* ignore */ } finally {
      loading.value = false
    }
  }

  async function loadKb(id: number) {
    loading.value = true
    try {
      const [kbData, docsData] = await Promise.all([
        getKnowledgeBaseApi(id),
        listDocumentsApi(id),
      ])
      currentKb.value = kbData
      documents.value = docsData ?? []
    } catch { /* ignore */ } finally {
      loading.value = false
    }
  }

  async function createKb(data: { name: string; description?: string }) {
    const kb = await createKnowledgeBaseApi(data)
    await fetchKbs()
    return kb
  }

  async function updateKb(id: number, data: { name?: string; description?: string }) {
    const updated = await updateKnowledgeBaseApi(id, data)
    await fetchKbs()
    if (currentKb.value?.id === id) currentKb.value = updated
    return true
  }

  async function removeKb(id: number) {
    await deleteKnowledgeBaseApi(id)
    if (currentKb.value?.id === id) {
      currentKb.value = null
      documents.value = []
    }
    await fetchKbs()
    return true
  }

  async function uploadDoc(kbId: number, file: File) {
    const doc = await uploadDocumentApi(kbId, file)
    documents.value = (await listDocumentsApi(kbId)) ?? []
    return doc
  }

  async function uploadDocs(kbId: number, files: File[]) {
    const docs = await uploadDocumentsApi(kbId, files)
    documents.value = (await listDocumentsApi(kbId)) ?? []
    return docs
  }

  async function removeDoc(kbId: number, docId: number) {
    await deleteDocumentApi(kbId, docId)
    documents.value = documents.value.filter((d) => d.id !== docId)
    return true
  }

  async function search(q: string) {
    searchQuery.value = q
    if (!q.trim()) {
      searchResults.value = []
      return
    }
    searchResults.value = (await searchDocumentsApi(q)) ?? []
  }

  function clearCurrent() {
    currentKb.value = null
    documents.value = []
  }

  return {
    kbs, currentKb, documents, loading, searchQuery, searchResults, isSearching,
    fetchKbs, loadKb, createKb, updateKb, removeKb,
    uploadDoc, uploadDocs, removeDoc, search, clearCurrent,
  }
})
