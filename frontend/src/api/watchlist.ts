import { apiFetch } from './client'

export type WatchlistItem = {
  id: string
  symbol: string
  mosAlertMin: number | null
  mosAlertMax: number | null
  fundamentalDegradeThreshold: number | null
  monitoringReason: string | null
  rationaleNote: string | null
  addedAt: string
}

export type WatchlistThresholds = Pick<WatchlistItem, 'mosAlertMin' | 'mosAlertMax' | 'fundamentalDegradeThreshold' | 'monitoringReason' | 'rationaleNote'>

export type Alert = {
  id: string
  alertType: string
  symbol: string
  threshold: number | null
  triggeredAt: string | null
  status: string
  priority: string | null
  deliveryStatus: string | null
}

async function json<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init)
  if (!response.ok) throw new Error(`Request failed (${response.status}).`)
  return response.json() as Promise<T>
}

const body = (method: 'POST' | 'PUT', value: unknown): RequestInit => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(value),
})

export const watchlistApi = {
  list: () => json<WatchlistItem[]>('/api/v1/watchlist'),
  alerts: () => json<Alert[]>('/api/v1/watchlist/alerts'),
  add: (symbol: string, thresholds: WatchlistThresholds) =>
    json<WatchlistItem>('/api/v1/watchlist', body('POST', { symbol, ...thresholds })),
  update: (id: string, thresholds: WatchlistThresholds) =>
    json<WatchlistItem>(`/api/v1/watchlist/${id}`, body('PUT', thresholds)),
  remove: async (id: string): Promise<void> => {
    const response = await apiFetch(`/api/v1/watchlist/${id}`, { method: 'DELETE' })
    if (!response.ok) throw new Error(`Request failed (${response.status}).`)
  },
  acknowledge: (id: string) => json<Alert>(`/api/v1/alerts/${id}/ack`, { method: 'PUT' }),
}
