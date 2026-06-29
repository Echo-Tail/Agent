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

export interface SellingPointEvidence {
  source_path: string
  source_text: string
}

export interface BuyerCognition {
  id: string
  enabled: boolean
  priority: number
  type: string
  visual_model: string
  feature: string
  feature_cn?: string
  buyer_cognition_cn: string
  buyer_cognition_en: string
  scene_cn?: string
  scene_en?: string
  pain_point_cn?: string
  pain_point_en?: string
  belief_cn?: string
  belief_en?: string
  confidence?: string
  evidence: SellingPointEvidence[]
  risk_notes?: string[]
}

export interface SellingPointCognitionJson {
  category: string
  category_strategy_version: string
  profile_id?: number
  profile_version_id?: number
  status: string
  buyer_cognitions: BuyerCognition[]
  global_constraints: string[]
  claims_to_avoid: string[]
  review?: {
    status?: string
    missing_fields?: string[]
    low_confidence_items?: string[]
    notes?: string
  }
}

export interface SellingPointCognitionVersion {
  id: number
  profileId: number
  profileVersionId: number | null
  versionNumber: number
  status: string
  cognitionJson: string | null
  sourceFactsHash: string | null
  createdBy: number | null
  createdAt: string
  confirmedBy: number | null
  confirmedAt: string | null
}

export interface TextOverlays {
  headline?: string
  subhead?: string
  badges?: string[]
}

export interface GalleryStrategyImage {
  slot: number
  role: string
  visual_model: string
  goal_cn: string
  goal_en: string
  selected_cognition_ids: string[]
  buyer_cognition_cn: string
  buyer_cognition_en: string
  visual_structure_cn: string
  visual_structure_en: string
  required_visual_elements: string[]
  text_overlays_cn: TextOverlays
  text_overlays_en: TextOverlays
  prompt_cn: string
  prompt_en: string
  negative_constraints: string[]
  text_rendering_risk?: string
  evidence?: SellingPointEvidence[]
}

export interface AplusStrategyModule {
  module_index: number
  module_type: string
  goal_cn: string
  goal_en: string
  selected_cognition_ids: string[]
  visual_model: string
  headline_cn: string
  headline_en: string
  body_copy_cn: string
  body_copy_en: string
  image_prompt_cn: string
  image_prompt_en: string
  required_assets: string[]
  text_overlays_cn: TextOverlays
  text_overlays_en: TextOverlays
  negative_constraints: string[]
  evidence?: SellingPointEvidence[]
}

export interface VisualStrategyJson {
  category: string
  category_strategy_version: string
  profile_id?: number
  profile_version_id?: number
  cognition_version_id?: number
  status: string
  content_scope: string[]
  global_constraints: string[]
  claims_to_avoid: string[]
  gallery_strategy?: { images: GalleryStrategyImage[] }
  aplus_strategy?: { layout_type: string; modules: AplusStrategyModule[] }
  review?: {
    status?: string
    missing_assets?: string[]
    low_confidence_prompts?: string[]
    notes?: string
  }
}

export interface VisualStrategyVersion {
  id: number
  profileId: number
  profileVersionId: number | null
  cognitionVersionId: number
  versionNumber: number
  status: string
  contentScope: string
  strategyJson: string | null
  createdBy: number | null
  createdAt: string
  confirmedBy: number | null
  confirmedAt: string | null
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

export function generateSellingPointCognitions(id: number) {
  return api.post<SellingPointCognitionVersion>(`/product-profiles/${id}/selling-point-cognitions/generate`)
}

export function getCurrentSellingPointCognition(id: number) {
  return api.get<SellingPointCognitionVersion>(`/product-profiles/${id}/selling-point-cognitions/current`)
}

export function getSellingPointCognitionVersions(id: number) {
  return api.get<SellingPointCognitionVersion[]>(`/product-profiles/${id}/selling-point-cognitions/versions`)
}

export function updateSellingPointCognition(id: number, versionId: number, cognitionJson: string) {
  return api.put<SellingPointCognitionVersion>(`/product-profiles/${id}/selling-point-cognitions/${versionId}`, cognitionJson, {
    headers: { 'Content-Type': 'text/plain' },
  })
}

export function confirmSellingPointCognition(id: number, versionId: number) {
  return api.post<SellingPointCognitionVersion>(`/product-profiles/${id}/selling-point-cognitions/${versionId}/confirm`)
}

export function generateVisualStrategy(id: number, data?: { cognition_version_id?: number | null; cognitionVersionId?: number | null; content_scope?: string[]; contentScope?: string[] }) {
  return api.post<VisualStrategyVersion>(`/product-profiles/${id}/visual-strategies/generate`, data ?? {})
}

export function getCurrentVisualStrategy(id: number) {
  return api.get<VisualStrategyVersion>(`/product-profiles/${id}/visual-strategies/current`)
}

export function getVisualStrategyVersions(id: number) {
  return api.get<VisualStrategyVersion[]>(`/product-profiles/${id}/visual-strategies/versions`)
}

export function updateVisualStrategy(id: number, versionId: number, strategyJson: string) {
  return api.put<VisualStrategyVersion>(`/product-profiles/${id}/visual-strategies/${versionId}`, strategyJson, {
    headers: { 'Content-Type': 'text/plain' },
  })
}

export function confirmVisualStrategy(id: number, versionId: number) {
  return api.post<VisualStrategyVersion>(`/product-profiles/${id}/visual-strategies/${versionId}/confirm`)
}
