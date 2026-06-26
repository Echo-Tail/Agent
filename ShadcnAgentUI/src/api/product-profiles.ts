import { api } from './request'
import type { PageResponse } from './image'

export interface ProductProfile {
  id: number
  userId: number
  productName: string
  brand: string | null
  sku: string | null
  modelNumber: string | null
  targetAsin: string | null
  category: string
  markdownContent: string | null
  sourceType: string | null
  sourceAsin: string | null
  sourceRawJson: string | null
  productFactsJson: string | null
  status: string
  parseError: string | null
  currentVersionId: number | null
  createdAt: string
  updatedAt: string
}

export interface ProductProfileVersion {
  id: number
  profileId: number
  versionNumber: number
  productFactsJson: string | null
  confirmedBy: number | null
  confirmedAt: string | null
  createdAt: string
}

export interface ProductProfileImage {
  id: number
  profileId: number
  fileName: string
  filePath: string
  fileSize: number | null
  mimeType: string | null
  tag: string
  uploadedBy: number
  createdAt: string
}

export function listProductProfiles(params?: {
  page?: number
  size?: number
  status?: string
  keyword?: string
}) {
  return api.get<PageResponse<ProductProfile>>('/product-profiles', { params })
}

export function createProductProfile(productName: string, markdownContent?: string) {
  const params = new URLSearchParams()
  if (productName) params.append('productName', productName)
  if (markdownContent) params.append('markdownContent', markdownContent)
  return api.post<ProductProfile>('/product-profiles', params, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  })
}

export function createProductProfileFromFile(file: File) {
  const fd = new FormData()
  fd.append('file', file)
  return api.post<ProductProfile>('/product-profiles', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120_000,
  })
}

export function createProductProfileFromAsin(asin: string) {
  const params = new URLSearchParams()
  params.append('asin', asin)
  return api.post<ProductProfile>('/product-profiles', params, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    timeout: 120_000,
  })
}

export function getProductProfile(id: number) {
  return api.get<ProductProfile>(`/product-profiles/${id}`)
}

export function updateProductProfileFacts(id: number, productFactsJson: string) {
  return api.put<ProductProfile>(`/product-profiles/${id}/facts`, productFactsJson, {
    headers: { 'Content-Type': 'text/plain' },
  })
}

export function confirmProductProfile(id: number) {
  return api.post<ProductProfile>(`/product-profiles/${id}/confirm`)
}

export function reparseProductProfile(id: number) {
  return api.post<ProductProfile>(`/product-profiles/${id}/reparse`)
}

export function createProductProfileVersion(id: number, markdownContent: string) {
  const params = new URLSearchParams()
  params.append('markdownContent', markdownContent)
  return api.post<ProductProfile>(`/product-profiles/${id}/versions`, params, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  })
}

export function getProductProfileVersions(id: number) {
  return api.get<ProductProfileVersion[]>(`/product-profiles/${id}/versions`)
}

export function getProductProfileVersion(versionId: number) {
  return api.get<ProductProfileVersion>(`/product-profiles/versions/${versionId}`)
}

export function getProductProfileImages(id: number) {
  return api.get<ProductProfileImage[]>(`/product-profiles/${id}/images`)
}

export function uploadProductProfileImage(id: number, file: File, tag?: string) {
  const fd = new FormData()
  fd.append('file', file)
  if (tag) fd.append('tag', tag)
  return api.post<ProductProfileImage>(`/product-profiles/${id}/images`, fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function deleteProductProfileImage(imageId: number) {
  return api.delete(`/product-profiles/images/${imageId}`)
}

export function deleteProductProfile(id: number) {
  return api.delete(`/product-profiles/${id}`)
}
