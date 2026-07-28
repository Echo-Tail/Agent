import { api } from './request'
import type { PageResponse } from './image'

export interface ReviewProjectProduct {
  id: number
  asin: string
  role: 'product' | 'own' | 'competitor'
  productName: string | null
  reviewLimit: number
}

export interface ReviewProject {
  id: number
  profileId: number | null
  name: string
  marketplace: string
  category: string
  status: string
  latestCollectionId: number | null
  products: ReviewProjectProduct[]
  createdAt: string
  updatedAt: string
}

export interface ReviewCollection {
  id: number
  projectId: number
  snapshotId: string | null
  datasetId: string
  status: string
  requestedCount: number
  collectedCount: number
  duplicateCount: number
  errorMessage: string | null
  startedAt: string | null
  completedAt: string | null
  createdAt: string
}

export interface AuditSample {
  insightId: number
  reviewId: number
  asin: string
  reviewText: string
  evidenceQuote: string
  productModule: string
  severity: string
  audited: boolean
  evidenceValid: boolean | null
  moduleAccepted: boolean | null
  severityAccepted: boolean | null
  notes: string | null
}

export interface ValidationReport {
  runId: number
  sampleSize: number
  auditedCount: number
  evidenceValidityRate: number
  moduleAcceptanceRate: number
  severityAcceptanceRate: number
  duplicateRate: number
  traceableTopOpportunities: number
  topOpportunityCount: number
  releaseReady: boolean
  checks: { key: string; passed: boolean; actual: string; target: string }[]
}

export interface AnalysisRun {
  id: number
  projectId: number
  versionNumber: number
  status: string
  taxonomyVersion: string
  promptVersion: string
  modelId: number
  sourceReviewCount: number
  processedReviewCount: number
  failedReviewCount: number
  errorMessage: string | null
  createdAt: string
  confirmedAt: string | null
}

export interface ReviewInsight {
  id: number
  reviewId: number
  asin: string
  rating: number | null
  reviewText: string
  userProblem: string
  usageScenario: string
  productModule: string
  severity: string
  sentiment: string
  evidenceQuote: string
  actionType: string
  improvementAction: string
  returnRisk: number
  conversionRisk: number
  confidence: number
  manuallyEdited: boolean
  updatedAt: string
}

export interface ReviewOpportunity {
  id: number
  analysisRunId: number
  title: string
  usageScenario: string
  productModule: string
  severity: string
  actionType: string
  recommendedAction: string
  insightCount: number
  affectedReviewRatio: number
  customerImpact: number
  businessImpact: number
  implementationEffort: number
  priorityScore: number
  rationale: string | null
  manuallyEdited: boolean
}

export interface DimensionCount { key: string; count: number }
export interface ReviewDashboard {
  runId: number
  reviewCount: number
  insightCount: number
  opportunityCount: number
  manuallyEditedInsightCount: number
  averageRating: number | null
  ratings: DimensionCount[]
  severities: DimensionCount[]
  scenarios: DimensionCount[]
  modules: DimensionCount[]
  actionTypes: DimensionCount[]
  productReviewCounts: Record<string, number>
}

export interface ProjectProductInput {
  asin: string
  role: 'own' | 'competitor'
  reviewLimit: number
}

const base = '/review-analysis/projects'

export const listReviewProjects = () => api.get<ReviewProject[]>(base)
export const getReviewProject = (projectId: number) => api.get<ReviewProject>(`${base}/${projectId}`)
export const createReviewProject = (data: { asins: string[] }) =>
  api.post<ReviewProject>(base, data)
export const deleteReviewProject = (projectId: number) => api.delete(`${base}/${projectId}`)

export const startReviewCollection = (projectId: number) =>
  api.post<ReviewCollection>(`${base}/${projectId}/collections`, undefined, {
    headers: { 'Idempotency-Key': crypto.randomUUID() },
  })
export const getReviewCollection = (projectId: number, batchId: number, refresh = true) =>
  api.get<ReviewCollection>(`${base}/${projectId}/collections/${batchId}`, { params: { refresh }, silent: true })

export const startReviewAnalysis = (
  projectId: number,
) => api.post<AnalysisRun>(`${base}/${projectId}/analysis-runs`, undefined, {
  headers: { 'Idempotency-Key': crypto.randomUUID() },
})
export const getAnalysisRun = (projectId: number, runId: number) =>
  api.get<AnalysisRun>(`${base}/${projectId}/analysis-runs/${runId}`, { silent: true })
export const listAnalysisRuns = (projectId: number) =>
  api.get<AnalysisRun[]>(`${base}/${projectId}/analysis-runs`)
export const retryAnalysisFailures = (projectId: number, runId: number) =>
  api.post<AnalysisRun>(`${base}/${projectId}/analysis-runs/${runId}/retry-failures`)
export const confirmAnalysisRun = (projectId: number, runId: number) =>
  api.post<AnalysisRun>(`${base}/${projectId}/analysis-runs/${runId}/confirm`)

export const getReviewDashboard = (projectId: number, runId: number) =>
  api.get<ReviewDashboard>(`${base}/${projectId}/analysis-runs/${runId}/dashboard`)
export const listReviewOpportunities = (projectId: number, runId: number) =>
  api.get<ReviewOpportunity[]>(`${base}/${projectId}/analysis-runs/${runId}/opportunities`)
export const listOpportunityInsights = (projectId: number, runId: number, opportunityId: number) =>
  api.get<ReviewInsight[]>(`${base}/${projectId}/analysis-runs/${runId}/opportunities/${opportunityId}/insights`)
export const updateOpportunityEffort = (projectId: number, runId: number, id: number, implementationEffort: number) =>
  api.patch<ReviewOpportunity>(`${base}/${projectId}/analysis-runs/${runId}/opportunities/${id}/effort`, { implementationEffort })
export const listReviewInsights = (projectId: number, runId: number, params: Record<string, unknown>) =>
  api.get<PageResponse<ReviewInsight>>(`${base}/${projectId}/analysis-runs/${runId}/insights`, { params })
export const updateReviewInsight = (projectId: number, runId: number, insightId: number, data: Partial<ReviewInsight>) =>
  api.patch<ReviewInsight>(`${base}/${projectId}/analysis-runs/${runId}/insights/${insightId}`, data)
export const getValidationSample = (projectId: number, runId: number) =>
  api.get<AuditSample[]>(`${base}/${projectId}/analysis-runs/${runId}/validation/sample`)
export const auditValidationSample = (
  projectId: number, runId: number, insightId: number,
  data: { evidenceValid: boolean; moduleAccepted: boolean; severityAccepted: boolean; notes?: string },
) => api.put<AuditSample>(`${base}/${projectId}/analysis-runs/${runId}/validation/sample/${insightId}`, data)
export const getValidationReport = (projectId: number, runId: number) =>
  api.get<ValidationReport>(`${base}/${projectId}/analysis-runs/${runId}/validation/report`)
