import { apiFetch } from "./client";

export type FallbackFilters = {
  symbol?: string;
  operation?: string;
  eventType?: string;
  outcome?: string;
  triggerReason?: string;
  jobRunId?: string;
  from?: string;
  to?: string;
};

export type MarketDataFallbackEvent = {
  id: string;
  jobRunId: string | null;
  jobName: string | null;
  symbol: string;
  operation: string;
  eventType: string;
  triggerReason: string;
  primaryProvider: string;
  fallbackProvider: string;
  primaryStatus: string | null;
  outcome: string;
  missingFields: string | null;
  acceptedFields: string | null;
  errorDetail: string | null;
  durationMs: number;
  occurredAt: string;
};

export type MarketDataFallbackSummary = {
  totalAttempts: number;
  successfulFallbacks: number;
  successfulEnrichments: number;
  failedAttempts: number;
  rejectedAttempts: number;
  affectedSymbols: number;
  lastAttemptAt: string | null;
  byTrigger: Record<string, number>;
  byOperation: Record<string, number>;
  byOutcome: Record<string, number>;
};

export type FallbackPage = {
  content: MarketDataFallbackEvent[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

function query(filters: FallbackFilters, page?: number): string {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value?.trim()) params.set(key, value.trim());
  });
  if (page != null) params.set("page", String(page));
  params.set("size", "50");
  return `?${params.toString()}`;
}

async function get<T>(path: string): Promise<T> {
  const response = await apiFetch(path);
  if (!response.ok) throw new Error(`Request failed with HTTP ${response.status}`);
  return response.json() as Promise<T>;
}

export const marketDataFallbacksApi = {
  events: (filters: FallbackFilters, page: number) =>
    get<FallbackPage>(`/api/v1/admin/market-data-fallbacks${query(filters, page)}`),
  summary: (filters: FallbackFilters) =>
    get<MarketDataFallbackSummary>(`/api/v1/admin/market-data-fallbacks/summary${query(filters)}`),
};
