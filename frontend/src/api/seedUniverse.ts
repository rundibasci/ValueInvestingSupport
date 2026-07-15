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
    json<SeedResult[]>(`/api/v1/universe/seed?${tickersParam(symbols)}`, {
      method: "POST",
    }),
  seedAdminPack: (symbols: string[]) =>
    json<SeedResult[]>(`/api/v1/admin/seed?${tickersParam(symbols)}`, {
      method: "POST",
    }),
};
