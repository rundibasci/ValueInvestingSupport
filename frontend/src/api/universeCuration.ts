import { apiFetch } from "./client";
import type { SeedResult, SeedRunAccepted } from "./seedUniverse";

export type UniverseSortBy =
  | "MARKET_CAP_DESC"
  | "MARKET_CAP_ASC"
  | "VOLUME_DESC"
  | "SYMBOL_ASC";

export type UniverseSelectionCriteria = {
  exchanges: string[];
  countries: string[];
  sectors: string[];
  excludeSectors: boolean;
  marketCapMin: number | null;
  marketCapMax: number | null;
  volumeMin: number | null;
  maxSymbols: number;
  sortBy: UniverseSortBy;
};

export type UniverseTemplate = {
  id: string;
  name: string;
  description: string;
  criteria: Partial<UniverseSelectionCriteria> | null;
};

export type UniversePreviewRow = {
  symbol: string;
  companyName: string | null;
  exchange: string | null;
  country: string | null;
  sector: string | null;
  marketCap: number | null;
  volume: number | null;
};

export type UniversePreview = {
  totalMatches: number;
  returnedCount: number;
  capped: boolean;
  warning: string | null;
  symbols: UniversePreviewRow[];
};

export type UniverseSeedCriteriaResponse = {
  preview: UniversePreview;
  results: SeedResult[];
};
export type UniverseSeedAsyncResponse = { preview: UniversePreview; run: SeedRunAccepted };

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

function request(criteria: UniverseSelectionCriteria): RequestInit {
  return {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(criteria),
  };
}

export const universeCurationApi = {
  templates: () =>
    json<UniverseTemplate[]>("/api/v1/admin/universe/templates"),
  preview: (criteria: UniverseSelectionCriteria) =>
    json<UniversePreview>("/api/v1/admin/universe/preview", request(criteria)),
  seed: (criteria: UniverseSelectionCriteria) =>
    json<UniverseSeedCriteriaResponse | UniverseSeedAsyncResponse>(
      "/api/v1/admin/universe/seed",
      request(criteria),
    ),
};
