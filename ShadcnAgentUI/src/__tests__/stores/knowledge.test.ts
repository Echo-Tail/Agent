import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useKnowledgeStore } from '@/stores/knowledge'
import type { KnowledgeBase, KnowledgeDocument } from '@/types/knowledge'

vi.mock('@/api/knowledge', () => ({
  listKnowledgeBasesApi: vi.fn(),
  getKnowledgeBaseApi: vi.fn(),
  createKnowledgeBaseApi: vi.fn(),
  updateKnowledgeBaseApi: vi.fn(),
  deleteKnowledgeBaseApi: vi.fn(),
  listDocumentsApi: vi.fn(),
  uploadDocumentApi: vi.fn(),
  deleteDocumentApi: vi.fn(),
  searchDocumentsApi: vi.fn(),
}))

const mockKb: KnowledgeBase = {
  id: 1, name: '产品手册', description: '产品文档',
  createdAt: '2024-01-01', createdBy: 1,
}

const mockDocs: KnowledgeDocument[] = [
  { id: 1, knowledgeBaseId: 1, fileName: 'intro.md', fileType: 'md', content: '# Intro', charCount: 100, uploadedAt: '2024-01-01', uploadedBy: 1 },
  { id: 2, knowledgeBaseId: 1, fileName: 'guide.txt', fileType: 'txt', content: 'guide content', charCount: 200, uploadedAt: '2024-01-02', uploadedBy: 1 },
]

beforeEach(() => {
  setActivePinia(createPinia())
})

describe('useKnowledgeStore', () => {
  it('starts with empty state', () => {
    const store = useKnowledgeStore()
    expect(store.kbs).toEqual([])
    expect(store.currentKb).toBeNull()
    expect(store.documents).toEqual([])
    expect(store.loading).toBe(false)
    expect(store.isSearching).toBe(false)
  })

  it('fetchKbs populates knowledge bases', async () => {
    const { listKnowledgeBasesApi } = await import('@/api/knowledge')
    vi.mocked(listKnowledgeBasesApi).mockResolvedValue([mockKb])

    const store = useKnowledgeStore()
    await store.fetchKbs()

    expect(store.kbs).toHaveLength(1)
    expect(store.kbs[0].name).toBe('产品手册')
    expect(store.loading).toBe(false)
  })

  it('fetchKbs sets loading state', async () => {
    const { listKnowledgeBasesApi } = await import('@/api/knowledge')
    vi.mocked(listKnowledgeBasesApi).mockResolvedValue([])

    const store = useKnowledgeStore()
    const promise = store.fetchKbs()
    expect(store.loading).toBe(true)
    await promise
    expect(store.loading).toBe(false)
  })

  it('loadKb fetches KB detail and documents', async () => {
    const { getKnowledgeBaseApi, listDocumentsApi } = await import('@/api/knowledge')
    vi.mocked(getKnowledgeBaseApi).mockResolvedValue(mockKb)
    vi.mocked(listDocumentsApi).mockResolvedValue(mockDocs)

    const store = useKnowledgeStore()
    await store.loadKb(1)

    expect(store.currentKb).toEqual(mockKb)
    expect(store.documents).toHaveLength(2)
    expect(store.loading).toBe(false)
  })

  it('createKb creates and refreshes list', async () => {
    const { createKnowledgeBaseApi, listKnowledgeBasesApi } = await import('@/api/knowledge')
    vi.mocked(createKnowledgeBaseApi).mockResolvedValue({ id: 2, name: '新知识库' } as any)
    vi.mocked(listKnowledgeBasesApi).mockResolvedValue([mockKb, { id: 2, name: '新知识库' }] as any)

    const store = useKnowledgeStore()
    const result = await store.createKb({ name: '新知识库' })

    expect(result).toBeDefined()
    expect(store.kbs).toHaveLength(2)
  })

  it('updateKb updates KB and refreshes', async () => {
    const { updateKnowledgeBaseApi, listKnowledgeBasesApi } = await import('@/api/knowledge')
    vi.mocked(updateKnowledgeBaseApi).mockResolvedValue({ ...mockKb, name: '新名称' })
    vi.mocked(listKnowledgeBasesApi).mockResolvedValue([])

    const store = useKnowledgeStore()
    store.currentKb = mockKb
    const result = await store.updateKb(1, { name: '新名称' })

    expect(result).toBe(true)
    expect(store.currentKb?.name).toBe('新名称')
  })

  it('removeKb deletes and clears current if deleted', async () => {
    const { deleteKnowledgeBaseApi, listKnowledgeBasesApi } = await import('@/api/knowledge')
    vi.mocked(deleteKnowledgeBaseApi).mockResolvedValue(undefined as any)
    vi.mocked(listKnowledgeBasesApi).mockResolvedValue([])

    const store = useKnowledgeStore()
    store.currentKb = mockKb
    store.documents = mockDocs
    const result = await store.removeKb(1)

    expect(result).toBe(true)
    expect(store.currentKb).toBeNull()
    expect(store.documents).toEqual([])
  })

  it('uploadDoc uploads and refreshes document list', async () => {
    const { uploadDocumentApi, listDocumentsApi } = await import('@/api/knowledge')
    vi.mocked(uploadDocumentApi).mockResolvedValue({ id: 3 } as any)
    vi.mocked(listDocumentsApi).mockResolvedValue(mockDocs)

    const store = useKnowledgeStore()
    const file = new File(['test'], 'test.txt', { type: 'text/plain' })
    const result = await store.uploadDoc(1, file)

    expect(result).toBeDefined()
    expect(store.documents).toHaveLength(2)
  })

  it('removeDoc removes document from local list', async () => {
    const { deleteDocumentApi } = await import('@/api/knowledge')
    vi.mocked(deleteDocumentApi).mockResolvedValue(undefined as any)

    const store = useKnowledgeStore()
    store.documents = mockDocs
    const result = await store.removeDoc(1, 1)

    expect(result).toBe(true)
    expect(store.documents).toHaveLength(1)
    expect(store.documents[0].id).toBe(2)
  })

  it('search sets query and results', async () => {
    const { searchDocumentsApi } = await import('@/api/knowledge')
    vi.mocked(searchDocumentsApi).mockResolvedValue(mockDocs)

    const store = useKnowledgeStore()
    await store.search('产品')

    expect(store.searchQuery).toBe('产品')
    expect(store.isSearching).toBe(true)
    expect(store.searchResults).toHaveLength(2)
  })

  it('search with empty query clears results', async () => {
    const store = useKnowledgeStore()
    store.searchResults = mockDocs
    await store.search('')

    expect(store.searchResults).toEqual([])
    expect(store.isSearching).toBe(false)
  })

  it('clearCurrent resets current KB and documents', () => {
    const store = useKnowledgeStore()
    store.currentKb = mockKb
    store.documents = mockDocs
    store.clearCurrent()

    expect(store.currentKb).toBeNull()
    expect(store.documents).toEqual([])
  })
})
