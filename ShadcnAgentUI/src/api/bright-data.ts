import { api } from './request'

export interface BrightDataScrapeResponse {
  records?: any[]
  snapshotId?: string
  timeCostMs: number
  recordId: number
  message: string
}

export function scrapeAsin(asin: string) {
  return api.post<BrightDataScrapeResponse>('/bright-data/scrape-asin', null, {
    params: { asin },
    timeout: 120_000,
  })
}
