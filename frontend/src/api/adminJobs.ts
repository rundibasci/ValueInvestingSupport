import { apiFetch } from "./client";

export type JobRunSummary = {
  id: string;
  jobName: string;
  startedAt: string;
  completedAt: string | null;
  status: string;
  recordsProcessed: number | null;
  errorMessage: string | null;
};

export type JobMonitorRow = {
  jobName: string;
  cronExpression: string;
  enabled: boolean;
  nextRunAt: string | null;
  scheduleError: string | null;
  currentStatus: string;
  currentDurationSeconds: number;
  dataSource: string | null;
  runningRun: JobRunSummary | null;
  lastRun: JobRunSummary | null;
  lastSuccessfulRun: JobRunSummary | null;
  lastFailedRun: JobRunSummary | null;
  latestError: string | null;
};

export type JobRunStatus = {
  jobRunId: string;
  jobName: string;
  status: string;
  recordsProcessed: number | null;
  totalSymbols: number | null;
  elapsedSeconds: number;
  errorCount: number;
  scopeSymbols: string | null;
  scopeExchange: string | null;
  scopeDataTypes: string | null;
  startedAt: string;
  completedAt: string | null;
  errorMessage: string | null;
};

export type IngestionEvent = {
  id: string;
  jobRunId: string | null;
  jobName: string;
  symbol: string | null;
  dataType: string;
  status: string;
  source: string | null;
  errorDetail: string | null;
  occurredAt: string;
};

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type JobRunRequest = {
  symbols?: string;
  exchange?: string;
  dataTypes?: string;
};

export type JobTriggerResponse = {
  jobName: string;
  status: string;
  jobRunId: string;
};

async function json<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init);
  if (!response.ok) {
    const fallback = `Request failed with HTTP ${response.status}`;
    let message = fallback;
    try {
      const body = (await response.json()) as { error?: string; status?: string; jobRunId?: string };
      message = body.error ?? body.status ?? fallback;
      if (response.status === 409 && body.jobRunId) {
        message = `already-running:${body.jobRunId}`;
      }
    } catch {
      // Keep fallback message.
    }
    throw new Error(message);
  }
  return response.json() as Promise<T>;
}

function params(values: Record<string, string | number | null | undefined>): string {
  const search = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value != null && String(value).trim() !== "") search.set(key, String(value));
  });
  const query = search.toString();
  return query ? `?${query}` : "";
}

export const adminJobsApi = {
  monitor: () => json<JobMonitorRow[]>("/api/v1/admin/jobs/monitor"),
  run: (jobName: string, request: JobRunRequest) =>
    json<JobTriggerResponse>(`/api/v1/admin/jobs/${jobName}/run`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
    }),
  status: (jobRunId: string) =>
    json<JobRunStatus>(`/api/v1/admin/jobs/runs/${jobRunId}/status`),
  setEnabled: (jobName: string, enabled: boolean) =>
    json<JobMonitorRow>(`/api/v1/admin/jobs/${jobName}/enabled`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ enabled }),
    }),
  setCron: (jobName: string, cron: string) =>
    json<JobMonitorRow>(`/api/v1/admin/jobs/${jobName}/cron`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ cron }),
    }),
  history: (jobName: string, page = 0, size = 8) =>
    json<PageResponse<JobRunSummary>>(
      `/api/v1/admin/jobs/${jobName}/history${params({ page, size })}`,
    ),
  events: (jobName: string, runId?: string, page = 0, size = 10) =>
    json<PageResponse<IngestionEvent>>(
      `/api/v1/admin/jobs/${jobName}/events${params({ runId, page, size })}`,
    ),
};
