import { apiFetch } from "./client";

export type PortfolioAnalysisAccepted = { analysisRunId: string; status: string; total: number; statusUrl: string; outcomesUrl: string; pollingIntervalMs: number; joined: boolean };
export type PortfolioAnalysisStatus = { analysisRunId: string; portfolioId: string; importId: string | null; status: "QUEUED" | "RUNNING" | "COMPLETE" | "PARTIAL" | "FAILED"; phase: string; total: number; processed: number; succeeded: number; partial: number; failed: number; currentSymbol: string | null; terminalReason: string | null; analyticsSnapshotId: string | null; analysisVersion: string; createdAt: string; startedAt: string | null; updatedAt: string; completedAt: string | null; pollingIntervalMs: number };
export type PortfolioAnalysisOutcome = { position: number; symbol: string; status: string; source: string | null; refreshedAt: string | null; sourceLastPrice: number | null; sourceBaseValue: number | null; refreshedPrice: number | null; priceVariancePercent: number | null; reasonCode: string | null; reason: string | null; fallbackReason: string | null; errorMessage: string | null; reviewPath: string | null; calculationVersion: string; startedAt: string | null; completedAt: string | null };
export type AnalysisOutcomePage = { content: PortfolioAnalysisOutcome[]; page: number; size: number; totalElements: number; totalPages: number };

async function json<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init);
  if (!response.ok) {
    const payload = await response.json().catch(() => null) as { detail?: string; message?: string; error?: string } | null;
    throw new Error(payload?.detail || payload?.message || payload?.error || `Request failed (${response.status}).`);
  }
  return response.json() as Promise<T>;
}
const base = (portfolioId: string): string => `/api/v1/portfolios/${portfolioId}/analysis-runs`;
export const portfolioAnalysisApi = {
  start: (portfolioId: string, importId?: string) => json<PortfolioAnalysisAccepted>(base(portfolioId), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(importId ? { importId } : {}) }),
  latest: (portfolioId: string) => json<PortfolioAnalysisStatus>(`${base(portfolioId)}/latest`),
  status: (portfolioId: string, runId: string) => json<PortfolioAnalysisStatus>(`${base(portfolioId)}/${runId}`),
  outcomes: (portfolioId: string, runId: string) => json<AnalysisOutcomePage>(`${base(portfolioId)}/${runId}/outcomes?size=100`),
  retry: (portfolioId: string, runId: string) => json<PortfolioAnalysisAccepted>(`${base(portfolioId)}/${runId}/retry-failures`, { method: "POST" }),
};
