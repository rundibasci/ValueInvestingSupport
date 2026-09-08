import { useEffect, useMemo, useState } from "react";
import { Navigate } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { adminJobsApi, type IngestionEvent, type JobMonitorRow, type JobRunStatus, type JobRunSummary } from "../api/adminJobs";
import { useAuth } from "../auth/AuthProvider";

type DetailMode = "history" | "events";

function formatDate(value: string | null | undefined): string {
  if (!value) return "-";
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function formatDuration(seconds: number | null | undefined): string {
  if (!seconds) return "-";
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  const remainder = seconds % 60;
  return remainder ? `${minutes}m ${remainder}s` : `${minutes}m`;
}

function statusClass(status: string | null | undefined): string {
  switch ((status ?? "").toUpperCase()) {
    case "RUNNING":
      return "bg-sky-400/15 text-sky-100 ring-1 ring-sky-300/25";
    case "SUCCESS":
      return "bg-emerald-400/15 text-emerald-100 ring-1 ring-emerald-300/25";
    case "FAILED":
      return "bg-rose-400/15 text-rose-100 ring-1 ring-rose-300/25";
    case "SKIPPED":
      return "bg-amber-300/15 text-amber-100 ring-1 ring-amber-300/25";
    case "DISABLED":
      return "bg-slate-700/60 text-slate-200 ring-1 ring-slate-600";
    default:
      return "bg-slate-700/60 text-slate-200 ring-1 ring-slate-600";
  }
}

function RunSummary({ run }: { run: JobRunSummary | null }): JSX.Element {
  if (!run) return <span className="text-slate-400">No runs</span>;
  return (
    <div className="space-y-1">
      <span className={`inline-flex rounded-md px-2 py-1 text-xs font-semibold ${statusClass(run.status)}`}>
        {run.status}
      </span>
      <p className="text-xs text-slate-400">{formatDate(run.startedAt)}</p>
      {run.errorMessage && <p className="max-w-xs truncate text-xs text-rose-200">{run.errorMessage}</p>}
    </div>
  );
}

export function AdminJobsPage(): JSX.Element {
  const { session } = useAuth();
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [enabledFilter, setEnabledFilter] = useState("ALL");
  const [selectedJob, setSelectedJob] = useState<string | null>(null);
  const [detailMode, setDetailMode] = useState<DetailMode>("history");
  const [scope, setScope] = useState({ symbols: "", exchange: "", dataTypes: "" });
  const [trackedRunId, setTrackedRunId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const monitorQuery = useQuery({
    queryKey: ["admin-jobs", "monitor"],
    queryFn: adminJobsApi.monitor,
    refetchInterval: 15000,
  });

  const runStatusQuery = useQuery({
    queryKey: ["admin-jobs", "run-status", trackedRunId],
    queryFn: () => adminJobsApi.status(trackedRunId ?? ""),
    enabled: Boolean(trackedRunId),
    refetchInterval: (query) => {
      const status = (query.state.data as JobRunStatus | undefined)?.status;
      return status === "RUNNING" ? 2000 : false;
    },
  });

  const historyQuery = useQuery({
    queryKey: ["admin-jobs", "history", selectedJob],
    queryFn: () => adminJobsApi.history(selectedJob ?? ""),
    enabled: Boolean(selectedJob) && detailMode === "history",
  });

  const eventsQuery = useQuery({
    queryKey: ["admin-jobs", "events", selectedJob, trackedRunId],
    queryFn: () => adminJobsApi.events(selectedJob ?? "", trackedRunId ?? undefined),
    enabled: Boolean(selectedJob) && detailMode === "events",
  });

  const runMutation = useMutation({
    mutationFn: (jobName: string) =>
      adminJobsApi.run(jobName, {
        symbols: scope.symbols || undefined,
        exchange: scope.exchange || undefined,
        dataTypes: scope.dataTypes || undefined,
      }),
    onSuccess: (response) => {
      setTrackedRunId(response.jobRunId);
      setSelectedJob(response.jobName);
      setNotice(response.status === "skipped" ? "Job is disabled; run was recorded as skipped." : "Job started.");
      void queryClient.invalidateQueries({ queryKey: ["admin-jobs"] });
    },
    onError: (error) => {
      const message = error instanceof Error ? error.message : "Unable to run job.";
      if (message.startsWith("already-running:")) {
        const runId = message.split(":")[1];
        setTrackedRunId(runId);
        setNotice("That job is already running. Showing the active run instead.");
      } else {
        setNotice(message);
      }
    },
  });

  const enabledMutation = useMutation({
    mutationFn: ({ jobName, enabled }: { jobName: string; enabled: boolean }) =>
      adminJobsApi.setEnabled(jobName, enabled),
    onSuccess: () => {
      setNotice("Runtime setting updated.");
      void queryClient.invalidateQueries({ queryKey: ["admin-jobs"] });
    },
    onError: () => setNotice("Unable to update runtime setting."),
  });

  useEffect(() => {
    if (runStatusQuery.data && runStatusQuery.data.status !== "RUNNING") {
      void queryClient.invalidateQueries({ queryKey: ["admin-jobs", "monitor"] });
      void queryClient.invalidateQueries({ queryKey: ["admin-jobs", "history", selectedJob] });
      void queryClient.invalidateQueries({ queryKey: ["admin-jobs", "events", selectedJob, trackedRunId] });
    }
  }, [detailMode, queryClient, runStatusQuery.data, selectedJob, trackedRunId]);

  const jobs = monitorQuery.data ?? [];
  const filteredJobs = useMemo(
    () =>
      jobs.filter((job) => {
        const statusMatches = statusFilter === "ALL" || job.currentStatus === statusFilter;
        const enabledMatches =
          enabledFilter === "ALL" ||
          (enabledFilter === "ENABLED" && job.enabled) ||
          (enabledFilter === "DISABLED" && !job.enabled);
        return statusMatches && enabledMatches;
      }),
    [enabledFilter, jobs, statusFilter],
  );
  const selectedRow = jobs.find((job) => job.jobName === selectedJob) ?? filteredJobs[0] ?? null;

  useEffect(() => {
    if (!selectedJob && selectedRow) setSelectedJob(selectedRow.jobName);
  }, [selectedJob, selectedRow]);

  if (session?.role !== "ADMIN") return <Navigate to="/" replace />;

  return (
    <section className="space-y-6">
      <div className="flex flex-col justify-between gap-4 md:flex-row md:items-end">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[.22em] text-emerald-300">Operations</p>
          <h1 className="mt-2 text-3xl font-semibold text-white">Scheduled Jobs</h1>
          <p className="mt-3 max-w-3xl leading-7 text-slate-300">
            Monitor scheduled ingestion work, inspect recent outcomes, and launch a controlled run now.
          </p>
        </div>
        <button
          className="rounded-lg border border-slate-700 px-4 py-2 text-sm font-semibold text-slate-200 hover:border-emerald-300 hover:text-white"
          onClick={() => void monitorQuery.refetch()}
          type="button"
        >
          Refresh
        </button>
      </div>

      {notice && (
        <div role="status" className="rounded-lg border border-slate-700 bg-slate-900 px-4 py-3 text-sm text-slate-200">
          {notice}
        </div>
      )}

      <div className="grid gap-4 md:grid-cols-3">
        <label className="block text-sm font-medium text-slate-200">
          Status
          <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)} className="mt-2 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2">
            <option value="ALL">All statuses</option>
            <option value="RUNNING">Running</option>
            <option value="SUCCESS">Success</option>
            <option value="FAILED">Failed</option>
            <option value="SKIPPED">Skipped</option>
            <option value="IDLE">Idle</option>
          </select>
        </label>
        <label className="block text-sm font-medium text-slate-200">
          Runtime
          <select value={enabledFilter} onChange={(event) => setEnabledFilter(event.target.value)} className="mt-2 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2">
            <option value="ALL">Enabled and disabled</option>
            <option value="ENABLED">Enabled</option>
            <option value="DISABLED">Disabled</option>
          </select>
        </label>
        <div className="rounded-lg border border-slate-800 bg-slate-900/60 p-4">
          <p className="text-xs uppercase tracking-[.18em] text-slate-400">Jobs</p>
          <p className="mt-1 text-2xl font-semibold text-white">{filteredJobs.length}</p>
        </div>
      </div>

      <ul className="space-y-3 lg:hidden">
        {filteredJobs.map((job) => (
          <JobCard
            key={job.jobName}
            job={job}
            selected={selectedJob === job.jobName}
            onSelect={() => setSelectedJob(job.jobName)}
            onRun={() => runMutation.mutate(job.jobName)}
            runPending={runMutation.isPending}
            onToggleEnabled={() => enabledMutation.mutate({ jobName: job.jobName, enabled: !job.enabled })}
            onHistory={() => { setSelectedJob(job.jobName); setDetailMode("history"); }}
            onEvents={() => { setSelectedJob(job.jobName); setDetailMode("events"); }}
          />
        ))}
      </ul>
      <div className="hidden overflow-x-auto rounded-lg border border-slate-800 lg:block">
        <table className="min-w-[1100px] w-full divide-y divide-slate-800 text-sm">
          <thead className="bg-slate-900/80 text-left text-xs uppercase tracking-[.16em] text-slate-400">
            <tr>
              <th className="px-4 py-3">Job</th>
              <th className="px-4 py-3">State</th>
              <th className="px-4 py-3">Schedule</th>
              <th className="px-4 py-3">Current</th>
              <th className="px-4 py-3">Last run</th>
              <th className="px-4 py-3">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800 bg-slate-950">
            {filteredJobs.map((job) => (
              <tr key={job.jobName} className={selectedJob === job.jobName ? "bg-slate-900/80" : undefined}>
                <td className="px-4 py-4 align-top">
                  <button className="text-left font-semibold text-white hover:text-emerald-300" onClick={() => setSelectedJob(job.jobName)} type="button">
                    {job.jobName}
                  </button>
                  <p className="mt-1 text-xs text-slate-400">Source {job.dataSource ?? "-"}</p>
                </td>
                <td className="px-4 py-4 align-top">
                  <span className={job.enabled ? "text-emerald-200" : "text-amber-200"}>{job.enabled ? "Enabled" : "Disabled"}</span>
                  {job.latestError && <p className="mt-1 max-w-xs truncate text-xs text-rose-200">{job.latestError}</p>}
                </td>
                <td className="px-4 py-4 align-top">
                  <code className="text-xs text-slate-300">{job.cronExpression}</code>
                  <p className="mt-1 text-xs text-slate-400">{job.enabled ? `Next ${formatDate(job.nextRunAt)}` : "Schedule inactive"}</p>
                  {job.scheduleError && <p className="mt-1 text-xs text-amber-200">{job.scheduleError}</p>}
                </td>
                <td className="px-4 py-4 align-top">
                  <span className={`inline-flex rounded-md px-2 py-1 text-xs font-semibold ${statusClass(job.currentStatus)}`}>{job.currentStatus}</span>
                  <p className="mt-1 text-xs text-slate-400">{formatDuration(job.currentDurationSeconds)}</p>
                </td>
                <td className="px-4 py-4 align-top">
                  <RunSummary run={job.lastRun} />
                </td>
                <td className="px-4 py-4 align-top">
                  <div className="flex flex-wrap gap-2">
                    <button className="min-h-11 rounded-lg bg-emerald-400 px-3 py-2 text-xs font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-60" disabled={runMutation.isPending} onClick={() => runMutation.mutate(job.jobName)} type="button">
                      Run now
                    </button>
                    <button className="min-h-11 rounded-lg border border-slate-700 px-3 py-2 text-xs font-semibold text-slate-200 hover:border-emerald-300" onClick={() => enabledMutation.mutate({ jobName: job.jobName, enabled: !job.enabled })} type="button">
                      {job.enabled ? "Disable" : "Enable"}
                    </button>
                    <button className="min-h-11 rounded-lg border border-slate-700 px-3 py-2 text-xs font-semibold text-slate-200 hover:border-emerald-300" onClick={() => { setSelectedJob(job.jobName); setDetailMode("history"); }} type="button">
                      History
                    </button>
                    <button className="min-h-11 rounded-lg border border-slate-700 px-3 py-2 text-xs font-semibold text-slate-200 hover:border-emerald-300" onClick={() => { setSelectedJob(job.jobName); setDetailMode("events"); }} type="button">
                      Events
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {!monitorQuery.isLoading && !filteredJobs.length && <p className="rounded-lg border border-slate-800 p-6 text-sm text-slate-400">No jobs match these filters.</p>}

      <div className="grid gap-6 xl:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)]">
        <section className="rounded-lg border border-slate-800 bg-slate-900/50 p-5">
          <h2 className="text-lg font-semibold text-white">Run scope</h2>
          <div className="mt-4 grid gap-4">
            <label className="block text-sm text-slate-200">
              Symbols
              <input value={scope.symbols} onChange={(event) => setScope((current) => ({ ...current, symbols: event.target.value }))} placeholder="AAPL,MSFT" className="mt-2 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2" />
            </label>
            <label className="block text-sm text-slate-200">
              Exchange
              <input value={scope.exchange} onChange={(event) => setScope((current) => ({ ...current, exchange: event.target.value }))} placeholder="NASDAQ" className="mt-2 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2" />
            </label>
            <label className="block text-sm text-slate-200">
              Data types
              <input value={scope.dataTypes} onChange={(event) => setScope((current) => ({ ...current, dataTypes: event.target.value }))} placeholder="profile,quote,dcf" className="mt-2 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2" />
            </label>
          </div>
          {runStatusQuery.data && <RunStatusPanel status={runStatusQuery.data} />}
        </section>

        <section className="rounded-lg border border-slate-800 bg-slate-900/50 p-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 className="text-lg font-semibold text-white">{selectedRow?.jobName ?? "Job details"}</h2>
            <div className="flex gap-2">
              <button className={`rounded-lg px-3 py-2 text-xs font-semibold ${detailMode === "history" ? "bg-emerald-400 text-slate-950" : "border border-slate-700 text-slate-200"}`} onClick={() => setDetailMode("history")} type="button">History</button>
              <button className={`rounded-lg px-3 py-2 text-xs font-semibold ${detailMode === "events" ? "bg-emerald-400 text-slate-950" : "border border-slate-700 text-slate-200"}`} onClick={() => setDetailMode("events")} type="button">Events</button>
            </div>
          </div>
          <div className="mt-4">
            {detailMode === "history" ? (
              <HistoryList rows={historyQuery.data?.content ?? []} />
            ) : (
              <EventList rows={eventsQuery.data?.content ?? []} />
            )}
          </div>
        </section>
      </div>
    </section>
  );
}

function JobCard({
  job,
  selected,
  onSelect,
  onRun,
  runPending,
  onToggleEnabled,
  onHistory,
  onEvents,
}: {
  job: JobMonitorRow;
  selected: boolean;
  onSelect: () => void;
  onRun: () => void;
  runPending: boolean;
  onToggleEnabled: () => void;
  onHistory: () => void;
  onEvents: () => void;
}): JSX.Element {
  return (
    <li className={`rounded-lg border p-4 ${selected ? "border-emerald-400/60 bg-slate-900/80" : "border-slate-800 bg-slate-950"}`}>
      <button className="text-left font-semibold text-white hover:text-emerald-300" onClick={onSelect} type="button">
        {job.jobName}
      </button>
      <p className="mt-1 text-xs text-slate-400">Source {job.dataSource ?? "-"}</p>
      <dl className="mt-3 grid grid-cols-2 gap-3 text-sm">
        <div>
          <dt className="text-xs uppercase text-slate-400">State</dt>
          <dd className={job.enabled ? "text-emerald-200" : "text-amber-200"}>{job.enabled ? "Enabled" : "Disabled"}</dd>
        </div>
        <div>
          <dt className="text-xs uppercase text-slate-400">Current</dt>
          <dd><span className={`inline-flex rounded-md px-2 py-1 text-xs font-semibold ${statusClass(job.currentStatus)}`}>{job.currentStatus}</span><p className="mt-1 text-xs text-slate-400">{formatDuration(job.currentDurationSeconds)}</p></dd>
        </div>
        <div className="col-span-2">
          <dt className="text-xs uppercase text-slate-400">Schedule</dt>
          <dd><code className="text-xs text-slate-300">{job.cronExpression}</code><p className="mt-1 text-xs text-slate-400">{job.enabled ? `Next ${formatDate(job.nextRunAt)}` : "Schedule inactive"}</p></dd>
        </div>
        <div className="col-span-2">
          <dt className="text-xs uppercase text-slate-400">Last run</dt>
          <dd><RunSummary run={job.lastRun} /></dd>
        </div>
      </dl>
      {job.latestError && <p className="mt-2 text-xs text-rose-200">{job.latestError}</p>}
      <div className="mt-3 flex flex-wrap gap-2 border-t border-slate-800 pt-3">
        <button className="min-h-11 rounded-lg bg-emerald-400 px-3 py-2 text-xs font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-60" disabled={runPending} onClick={onRun} type="button">
          Run now
        </button>
        <button className="min-h-11 rounded-lg border border-slate-700 px-3 py-2 text-xs font-semibold text-slate-200 hover:border-emerald-300" onClick={onToggleEnabled} type="button">
          {job.enabled ? "Disable" : "Enable"}
        </button>
        <button className="min-h-11 rounded-lg border border-slate-700 px-3 py-2 text-xs font-semibold text-slate-200 hover:border-emerald-300" onClick={onHistory} type="button">
          History
        </button>
        <button className="min-h-11 rounded-lg border border-slate-700 px-3 py-2 text-xs font-semibold text-slate-200 hover:border-emerald-300" onClick={onEvents} type="button">
          Events
        </button>
      </div>
    </li>
  );
}

function RunStatusPanel({ status }: { status: JobRunStatus }): JSX.Element {
  return (
    <div className="mt-5 rounded-lg border border-slate-800 bg-slate-950 p-4">
      <div className="flex items-center justify-between gap-3">
        <span className={`inline-flex rounded-md px-2 py-1 text-xs font-semibold ${statusClass(status.status)}`}>{status.status}</span>
        <span className="text-xs text-slate-400">{formatDuration(status.elapsedSeconds)}</span>
      </div>
      <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
        <div><dt className="text-slate-400">Processed</dt><dd className="font-semibold text-white">{status.recordsProcessed ?? "-"}</dd></div>
        <div><dt className="text-slate-400">Errors</dt><dd className="font-semibold text-white">{status.errorCount}</dd></div>
        <div><dt className="text-slate-400">Symbols</dt><dd className="font-semibold text-white">{status.scopeSymbols ?? "-"}</dd></div>
        <div><dt className="text-slate-400">Data</dt><dd className="font-semibold text-white">{status.scopeDataTypes ?? "-"}</dd></div>
      </dl>
      {status.errorMessage && <p className="mt-3 text-sm text-rose-200">{status.errorMessage}</p>}
    </div>
  );
}

function HistoryList({ rows }: { rows: JobRunSummary[] }): JSX.Element {
  if (!rows.length) return <p className="text-sm text-slate-400">No history for this job yet.</p>;
  return (
    <div className="space-y-3">
      {rows.map((run) => (
        <div className="rounded-lg border border-slate-800 bg-slate-950 p-3" key={run.id}>
          <div className="flex items-center justify-between gap-3">
            <span className={`inline-flex rounded-md px-2 py-1 text-xs font-semibold ${statusClass(run.status)}`}>{run.status}</span>
            <span className="text-xs text-slate-400">{formatDate(run.startedAt)}</span>
          </div>
          <p className="mt-2 text-sm text-slate-300">Records {run.recordsProcessed ?? "-"}</p>
          {run.errorMessage && <p className="mt-1 text-sm text-rose-200">{run.errorMessage}</p>}
        </div>
      ))}
    </div>
  );
}

function EventList({ rows }: { rows: IngestionEvent[] }): JSX.Element {
  if (!rows.length) return <p className="text-sm text-slate-400">No ingestion events match this selection.</p>;
  return (
    <div className="space-y-3">
      {rows.map((event) => (
        <div className="rounded-lg border border-slate-800 bg-slate-950 p-3" key={event.id}>
          <div className="flex items-center justify-between gap-3">
            <span className={`inline-flex rounded-md px-2 py-1 text-xs font-semibold ${statusClass(event.status)}`}>{event.status}</span>
            <span className="text-xs text-slate-400">{formatDate(event.occurredAt)}</span>
          </div>
          <p className="mt-2 text-sm text-slate-300">{event.symbol ?? "-"} / {event.dataType} / {event.source ?? "-"}</p>
          {event.errorDetail && <p className="mt-1 text-sm text-rose-200">{event.errorDetail}</p>}
        </div>
      ))}
    </div>
  );
}
