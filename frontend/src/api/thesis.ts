import { apiFetch } from "./client";

// Mirrors backend it.mazzoni.vis.thesis.* DTOs exactly (verified against source at
// implementation time, per this project's own "verify against real code, not spec text"
// discipline — see specs/2026-08-28-ta5-ai-investment-thesis-frontend/validation.md).

export type ThesisClassification =
  | "POTENTIALLY_UNDERVALUED"
  | "FAIRLY_VALUED"
  | "POTENTIALLY_OVERVALUED"
  | "UNDER_REVIEW"
  | "INSUFFICIENT_DATA";

export type ThesisStatus = "GENERATING" | "READY" | "FAILED" | "HUMAN_REVIEW_PENDING";

export type EvidenceField =
  | "marketPrice"
  | "intrinsicValue"
  | "marginOfSafetyPercent"
  | "valueScore"
  | "dividendYieldPercent"
  | "payoutRatioPercent"
  | "netDebtToEbitda"
  | "revenueTrend"
  | "earningsTrend"
  | "freeCashFlowTrend"
  | "dataQuality"
  | "deterministicWarnings";

export type ThesisEvidence = { claim: string; evidenceFields: EvidenceField[] };

export type ThesisOutput = {
  classification: ThesisClassification | null;
  confidence: number | null;
  summary: string | null;
  bullCase: ThesisEvidence[];
  bearCase: ThesisEvidence[];
  keyRisks: string[];
  keyAssumptions: string[];
  invalidationConditions: string[];
  dataWarnings: string[];
  humanReviewRequired: boolean;
};

// GET /api/v1/securities/{symbol}/thesis — ThesisResponse. No errorCode/errorMessage/
// latencyMs field exists on this DTO (only on the run-status poll response below); a FAILED
// thesis's explanation lives in output.dataWarnings (ThesisOutput.deterministicFallback
// puts the tracked error reason there as the sole entry).
export type Thesis = {
  id: string;
  symbol: string;
  status: ThesisStatus | "NOT_GENERATED";
  modelId: string | null;
  modelVersion: string | null;
  promptVersion: string | null;
  output: ThesisOutput | null;
  generatedAt: string | null;
  stale: boolean;
};

// POST .../thesis/generate → 202, ThesisGenerationAcceptedResponse
export type ThesisGenerationAccepted = {
  thesisRunId: string;
  status: string;
  pollingIntervalMs: number;
  statusUrl: string;
};

// GET .../thesis/runs/{thesisRunId}/status — ThesisRunStatusResponse
export type ThesisRunStatus = {
  thesisRunId: string;
  status: ThesisStatus;
  classification: string | null;
  confidence: number | null;
  humanReviewRequired: boolean | null;
  errorCode: string | null;
  generatedAt: string | null;
};

// GET /api/v1/admin/thesis/review-queue — PageResponse<ThesisReviewQueueItemResponse>
export type ReviewQueueItem = {
  id: string;
  symbol: string;
  companyName: string | null;
  status: string;
  classification: string | null;
  humanReviewRequired: boolean | null;
  dataWarningsPresent: boolean;
  generatedAt: string | null;
};

export type ReviewQueuePage = {
  content: ReviewQueueItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

/** Distinguishable from a generic failure so the panel can render "Daily limit reached"
 * copy instead of a retry-suggesting error. Matches GlobalExceptionHandler's 429 body
 * exactly: { error, code: "RATE_LIMIT_EXCEEDED", limit, resetsAt }. */
export class RateLimitError extends Error {
  constructor(readonly limit: number, readonly resetsAt: string) {
    super(`Daily thesis generation limit (${limit}) exceeded; resets at ${resetsAt}.`);
  }
}

/** Distinguishable from a generic failure so the panel can explain the feature is turned
 * off in this environment instead of suggesting a retry that will never succeed. */
export class ThesisDisabledError extends Error {
  constructor() {
    super("AI Investment Thesis generation is not enabled in this environment.");
  }
}

async function json<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init);
  if (response.status === 429) {
    const payload = (await response.json().catch(() => null)) as { limit?: number; resetsAt?: string } | null;
    throw new RateLimitError(payload?.limit ?? 0, payload?.resetsAt ?? "");
  }
  if (response.status === 503) throw new ThesisDisabledError();
  if (!response.ok) {
    const payload = (await response.json().catch(() => null)) as { detail?: string; message?: string; error?: string } | null;
    throw new Error(payload?.detail || payload?.message || payload?.error || `Request failed (${response.status}).`);
  }
  return response.json() as Promise<T>;
}

const base = (symbol: string): string => `/api/v1/securities/${encodeURIComponent(symbol)}/thesis`;

export const thesisApi = {
  generate: (symbol: string) => json<ThesisGenerationAccepted>(`${base(symbol)}/generate`, { method: "POST" }),
  status: (symbol: string, thesisRunId: string) => json<ThesisRunStatus>(`${base(symbol)}/runs/${thesisRunId}/status`),
  // The backend always answers 200 with status: "NOT_GENERATED" rather than 404 — the
  // client normalizes that to `null` so callers get the "no thesis yet" case as a plain
  // value, not an error to catch (requirements.md → Decision 6).
  latest: async (symbol: string): Promise<Thesis | null> => {
    const thesis = await json<Thesis>(base(symbol));
    return thesis.status === "NOT_GENERATED" ? null : thesis;
  },
  reviewQueue: (page = 0, size = 20) => json<ReviewQueuePage>(`/api/v1/admin/thesis/review-queue?page=${page}&size=${size}`),
};
