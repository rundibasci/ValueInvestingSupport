import { apiFetch } from './client'

export interface ScreenerRequest {
  sector?: string | null
  exchange?: string | null
  minMarginOfSafety?: number | null
  maxMarginOfSafety?: number | null
  minValueScore?: number | null
  minRoic?: number | null
  maxDebtToEquity?: number | null
  minDividendYield?: number | null
  minRevenueGrowth?: number | null
  sortField?: string | null
  sortDirection?: 'ASC' | 'DESC' | null
  page?: number
  pageSize?: number
}

export interface ScreenerResultItem {
  symbol: string
  companyName: string
  sector: string
  exchange: string
  currentPrice: number | null
  compositeFairValue: number | null
  marginOfSafety: number | null
  totalScore: number | null
  mosScore: number | null
  qualityScore: number | null
  safetyScore: number | null
  growthScore: number | null
  dividendScore: number | null
  recommendation: string
  scoreDate?: string | null
}

export interface ScreenerResponse {
  results: ScreenerResultItem[]
  page: number
  pageSize: number
  totalElements: number
  totalPages: number
}

export async function fetchScreener(request: ScreenerRequest): Promise<ScreenerResponse> {
  const response = await apiFetch('/api/v1/screener', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!response.ok) {
    throw new Error('Failed to fetch screener results')
  }
  return response.json()
}

export async function fetchScreenerPresets(): Promise<Record<string, ScreenerRequest>> {
  const response = await apiFetch('/api/v1/screener/presets')
  if (!response.ok) {
    throw new Error('Failed to fetch presets')
  }
  return response.json()
}

export async function fetchSectors(): Promise<string[]> {
  const response = await apiFetch('/api/v1/screener/sectors')
  if (!response.ok) {
    throw new Error('Failed to fetch sectors')
  }
  return response.json()
}

export async function fetchExchanges(): Promise<string[]> {
  const response = await apiFetch('/api/v1/screener/exchanges')
  if (!response.ok) {
    throw new Error('Failed to fetch exchanges')
  }
  return response.json()
}
