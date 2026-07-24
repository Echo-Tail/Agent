export interface UpscaleCandidate {
  recordId?: number
  width?: number | null
  height?: number | null
}

export function validateWorkflowUpscale(candidate: UpscaleCandidate | undefined, factor: number): string {
  if (!candidate?.recordId) return '当前图片缺少记录信息，无法高清放大'
  if (![2, 3, 4].includes(factor)) return '高清放大倍数仅支持 2×、3× 或 4×'
  if (!candidate.width || !candidate.height) return '无法读取图片尺寸，请等待图片加载完成'
  if (Math.max(candidate.width, candidate.height) > 1920 || Math.min(candidate.width, candidate.height) > 1080) {
    return '图片尺寸超出高清放大限制：长边不超过 1920px，短边不超过 1080px'
  }
  return ''
}

export function normalizeWorkflowImageUrl(url: string): string {
  if (/^(blob:|https?:)/i.test(url)) return url
  const normalized = url.replace(/\\/g, '/').replace(/^\.\//, '/')
  return normalized.startsWith('/') ? normalized : `/${normalized}`
}
