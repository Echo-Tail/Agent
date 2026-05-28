/**
 * 文件 API 测试
 *
 * 覆盖 uploadFileApi / listMyFilesApi / downloadFileApi 三个函数。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import http from '@/api/request'
import {
  uploadFileApi,
  listMyFilesApi,
  downloadFileApi,
} from '@/api/file'

vi.mock('@/api/request', () => ({
  default: {
    post: vi.fn(),
    get: vi.fn(),
  },
}))

describe('file API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('uploadFileApi', () => {
    it('应 POST /files/upload 含 FormData', async () => {
      const file = new File(['test'], 'test.txt', { type: 'text/plain' })
      vi.mocked(http.post).mockResolvedValue({ id: 1, originalName: 'test.txt' })

      const result = await uploadFileApi(file)

      expect(http.post).toHaveBeenCalledWith(
        '/files/upload',
        expect.any(FormData),
        { headers: { 'Content-Type': 'multipart/form-data' } },
      )
      expect(result.id).toBe(1)
    })

    it('应附带 contextType + contextId', async () => {
      const file = new File(['data'], 'doc.pdf', { type: 'application/pdf' })
      vi.mocked(http.post).mockResolvedValue({ id: 2 })

      await uploadFileApi(file, 'PRIVATE', 5)

      // Verify FormData contains the context fields
      const callArgs = vi.mocked(http.post).mock.calls[0]
      const formData = callArgs[1] as FormData
      expect(formData.get('contextType')).toBe('PRIVATE')
      expect(formData.get('contextId')).toBe('5')
    })

    it('不传 context 时不应附带', async () => {
      const file = new File(['data'], 'doc.pdf', { type: 'application/pdf' })
      vi.mocked(http.post).mockResolvedValue({ id: 3 })

      await uploadFileApi(file)

      const callArgs = vi.mocked(http.post).mock.calls[0]
      const formData = callArgs[1] as FormData
      expect(formData.get('contextType')).toBeNull()
      expect(formData.get('contextId')).toBeNull()
    })
  })

  describe('listMyFilesApi', () => {
    it('应 GET /files 带 context 参数', async () => {
      vi.mocked(http.get).mockResolvedValue([
        { id: 1, originalName: 'a.txt' },
        { id: 2, originalName: 'b.txt' },
      ])

      const result = await listMyFilesApi('PRIVATE', 5)

      expect(http.get).toHaveBeenCalledWith('/files', {
        params: { contextType: 'PRIVATE', contextId: 5 },
      })
      expect(result).toHaveLength(2)
    })

    it('应正确传递 AGENT context', async () => {
      vi.mocked(http.get).mockResolvedValue([])

      await listMyFilesApi('AGENT', 10)

      expect(http.get).toHaveBeenCalledWith('/files', {
        params: { contextType: 'AGENT', contextId: 10 },
      })
    })
  })

  describe('downloadFileApi', () => {
    it('应 fetch /v1/files/{id}/download', async () => {
      const mockBlob = new Blob(['data'], { type: 'text/plain' })
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        blob: vi.fn().mockResolvedValue(mockBlob),
      })

      const result = await downloadFileApi(1)

      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/v1/files/1/download',
        expect.objectContaining({ headers: expect.any(Object) }),
      )
      expect(result).toBe(mockBlob)
    })

    it('下载失败应抛错', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
      })

      await expect(downloadFileApi(999)).rejects.toThrow('下载失败')
    })
  })
})
