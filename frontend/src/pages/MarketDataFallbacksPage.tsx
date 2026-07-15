import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Navigate } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import {
  marketDataFallbacksApi,
  type FallbackFilters,
  type MarketDataFallbackEvent,
} from "../api/marketDataFallbacks";

const emptyFilters: FallbackFilters = {
  symbol: "",
  operation: "",
  eventType: "",
  outcome: "",
  triggerReason: "",
  jobRunId: "",
  from: "",
  to: "",
};

function date(value: string | null): string {
  return value ? new Intl.DateTimeFormat("en-GB", { dateStyle: "medium", timeStyle: "medium" }).format(new Date(value)) : "-";
}

function outcomeClass(outcome: string): string {
  if (outcome === "SUCCESS") return "bg-emerald-400/15 text-emerald-200";
  if (outcome === "FAILED") return "bg-rose-400/15 text-rose-200";
  return "bg-amber-300/15 text-amber-100";
}

export function MarketDataFallbacksPage(): JSX.Element {
  const { session } = useAuth();
  const [draft, setDraft] = useState<FallbackFilters>(emptyFilters);
  const [filters, setFilters] = useState<FallbackFilters>(emptyFilters);
  const [page, setPage] = useState(0);

  const summary = useQuery({
    queryKey: ["market-data-fallbacks", "summary", filters],
    queryFn: () => marketDataFallbacksApi.summary(filters),
  });
  const events = useQuery({
    queryKey: ["market-data-fallbacks", "events", filters, page],
    queryFn: () => marketDataFallbacksApi.events(filters, page),
  });

  if (session?.role !== "ADMIN") return <Navigate to="/" replace />;

  const cards = [
    ["Attempts", summary.data?.totalAttempts ?? 0],
    ["Fallback success", summary.data?.successfulFallbacks ?? 0],
    ["Enrichment success", summary.data?.successfulEnrichments ?? 0],
    ["Failed", summary.data?.failedAttempts ?? 0],
    ["Rejected", summary.data?.rejectedAttempts ?? 0],
    ["Symbols", summary.data?.affectedSymbols ?? 0],
  ];

  return (
    <section className="space-y-6">
      <div>
        <p className="text-xs font-semibold uppercase tracking-[.22em] text-emerald-300">Market data operations</p>
        <h1 className="mt-2 text-3xl font-semibold text-white">Yahoo fallback analysis</h1>
        <p className="mt-3 max-w-4xl leading-7 text-slate-300">
          Inspect every Yahoo call initiated by the FMP wrapper. Successful fallback, optional enrichment,
          rejected values, and provider failures are tracked separately without storing provider payloads or credentials.
        </p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-6">
        {cards.map(([label, value]) => (
          <div key={label} className="rounded-lg border border-slate-800 bg-slate-900/60 p-4">
            <p className="text-xs uppercase tracking-[.16em] text-slate-500">{label}</p>
            <p className="mt-2 text-2xl font-semibold text-white">{value}</p>
          </div>
        ))}
      </div>

      <form
        className="grid gap-4 rounded-lg border border-slate-800 bg-slate-900/50 p-5 md:grid-cols-3"
        onSubmit={(event) => { event.preventDefault(); setPage(0); setFilters(draft); }}
      >
        <Filter label="Symbol" value={draft.symbol} placeholder="KO" onChange={(value) => setDraft({ ...draft, symbol: value })} />
        <Filter label="Operation" value={draft.operation} placeholder="profile, fundamentals, ratios, quote" onChange={(value) => setDraft({ ...draft, operation: value })} />
        <Select label="Event type" value={draft.eventType} onChange={(value) => setDraft({ ...draft, eventType: value })} options={["PRIMARY_PROVIDER_FALLBACK", "PRIMARY_PROVIDER_ENRICHMENT"]} />
        <Select label="Outcome" value={draft.outcome} onChange={(value) => setDraft({ ...draft, outcome: value })} options={["SUCCESS", "FAILED", "REJECTED"]} />
        <Filter label="Trigger" value={draft.triggerReason} placeholder="PLAN_RESTRICTION, MISSING_FIELD" onChange={(value) => setDraft({ ...draft, triggerReason: value })} />
        <Filter label="Job run ID" value={draft.jobRunId} placeholder="UUID" onChange={(value) => setDraft({ ...draft, jobRunId: value })} />
        <DateFilter label="From" value={draft.from} onChange={(value) => setDraft({ ...draft, from: value })} />
        <DateFilter label="To" value={draft.to} onChange={(value) => setDraft({ ...draft, to: value })} />
        <div className="flex items-end gap-2">
          <button type="submit" className="rounded-lg bg-emerald-400 px-4 py-2 text-sm font-semibold text-slate-950">Apply filters</button>
          <button type="button" className="rounded-lg border border-slate-700 px-4 py-2 text-sm text-slate-200" onClick={() => { setDraft(emptyFilters); setFilters(emptyFilters); setPage(0); }}>Clear</button>
        </div>
      </form>

      {(events.isError || summary.isError) && <p className="rounded-lg border border-rose-400/30 bg-rose-400/10 p-4 text-rose-200">Unable to load fallback diagnostics.</p>}

      <div className="overflow-x-auto rounded-lg border border-slate-800">
        <table className="min-w-[1250px] w-full divide-y divide-slate-800 text-sm">
          <thead className="bg-slate-900/80 text-left text-xs uppercase tracking-[.14em] text-slate-400">
            <tr><th className="px-4 py-3">Time / symbol</th><th className="px-4 py-3">Type</th><th className="px-4 py-3">Operation</th><th className="px-4 py-3">Trigger</th><th className="px-4 py-3">Outcome</th><th className="px-4 py-3">Fields</th><th className="px-4 py-3">Job</th><th className="px-4 py-3">Detail</th></tr>
          </thead>
          <tbody className="divide-y divide-slate-800 bg-slate-950">
            {(events.data?.content ?? []).map((event) => <EventRow key={event.id} event={event} />)}
          </tbody>
        </table>
        {!events.isLoading && !events.data?.content.length && <p className="p-6 text-sm text-slate-400">No Yahoo fallback attempts match these filters.</p>}
      </div>

      <div className="flex items-center justify-between text-sm text-slate-300">
        <span>{events.data?.totalElements ?? 0} events · last attempt {date(summary.data?.lastAttemptAt ?? null)}</span>
        <div className="flex gap-2">
          <button className="rounded-lg border border-slate-700 px-3 py-2 disabled:opacity-40" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Previous</button>
          <button className="rounded-lg border border-slate-700 px-3 py-2 disabled:opacity-40" disabled={!events.data || page + 1 >= events.data.totalPages} onClick={() => setPage((value) => value + 1)}>Next</button>
        </div>
      </div>
    </section>
  );
}

function EventRow({ event }: { event: MarketDataFallbackEvent }): JSX.Element {
  return (
    <tr>
      <td className="px-4 py-4 align-top"><strong className="text-white">{event.symbol}</strong><p className="mt-1 text-xs text-slate-500">{date(event.occurredAt)} · {event.durationMs}ms</p></td>
      <td className="px-4 py-4 align-top text-xs text-slate-300">{event.eventType.replace("PRIMARY_PROVIDER_", "")}</td>
      <td className="px-4 py-4 align-top"><span className="text-slate-200">{event.operation}</span><p className="mt-1 text-xs text-slate-500">{event.primaryProvider} → {event.fallbackProvider}</p></td>
      <td className="px-4 py-4 align-top"><span className="text-amber-100">{event.triggerReason}</span><p className="mt-1 text-xs text-slate-500">{event.primaryStatus ?? "-"}</p></td>
      <td className="px-4 py-4 align-top"><span className={`rounded-md px-2 py-1 text-xs font-semibold ${outcomeClass(event.outcome)}`}>{event.outcome}</span></td>
      <td className="px-4 py-4 align-top text-xs"><p className="text-slate-300">Missing: {event.missingFields ?? "-"}</p><p className="mt-1 text-emerald-200">Accepted: {event.acceptedFields ?? "-"}</p></td>
      <td className="px-4 py-4 align-top text-xs text-slate-400"><p>{event.jobName ?? "HTTP request"}</p><p className="mt-1 max-w-36 truncate">{event.jobRunId ?? "-"}</p></td>
      <td className="max-w-xs px-4 py-4 align-top text-xs text-rose-200">{event.errorDetail ?? "-"}</td>
    </tr>
  );
}

function Filter({ label, value, placeholder, onChange }: { label: string; value?: string; placeholder: string; onChange: (value: string) => void }): JSX.Element {
  return <label className="text-sm text-slate-200">{label}<input className="mt-2 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2" value={value ?? ""} placeholder={placeholder} onChange={(event) => onChange(event.target.value)} /></label>;
}

function Select({ label, value, options, onChange }: { label: string; value?: string; options: string[]; onChange: (value: string) => void }): JSX.Element {
  return <label className="text-sm text-slate-200">{label}<select className="mt-2 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2" value={value ?? ""} onChange={(event) => onChange(event.target.value)}><option value="">All</option>{options.map((option) => <option key={option} value={option}>{option}</option>)}</select></label>;
}

function DateFilter({ label, value, onChange }: { label: string; value?: string; onChange: (value: string) => void }): JSX.Element {
  return <label className="text-sm text-slate-200">{label}<input type="datetime-local" className="mt-2 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2" value={value ?? ""} onChange={(event) => onChange(event.target.value)} /></label>;
}
