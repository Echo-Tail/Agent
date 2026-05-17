import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useKnowledgeStore } from '../../stores/knowledge'
import type { KnowledgeBase, KnowledgeDocument } from '../../types/knowledge'

vi.mock('../../api/knowledge', () => ({
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

const mockKbs: KnowledgeBase[] = [
  { id: 1, name: 'FAQ', description: '常见问题', createdAt: '2024-01-01', createdBy: 1 },
]

const mockDocs: KnowledgeDocument[] = [
  { id: 1, knowledgeBaseId: 1, fileName: 'doc.txt', fileType: 'txt',
    content: 'hello', charCount: 5, uploadedAt: '2024-01-01', uploadedBy: 1 },
]

beforeEach(() => {
  setActivePinia(createPinia())
})

describe('useKnowledgeStore', () => {
  it('starts empty', () => {
    const store = useKnowledgeStore()
    expect(store.kbs).toEqual([])
    expect(store.currentKb).toBeNull()
    expect(store.documents).toEqual([])
    expect(store.loading).toBe(false)
    expect(store.isSearching).toBe(false)
  })

  it('fetchKbs populates list', async () => {
    const { listKnowledgeBasesApi } = await import('../../api/knowledge')
    vi.mocked(listKnowledgeBasesApi).mockResolvedValue({
      data: { code: 200, message: 'ok', data: mockKbs },
    } as any)

    const store = useKnowledgeStore()
    await store.fetchKbs()

    expect(store.kbs).toHaveLength(1)
    expect(store.kbs[0].name).toBe('FAQ')
  })

  it('loadKb loads KB and documents', async () => {
    const { getKnowledgeBaseApi, listDocumentsApi } = await import('../../api/knowledge')
    vi.mocked(getKnowledgeBaseApi).mockResolvedValue({
      data: { code: 200, message: 'ok', data: mockKbs[0] },
    } as any)
    vi.mocked(listDocumentsApi).mockResolvedValue({
      data: { code: 200, message: 'ok', data: mockDocs },
    } as any)

    const store = useKnowledgeStore()
    await store.loadKb(1)

    expect(store.currentKb).toEqual(mockKbs[0])
    expect(store.documents).toHaveLength(1)
  })

  it('createKb refetches list', async () => {
    const { createKnowledgeBaseApi, listKnowledgeBasesApi } = await import('../../api/knowledge')
    vi.mocked(createKnowledgeBaseApi).mockResolvedValue({
      data: { code: 200, message: 'ok', data: { id: 2 } },
    } as any)
    vi.mocked(listKnowledgeBasesApi).mockResolvedValue({
      data: { code: 200, message: 'ok', data: [...mockKbs, { id: 2, name: 'New KB', description: '', createdAt: '', createdBy: 1 }] },
    } as any)

    const store = useKnowledgeStore()
    const result = await store.createKb({ name: 'New KB' })

    expect(result).toBeDefined()
    expect(store.kbs).toHaveLength(2)
  })

  it('createKb throws on failure', async () => {
    const { createKnowledgeBaseApi } = await import('../../api/knowledge')
    vi.mocked(createKnowledgeBaseApi).mockResolvedValue({
      data: { code: 400, message: '创建失败', data: null },
    } as any)

    const store = useKnowledgeStore()
    await expect(store.createKb({ name: 'Bad' })).rejects.toThrow('创建失败')
  })

  it('removeKb clears current when deleted', async () => {
    const { deleteKnowledgeBaseApi, listKnowledgeBasesApi } = await import('../../api/knowledge')
    vi.mocked(deleteKnowledgeBaseApi).mockResolvedValue({
      data: { code: 200, message: 'ok', data: null },
    } as any)
    vi.mocked(listKnowledgeBasesApi).mockResolvedValue({
      data: { code: 200, message: 'ok', data: [] },
    } as any)

    const store = useKnowledgeStore()
    store.currentKb = mockKbs[0]
    store.documents = mockDocs

    const result = await store.removeKb(1)

    expect(result).toBe(true)
    expect(store.currentKb).toBeNull()
    expect(store.documents).toEqual([])
  })

  it('removeDoc filters document locally', async () => {
    const { deleteDocumentApi } = await import('../../api/knowledge')
    vi.mocked(deleteDocumentApi).mockResolvedValue({
      data: { code: 200, message: 'ok', data: null },
    } as any)

    const store = useKnowledgeStore()
    store.documents = mockDocs

    const result = await store.removeDoc(1, 1)

    expect(result).toBe(true)
    expect(store.documents).toEqual([])
  })

  describe('search', () => {
    it('sets searchResults on match', async () => {
      const { searchDocumentsApi } = await import('../../api/knowledge')
      vi.mocked(searchDocumentsApi).mockResolvedValue({
        data: { code: 200, message: 'ok', data: mockDocs },
      } as any)

      const store = useKnowledgeStore()
      await store.search('hello')

      expect(store.searchQuery).toBe('hello')
      expect(store.isSearching).toBe(true)
      expect(store.searchResults).toHaveLength(1)
    })

    it('clears results for empty query', async () => {
      const store = useKnowledgeStore()
      store.searchResults = mockDocs

      await store.search('')

      expect(store.searchQuery).toBe('')
      expect(store.isSearching).toBe(false)
      expect(store.searchResults).toEqual([])
    })
  })

  describe('clearCurrent', () => {
    it('resets currentKb and documents', () => {
      const store = useKnowledgeStore()
      store.currentKb = mockKbs[0]
      store.documents = mockDocs

      store.clearCurrent()

      expect(store.currentKb).toBeNull()
      expect(store.documents).toEqual([])
    })
  })
})
