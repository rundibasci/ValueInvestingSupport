import { apiFetch } from "./client";

export type SeedStatus =
  | "seeded"
  | "seeded_partial"
  | "refreshed"
  | "skipped"
  | "failed"
  | "unavailable";

export type SeedResult = {
  symbol: string;
  companyName: string | null;
  sector: string | null;
  exchange: string | null;
  country: string | null;
  description: string | null;
  currentPrice: number | null;
  compositeFairValue: number | null;
  marginOfSafety: number | null;
  totalScore: number | null;
  recommendation: string | null;
  source: string | null;
  status: SeedStatus | string | null;
  fallbackReason: string | null;
  refreshedAt: string | null;
  reasonCode: string | null;
  reason: string | null;
  error: string | null;
};
export type SeedRunAccepted = {
  seedRunId: string;
  status: string;
  normalizedTickerCount: number;
  progressUrl: string;
  outcomesUrl: string;
  pollingIntervalMs: number;
  joinedExistingRun: boolean;
};
export type SeedRunStatus = {
  seedRunId: string;
  scope: string;
  status: string;
  total: number;
  processed: number;
  succeeded: number;
  partiallySeeded: number;
  failed: number;
  currentSymbol: string | null;
  terminalReason: string | null;
  createdAt: string;
  startedAt: string | null;
  updatedAt: string;
  completedAt: string | null;
  pollingIntervalMs: number;
};
export type SeedRunOutcome = {
  position: number;
  symbol: string;
  status: "SUCCESS" | "PARTIAL" | "FAILED";
  source: string | null;
  reasonCode: string | null;
  reason: string | null;
  fallbackReason: string | null;
  error: string | null;
  completedAt: string;
};
export type PageResponse<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };
export type SeedSubmitResponse = SeedResult[] | SeedRunAccepted;

async function json<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init);
  if (!response.ok) {
    const payload = (await response.json().catch(() => null)) as
      | { message?: string; detail?: string; error?: string }
      | null;
    throw new Error(
      payload?.message ||
        payload?.detail ||
        payload?.error ||
        `Request failed (${response.status}).`,
    );
  }
  return response.json() as Promise<T>;
}

function tickersParam(symbols: string[]): string {
  return new URLSearchParams({ tickers: symbols.join(",") }).toString();
}

export const seedUniverseApi = {
  seedCsv: (symbols: string[]) =>
    json<SeedSubmitResponse>(`/api/v1/universe/seed?${tickersParam(symbols)}`, {
      method: "POST",
    }),
  seedAdminPack: (symbols: string[]) =>
    json<SeedSubmitResponse>(`/api/v1/admin/seed?${tickersParam(symbols)}`, {
      method: "POST",
    }),
  run: (id: string) => json<SeedRunStatus>(`/api/v1/seed/runs/${id}`),
  outcomes: (id: string, page = 0) =>
    json<PageResponse<SeedRunOutcome>>(`/api/v1/seed/runs/${id}/outcomes?page=${page}&size=50`),
  retryFailures: (id: string) =>
    json<SeedRunAccepted>(`/api/v1/seed/runs/${id}/retry-failures`, { method: "POST" }),
};
