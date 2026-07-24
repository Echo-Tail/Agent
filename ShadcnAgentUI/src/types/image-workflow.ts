export type ImageWorkflowNodeKind = 'generation' | 'result'

export interface ImageWorkflowNodeData {
  kind: ImageWorkflowNodeKind
  title: string
  prompt?: string
  imageUrl?: string
  status?: 'draft' | 'pending' | 'running' | 'succeeded' | 'failed'
  imageCount?: number
  jobId?: number
  statusText?: string
  recordId?: number
  width?: number | null
  height?: number | null
  elapsedSeconds?: number
  upscaleFactor?: number
  upscaleJobId?: number
  modelId?: number
  size?: string
  quality?: string
  parentNodeId?: string
  referenceImages?: Array<{
    url: string
    name: string
    recordId?: number
    assetId?: number
  }>
  maskImage?: {
    url: string
    name: string
    assetId?: number
  }
}

export interface ImageWorkflowSnapshot {
  version: 1
  backgroundColor?: string
  nodes: Array<{
    id: string
    type: ImageWorkflowNodeKind
    position: { x: number; y: number }
    data: ImageWorkflowNodeData
  }>
  edges: Array<{
    id: string
    source: string
    target: string
  }>
  viewport: {
    x: number
    y: number
    zoom: number
  }
}
