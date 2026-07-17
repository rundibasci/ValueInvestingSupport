import { apiFetch } from "./client";

export type ImportMode = "MERGE" | "REPLACE";
export type ImportRowStatus = "READY" | "CASH" | "NEEDS_MAPPING" | "NEEDS_ADMIN_MAPPING" | "WARNING" | "INVALID";

export type PortfolioImportRow = {
  rowId: string;
  rowNumber: number;
  productName: string;
  sourceCode: string | null;
  isin: string | null;
  quantity: number | null;
  sourceLastPrice: number | null;
  nativeCurrency: string | null;
  nativeValue: number | null;
  baseValue: number | null;
  resolvedSecurityId: string | null;
  resolvedSymbol: string | null;
  classification: string;
  status: ImportRowStatus;
  warning: string | null;
  error: string | null;
  committedOutcome: string | null;
};

export type PortfolioImportPreview = {
  importId: string;
  portfolioId: string | null;
  filename: string;
  checksum: string;
  detectedSchema: string;
  mode: ImportMode;
  baseCurrency: string;
  status: string;
  sourceRowCount: number;
  readyRowCount: number;
  warningCount: number;
  errorCount: number;
  baseValueTotal: number;
  nativeValueTotals: Record<string, number>;
  createdAt: string;
  expiresAt: string;
  rows: PortfolioImportRow[];
};

export type IsinMapping = { rowId: string; securityId: string };
export type PortfolioImportCommit = {
  importId: string;
  portfolioId: string;
  status: string;
  mode: ImportMode;
  committedHoldingRows: number;
  committedCashRows: number;
  skippedRows: number;
  baseValueTotal: number;
  nativeValueTotals: Record<string, number>;
  committedAt: string;
  rows: PortfolioImportRow[];
};
export type PortfolioImportHistoryItem = {
  importId: string;
  portfolioId: string | null;
  portfolioName: string | null;
  filename: string;
  checksum: string;
  mode: ImportMode;
  baseCurrency: string;
  status: string;
  sourceRowCount: number;
  readyRowCount: number;
  warningCount: number;
  errorCount: number;
  createdAt: string;
  expiresAt: string;
  committedAt: string | null;
};
export type PortfolioImportHistory = {
  content: PortfolioImportHistoryItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};
export type SecuritySearchResult = {
  id: string;
  symbol: string;
  companyName: string;
  sector: string | null;
  exchange: string | null;
};

async function json<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init);
  if (!response.ok) {
    const payload = (await response.json().catch(() => null)) as { error?: string; detail?: string; message?: string } | null;
    const error = new Error(payload?.error || payload?.detail || payload?.message || `Request failed (${response.status}).`);
    Object.assign(error, { status: response.status });
    throw error;
  }
  return response.json() as Promise<T>;
}

export const portfolioImportApi = {
  preview: (input: { file: File; portfolioId?: string; baseCurrency: string; mode: ImportMode }) => {
    const form = new FormData();
    form.append("file", input.file);
    const params = new URLSearchParams({ baseCurrency: input.baseCurrency, mode: input.mode });
    if (input.portfolioId) params.set("portfolioId", input.portfolioId);
    return json<PortfolioImportPreview>(`/api/v1/portfolios/imports/preview?${params}`, { method: "POST", body: form });
  },
  commit: (importId: string, input: { newPortfolioName?: string; replaceConfirmed: boolean; skippedRowIds: string[]; mappings: IsinMapping[] }) =>
    json<PortfolioImportCommit>(`/api/v1/portfolios/imports/${importId}/commit`, {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input),
    }),
  history: (portfolioId?: string, page = 0) => {
    const params = new URLSearchParams({ page: String(page), size: "10" });
    if (portfolioId) params.set("portfolioId", portfolioId);
    return json<PortfolioImportHistory>(`/api/v1/portfolios/imports?${params}`);
  },
  detail: (importId: string) => json<PortfolioImportPreview>(`/api/v1/portfolios/imports/${importId}`),
  searchSecurities: (query: string) => json<SecuritySearchResult[]>(`/api/v1/securities/search?q=${encodeURIComponent(query)}`),
  report: async (importId: string): Promise<Blob> => {
    const response = await apiFetch(`/api/v1/portfolios/imports/${importId}/report.csv`);
    if (!response.ok) throw new Error(`Report download failed (${response.status}).`);
    return response.blob();
  },
};
