import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { useState } from "react";
import { portfolioAnalysisApi } from "../api/portfolioAnalysis";

const terminal = new Set(["COMPLETE", "PARTIAL", "FAILED"]);
const buttonClass = "rounded-lg bg-emerald-400 px-4 py-2 text-sm font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-50";

export function PortfolioAnalysisPanel({ portfolioId, importId, compact = false }: { portfolioId: string; importId?: string; compact?: boolean }): JSX.Element {
  const client = useQueryClient();
  const storageKey = `portfolio-analysis-run:${portfolioId}`;
  const [runId, setRunId] = useState<string | null>(() => localStorage.getItem(storageKey));
  const start = useMutation({
    mutationFn: () => portfolioAnalysisApi.start(portfolioId, importId),
    onSuccess: (accepted) => { localStorage.setItem(storageKey, accepted.analysisRunId); setRunId(accepted.analysisRunId); void client.invalidateQueries({ queryKey: ["portfolio-analysis", portfolioId] }); },
  });
  const status = useQuery({
    queryKey: ["portfolio-analysis", portfolioId, runId],
    queryFn: () => portfolioAnalysisApi.status(portfolioId, runId!),
    enabled: Boolean(runId),
    refetchInterval: (query) => terminal.has(query.state.data?.status || "") ? false : (query.state.data?.pollingIntervalMs || 1500),
  });
  const outcomes = useQuery({ queryKey: ["portfolio-analysis", portfolioId, runId, "outcomes"], queryFn: () => portfolioAnalysisApi.outcomes(portfolioId, runId!), enabled: Boolean(runId && status.data), refetchInterval: terminal.has(status.data?.status || "") ? false : 2000 });
  const retry = useMutation({ mutationFn: () => portfolioAnalysisApi.retry(portfolioId, runId!), onSuccess: (accepted) => { localStorage.setItem(storageKey, accepted.analysisRunId); setRunId(accepted.analysisRunId); void client.invalidateQueries({ queryKey: ["portfolio-analysis", portfolioId] }); } });
  const run = status.data;
  const error = start.error instanceof Error ? start.error : status.error instanceof Error ? status.error : null;
  const progress = run?.total ? Math.round(run.processed * 100 / run.total) : 0;
  return <section className={compact ? "mt-3 rounded-lg border border-slate-700 bg-slate-950/50 p-3" : "rounded-2xl border border-emerald-400/20 bg-slate-900/50 p-5 sm:p-6"} aria-label="Portfolio in-depth analysis">
    <div className="flex flex-wrap items-center justify-between gap-3"><div><h2 className={compact ? "font-semibold text-white" : "text-xl font-semibold text-white"}>In-depth portfolio analysis</h2><p className="mt-1 text-sm text-slate-400">Refresh shared research, calculations, and portfolio measurements. Cash stays in allocation totals and is never valued as a security.</p></div><button type="button" className={buttonClass} disabled={start.isPending || Boolean(run && !terminal.has(run.status))} onClick={() => start.mutate()}>{start.isPending ? "Starting…" : run ? "Run analysis again" : "Seed and analyze portfolio"}</button></div>
    {error && <p role="alert" className="mt-3 text-sm text-rose-200">{error.message}</p>}
    {run && <div className="mt-4"><div className="flex flex-wrap justify-between gap-2 text-sm"><span className="font-semibold text-emerald-200">{run.status} · {run.phase.replaceAll("_", " ")}{run.currentSymbol ? ` · ${run.currentSymbol}` : ""}</span><span className="text-slate-400">{run.processed}/{run.total} processed · {run.succeeded} complete · {run.partial} partial · {run.failed} failed</span></div><div className="mt-2 h-2 overflow-hidden rounded bg-slate-800"><div className="h-full bg-emerald-400 transition-all" style={{ width: `${progress}%` }} /></div><p className="mt-2 text-xs text-slate-500">Analysis {run.analysisVersion} · updated {new Date(run.updatedAt).toLocaleString()}{run.analyticsSnapshotId ? " · portfolio measurements refreshed" : ""}</p>{run.terminalReason && <p className="mt-2 text-sm text-amber-200">{run.terminalReason}</p>}</div>}
    {outcomes.data?.content.length ? <div className="mt-4 overflow-x-auto"><table className="w-full min-w-[42rem] text-left text-sm"><thead className="text-xs uppercase text-slate-500"><tr><th className="py-2">Security</th><th>Status</th><th>Source / freshness</th><th>Availability</th><th>Research</th></tr></thead><tbody className="divide-y divide-slate-800">{outcomes.data.content.map((item) => <tr key={item.symbol}><td className="py-2 font-semibold text-white">{item.symbol}</td><td className={item.status === "FAILED" ? "text-rose-200" : item.status === "PARTIAL" ? "text-amber-200" : "text-emerald-200"}>{item.status}</td><td className="text-slate-300">{item.source || "Unavailable"}<span className="block text-xs text-slate-500">{item.refreshedAt || "No refresh date"} · {item.calculationVersion}</span>{item.sourceLastPrice != null && <span className="block text-xs text-slate-400">Broker {item.sourceLastPrice.toLocaleString()} → platform {item.refreshedPrice?.toLocaleString() ?? "—"}{item.priceVariancePercent != null ? ` (${item.priceVariancePercent.toFixed(2)}%)` : ""}</span>}{item.sourceBaseValue != null && <span className="block text-xs text-slate-500">Broker base value {item.sourceBaseValue.toLocaleString()}</span>}</td><td className="max-w-xs text-xs text-slate-400">{item.errorMessage || item.reason || item.fallbackReason || "Available"}</td><td>{item.reviewPath ? <Link to={item.reviewPath} className="text-emerald-200 underline">Full review</Link> : <span className="text-slate-600">Unavailable</span>}</td></tr>)}</tbody></table></div> : null}
    {run && (run.partial > 0 || run.failed > 0) && terminal.has(run.status) && <button type="button" disabled={retry.isPending} onClick={() => retry.mutate()} className="mt-4 text-sm font-semibold text-amber-200 underline">{retry.isPending ? "Retrying…" : "Retry failed/partial"}</button>}
    <p className="mt-4 text-xs leading-5 text-slate-500">Fair values, margins of safety, scores, risk labels, and rebalancing diagnostics are decision support, not personalized investment advice. Review assumptions, source dates, coverage, and model guardrails.</p>
  </section>;
}
