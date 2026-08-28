import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  RateLimitError,
  ThesisDisabledError,
  thesisApi,
  type EvidenceField,
  type Thesis,
  type ThesisEvidence,
} from "../api/thesis";

const MIFID_DISCLAIMER = "This is a decision-support tool, not investment advice (MiFID II).";

/** Maps each allowed evidenceFields value to the review-page section id it substantiates
 * (plan.md → Group 2.4). Exported in case the admin review-queue page ever needs the same
 * mapping — no other consumer exists yet. */
export const EVIDENCE_FIELD_SECTION: Record<EvidenceField, string> = {
  marketPrice: "valuation",
  intrinsicValue: "valuation",
  marginOfSafetyPercent: "valuation",
  valueScore: "quality",
  dividendYieldPercent: "dividends",
  payoutRatioPercent: "dividends",
  netDebtToEbitda: "debt",
  revenueTrend: "earnings",
  earningsTrend: "earnings",
  freeCashFlowTrend: "cash",
  dataQuality: "source",
  deterministicWarnings: "risk",
};

const classificationLabel = (value: string | null | undefined): string =>
  (value || "INSUFFICIENT_DATA").replace(/_/g, " ").toLowerCase();

function ClassificationBadge({ value }: { value: string | null | undefined }): JSX.Element {
  const normalized = value || "INSUFFICIENT_DATA";
  const classes =
    normalized === "POTENTIALLY_UNDERVALUED"
      ? "bg-emerald-300/15 text-emerald-100"
      : normalized === "POTENTIALLY_OVERVALUED"
        ? "bg-rose-400/15 text-rose-100"
        : normalized === "FAIRLY_VALUED"
          ? "bg-amber-300/15 text-amber-100"
          : "bg-slate-700/60 text-slate-200";
  return <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${classes}`}>{classificationLabel(normalized)}</span>;
}

function Disclaimer(): JSX.Element {
  return <p className="mt-4 rounded-lg border border-amber-300/20 bg-amber-300/5 p-3 text-xs leading-5 text-amber-100">{MIFID_DISCLAIMER}</p>;
}

function EvidenceList({ items, symbol }: { items: ThesisEvidence[]; symbol: string }): JSX.Element {
  if (!items.length) return <p className="text-sm text-slate-500">None reported.</p>;
  return (
    <ul className="space-y-2 text-sm leading-6 text-slate-200">
      {items.map((item, index) => (
        <li key={`${index}-${item.claim}`} className="rounded-lg border border-slate-800 bg-slate-950/40 p-3">
          <p>{item.claim}</p>
          <p className="mt-1 flex flex-wrap gap-x-2 gap-y-1 text-xs text-slate-500">
            Evidence:{" "}
            {item.evidenceFields.map((field) => (
              <a key={field} href={`/securities/${symbol}/review#${EVIDENCE_FIELD_SECTION[field]}`} className="text-emerald-300 underline">
                {field}
              </a>
            ))}
          </p>
        </li>
      ))}
    </ul>
  );
}

function StringList({ items }: { items: string[] }): JSX.Element {
  if (!items.length) return <p className="text-sm text-slate-500">None reported.</p>;
  return (
    <ul className="list-disc space-y-1 pl-5 text-sm leading-6 text-slate-200">
      {items.map((item, index) => (
        <li key={index}>{item}</li>
      ))}
    </ul>
  );
}

function ProvenanceInspector({ thesis }: { thesis: Thesis }): JSX.Element {
  return (
    <details className="mt-4 rounded-lg border border-slate-800 bg-slate-950/40 p-3 text-sm text-slate-300">
      <summary className="cursor-pointer font-semibold text-slate-200">Model & prompt details</summary>
      <dl className="mt-3 grid gap-2 sm:grid-cols-2">
        <div><dt className="text-xs uppercase tracking-wide text-slate-500">Model</dt><dd>{thesis.modelId || "Unavailable"}</dd></div>
        <div><dt className="text-xs uppercase tracking-wide text-slate-500">Model version</dt><dd>{thesis.modelVersion || "Unavailable"}</dd></div>
        <div><dt className="text-xs uppercase tracking-wide text-slate-500">Prompt version</dt><dd>{thesis.promptVersion || "Unavailable"}</dd></div>
        <div><dt className="text-xs uppercase tracking-wide text-slate-500">Generated at</dt><dd>{thesis.generatedAt || "Unavailable"}</dd></div>
      </dl>
      <pre className="mt-3 max-h-64 overflow-auto rounded bg-slate-950 p-3 text-xs text-slate-400">{JSON.stringify(thesis, null, 2)}</pre>
    </details>
  );
}

export function ThesisPanel({ symbol }: { symbol: string }): JSX.Element {
  const client = useQueryClient();
  const [runId, setRunId] = useState<string | null>(null);
  const [runError, setRunError] = useState<string | null>(null);

  const latest = useQuery({
    queryKey: ["thesis", symbol],
    queryFn: () => thesisApi.latest(symbol),
    // Self-heals a page reload mid-generation: there is no way to recover the in-flight
    // request's thesisRunId from this endpoint alone (ThesisResponse exposes the row's own
    // id, not requestId), so falling back to polling "latest" itself while status is
    // GENERATING covers that case without needing the dedicated run-status endpoint.
    refetchInterval: (query) => (query.state.data?.status === "GENERATING" ? 3000 : false),
  });

  const runStatus = useQuery({
    queryKey: ["thesis-run", symbol, runId],
    queryFn: () => thesisApi.status(symbol, runId!),
    enabled: Boolean(runId),
    refetchInterval: (query) => (query.state.data && query.state.data.status !== "GENERATING" ? false : 1500),
  });

  const done = runStatus.data ? runStatus.data.status !== "GENERATING" : false;
  useEffect(() => {
    if (!done) return;
    if (runStatus.data?.status === "FAILED" && runStatus.data.errorCode) setRunError(runStatus.data.errorCode);
    void client.invalidateQueries({ queryKey: ["thesis", symbol] });
  }, [client, symbol, done, runStatus.data]);

  const generate = useMutation({
    mutationFn: () => thesisApi.generate(symbol),
    onSuccess: (accepted) => {
      setRunError(null);
      setRunId(accepted.thesisRunId);
    },
  });

  if (latest.isPending) return <p role="status" className="rounded-xl border border-slate-700 bg-slate-900/60 p-4 text-sm text-slate-300">Loading AI thesis…</p>;
  if (latest.error) {
    return (
      <div role="alert" className="rounded-xl border border-rose-300/30 bg-rose-400/10 p-4 text-sm text-rose-100">
        {latest.error instanceof Error ? latest.error.message : "AI thesis status is unavailable."}{" "}
        <button type="button" onClick={() => void latest.refetch()} className="font-semibold underline">Retry</button>
      </div>
    );
  }

  const thesis = latest.data;
  const generating = thesis?.status === "GENERATING" || (Boolean(runId) && !done);

  if (generating) {
    return (
      <div role="status" className="rounded-xl border border-slate-700 bg-slate-900/60 p-5">
        <p className="text-sm font-semibold text-white">Generating AI thesis…</p>
        <p className="mt-1 text-sm text-slate-400">This calls a live model and can take a few seconds.</p>
      </div>
    );
  }

  // Rendered inline wherever a generate/regenerate attempt can fail, rather than replacing
  // the whole panel — a rate-limited regenerate on an existing ready/stale thesis must not
  // hide that thesis's content behind the error.
  const generateError = generate.isError ? (
    generate.error instanceof RateLimitError ? (
      <p role="alert" className="mt-2 rounded-lg border border-amber-300/30 bg-amber-400/10 p-3 text-sm text-amber-100">
        Daily thesis generation limit reached ({generate.error.limit}/day). Try again after {generate.error.resetsAt}.
      </p>
    ) : generate.error instanceof ThesisDisabledError ? (
      <p className="mt-2 rounded-lg border border-slate-700 bg-slate-950/40 p-3 text-sm text-slate-300">{generate.error.message}</p>
    ) : (
      <p role="alert" className="mt-2 text-sm text-rose-100">{generate.error instanceof Error ? generate.error.message : "Generation failed."}</p>
    )
  ) : null;

  if (!thesis) {
    return (
      <div className="rounded-xl border border-slate-700 bg-slate-900/60 p-5">
        <p className="text-sm text-slate-300">No AI thesis has been generated for {symbol} yet.</p>
        <button
          type="button"
          onClick={() => generate.mutate()}
          disabled={generate.isPending}
          className="mt-4 rounded-lg bg-emerald-400 px-4 py-2 text-sm font-semibold text-slate-950 disabled:opacity-50"
        >
          {generate.isPending ? "Starting…" : "Generate AI Thesis"}
        </button>
        {generateError}
      </div>
    );
  }

  const output = thesis.output;

  if (thesis.status === "HUMAN_REVIEW_PENDING") {
    return (
      <div className="space-y-4">
        <div role="alert" className="rounded-xl border border-rose-300/30 bg-rose-400/10 p-4 text-sm leading-6 text-rose-100">
          <p className="font-semibold">This thesis is flagged for human review — do not treat it as a finished recommendation.</p>
        </div>
        {output?.summary && <p className="text-sm leading-6 text-slate-200">{output.summary}</p>}
        <div>
          <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-400">Key risks</h4>
          <div className="mt-2"><StringList items={output?.keyRisks ?? []} /></div>
        </div>
        {output?.dataWarnings.length ? (
          <p className="rounded-lg border border-amber-300/20 bg-amber-300/5 p-3 text-sm leading-6 text-amber-100">{output.dataWarnings.join(" ")}</p>
        ) : null}
        <ProvenanceInspector thesis={thesis} />
        <Disclaimer />
      </div>
    );
  }

  if (thesis.status === "FAILED") {
    return (
      <div className="space-y-3">
        <div role="alert" className="rounded-xl border border-rose-300/30 bg-rose-400/10 p-4 text-sm leading-6 text-rose-100">
          <p className="font-semibold">AI thesis generation failed.</p>
          <p className="mt-1">{output?.dataWarnings.join(" ") || "No further detail is available."}{runError ? ` (${runError})` : ""}</p>
        </div>
        <button type="button" onClick={() => generate.mutate()} disabled={generate.isPending} className="rounded-lg border border-slate-700 px-4 py-2 text-sm font-semibold text-slate-200 disabled:opacity-50">
          {generate.isPending ? "Retrying…" : "Retry"}
        </button>
        {generateError}
        <ProvenanceInspector thesis={thesis} />
      </div>
    );
  }

  // READY (possibly also stale)
  return (
    <div className="space-y-5">
      {thesis.stale && (
        <div className="rounded-lg border border-amber-300/30 bg-amber-400/10 p-3 text-sm text-amber-100">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p>This thesis was generated before the latest valuation/score refresh.</p>
            <button type="button" onClick={() => generate.mutate()} disabled={generate.isPending} className="rounded-lg border border-amber-300/50 px-3 py-1.5 text-xs font-semibold text-amber-100 disabled:opacity-50">
              {generate.isPending ? "Regenerating…" : "Regenerate"}
            </button>
          </div>
          {generateError}
        </div>
      )}
      <div className="flex flex-wrap items-center gap-3">
        <ClassificationBadge value={output?.classification} />
        {output?.confidence != null && <span className="text-sm text-slate-400">Confidence: {Math.round(output.confidence * 100)}%</span>}
      </div>
      {output?.summary && <p className="text-sm leading-6 text-slate-200">{output.summary}</p>}
      <div className="grid gap-5 sm:grid-cols-2">
        <div>
          <h4 className="text-xs font-semibold uppercase tracking-wide text-emerald-300">Bull case</h4>
          <div className="mt-2"><EvidenceList items={output?.bullCase ?? []} symbol={symbol} /></div>
        </div>
        <div>
          <h4 className="text-xs font-semibold uppercase tracking-wide text-rose-300">Bear case</h4>
          <div className="mt-2"><EvidenceList items={output?.bearCase ?? []} symbol={symbol} /></div>
        </div>
      </div>
      <div className="grid gap-5 sm:grid-cols-3">
        <div>
          <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-400">Key risks</h4>
          <div className="mt-2"><StringList items={output?.keyRisks ?? []} /></div>
        </div>
        <div>
          <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-400">Key assumptions</h4>
          <div className="mt-2"><StringList items={output?.keyAssumptions ?? []} /></div>
        </div>
        <div>
          <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-400">Invalidation conditions</h4>
          <div className="mt-2"><StringList items={output?.invalidationConditions ?? []} /></div>
        </div>
      </div>
      {output?.dataWarnings.length ? (
        <p className="rounded-lg border border-amber-300/20 bg-amber-300/5 p-3 text-sm leading-6 text-amber-100">{output.dataWarnings.join(" ")}</p>
      ) : null}
      <ProvenanceInspector thesis={thesis} />
      <Disclaimer />
    </div>
  );
}
