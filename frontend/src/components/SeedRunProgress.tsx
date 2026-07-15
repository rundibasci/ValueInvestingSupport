import { useEffect } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { seedUniverseApi, type SeedRunOutcome } from "../api/seedUniverse";

const terminal = new Set(["SUCCESS", "PARTIAL_SUCCESS", "FAILED"]);

export function SeedRunProgress({ runId, onRunChange, onDismiss }: {
  runId: string;
  onRunChange: (id: string) => void;
  onDismiss: () => void;
}): JSX.Element {
  const client = useQueryClient();
  const run = useQuery({
    queryKey: ["seed-run", runId],
    queryFn: () => seedUniverseApi.run(runId),
    refetchInterval: (query) => {
      const data = query.state.data;
      return data && terminal.has(data.status) ? false : (data?.pollingIntervalMs ?? 1500);
    },
  });
  const done = run.data ? terminal.has(run.data.status) : false;
  const outcomes = useQuery({
    queryKey: ["seed-run", runId, "outcomes"],
    queryFn: () => seedUniverseApi.outcomes(runId),
    enabled: Boolean(run.data),
    refetchInterval: done ? false : 3000,
  });
  const retry = useMutation({
    mutationFn: () => seedUniverseApi.retryFailures(runId),
    onSuccess: (accepted) => onRunChange(accepted.seedRunId),
  });

  useEffect(() => {
    if (!done) return;
    void client.invalidateQueries({ queryKey: ["screener"] });
    void client.invalidateQueries({ queryKey: ["security-search"] });
  }, [client, done]);

  if (run.isPending) return <p role="status" className="rounded-xl border border-slate-700 bg-slate-900/60 p-4 text-sm text-slate-300">Loading seed progress…</p>;
  if (run.error || !run.data) return (
    <div role="alert" className="rounded-xl border border-rose-300/30 bg-rose-400/10 p-4 text-sm text-rose-100">
      {run.error instanceof Error ? run.error.message : "Seed progress is unavailable."}{" "}
      <button type="button" onClick={() => void run.refetch()} className="font-semibold underline">Retry</button>{" · "}
      <button type="button" onClick={onDismiss} className="font-semibold underline">Dismiss</button>
    </div>
  );

  const value = run.data.total ? Math.round((run.data.processed / run.data.total) * 100) : 0;
  return (
    <section className="rounded-2xl border border-slate-700 bg-slate-900/60 p-5" aria-label="Bulk seed progress">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div><h2 className="text-lg font-semibold text-white">Bulk seed: {run.data.status.replaceAll("_", " ").toLowerCase()}</h2>
          <p className="mt-1 text-sm text-slate-400">Run {runId} {run.data.currentSymbol ? `· processing ${run.data.currentSymbol}` : ""}</p></div>
        {done && <button type="button" onClick={onDismiss} className="text-sm font-semibold text-slate-300 underline">Dismiss</button>}
      </div>
      <div className="mt-4 h-3 overflow-hidden rounded-full bg-slate-800" role="progressbar" aria-valuemin={0} aria-valuemax={run.data.total} aria-valuenow={run.data.processed}>
        <div className="h-full bg-emerald-400 transition-all" style={{ width: `${value}%` }} />
      </div>
      <p className="mt-2 text-sm text-slate-300">{run.data.processed}/{run.data.total} processed · {run.data.succeeded} complete · {run.data.partiallySeeded} partial · {run.data.failed} failed</p>
      {run.data.terminalReason && <p className="mt-3 text-sm text-amber-100">{run.data.terminalReason}</p>}
      {(outcomes.data?.content.length ?? 0) > 0 && <OutcomeList rows={outcomes.data!.content} />}
      {done && run.data.failed > 0 && <button type="button" disabled={retry.isPending} onClick={() => retry.mutate()} className="mt-4 rounded-lg border border-amber-300/40 px-3 py-2 text-sm font-semibold text-amber-100 disabled:opacity-50">{retry.isPending ? "Starting retry…" : "Retry failed symbols only"}</button>}
      {retry.error && <p role="alert" className="mt-2 text-sm text-rose-100">{retry.error.message}</p>}
      <p className="mt-3 text-xs text-slate-500">Successful and partially seeded symbols are never included in a failure retry.</p>
    </section>
  );
}

function OutcomeList({ rows }: { rows: SeedRunOutcome[] }): JSX.Element {
  return <ul className="mt-4 max-h-64 space-y-2 overflow-y-auto text-sm">{rows.map((item) => (
    <li key={item.symbol} className="rounded-lg bg-slate-950/60 px-3 py-2 text-slate-300">
      <span className="font-semibold text-white">{item.symbol}</span> · {item.status.toLowerCase()}{item.source ? ` · ${item.source}` : ""}
      {(item.reason || item.error) && <p className="mt-1 text-xs text-slate-400">{item.reason ?? item.error}</p>}
      {item.fallbackReason && <p className="mt-1 text-xs text-amber-100">{item.fallbackReason}</p>}
    </li>
  ))}</ul>;
}
