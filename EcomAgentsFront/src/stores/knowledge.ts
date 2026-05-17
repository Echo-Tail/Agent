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
  deleteDocumentApi,
  searchDocumentsApi,
} from '../api/knowledge'
import type { KnowledgeBase, KnowledgeDocument } from '../types/knowledge'

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
      const res = await listKnowledgeBasesApi()
      if (res.data.code === 200) {
        kbs.value = res.data.data ?? []
      }
    } catch {
      // Backend may not have KB endpoints yet
    } finally {
      loading.value = false
    }
  }

  async function loadKb(id: number) {
    loading.value = true
    try {
      const [kbRes, docsRes] = await Promise.all([
        getKnowledgeBaseApi(id),
        listDocumentsApi(id),
      ])
      if (kbRes.data.code === 200) {
        currentKb.value = kbRes.data.data
      }
      if (docsRes.data.code === 200) {
        documents.value = docsRes.data.data ?? []
      }
    } catch {
      // Backend may not have KB endpoints yet
    } finally {
      loading.value = false
    }
  }

  async function createKb(data: { name: string; description?: string }) {
    const res = await createKnowledgeBaseApi(data)
    if (res.data.code === 200) {
      await fetchKbs()
      return res.data.data
    }
    throw new Error(res.data.message || '创建失败')
  }

  async function updateKb(id: number, data: { name?: string; description?: string }) {
    const res = await updateKnowledgeBaseApi(id, data)
    if (res.data.code === 200) {
      await fetchKbs()
      if (currentKb.value?.id === id) {
        currentKb.value = res.data.data
      }
      return true
    }
    return false
  }

  async function removeKb(id: number) {
    const res = await deleteKnowledgeBaseApi(id)
    if (res.data.code === 200) {
      if (currentKb.value?.id === id) {
        currentKb.value = null
        documents.value = []
      }
      await fetchKbs()
      return true
    }
    return false
  }

  async function uploadDoc(kbId: number, file: File) {
    const res = await uploadDocumentApi(kbId, file)
    if (res.data.code === 200) {
      const doc = res.data.data
      // Reload docs to get updated list
      const docsRes = await listDocumentsApi(kbId)
      if (docsRes.data.code === 200) {
        documents.value = docsRes.data.data ?? []
      }
      return doc
    }
    throw new Error(res.data.message || '上传失败')
  }

  async function removeDoc(kbId: number, docId: number) {
    const res = await deleteDocumentApi(kbId, docId)
    if (res.data.code === 200) {
      documents.value = documents.value.filter((d) => d.id !== docId)
      return true
    }
    return false
  }

  async function search(q: string) {
    searchQuery.value = q
    if (!q.trim()) {
      searchResults.value = []
      return
    }
    const res = await searchDocumentsApi(q)
    if (res.data.code === 200) {
      searchResults.value = res.data.data ?? []
    }
  }

  function clearCurrent() {
    currentKb.value = null
    documents.value = []
  }

  return {
    kbs, currentKb, documents, loading,
    searchQuery, searchResults, isSearching,
    fetchKbs, loadKb, createKb, updateKb, removeKb,
    uploadDoc, removeDoc, search, clearCurrent,
  }
})
