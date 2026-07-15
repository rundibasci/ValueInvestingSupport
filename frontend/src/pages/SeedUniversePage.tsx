import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import { useMutation } from "@tanstack/react-query";
import { seedUniverseApi, type SeedResult } from "../api/seedUniverse";
import { useAuth } from "../auth/AuthProvider";
import { SeedRunProgress } from "../components/SeedRunProgress";

const tickerPattern = /^[A-Z][A-Z0-9.-]{0,14}$/;
const disclaimer =
  "Decision-support only, not investment advice (MiFID II). Fair value, margin of safety, recommendations, and scores are model outputs based on available data.";

const adminPacks = [
  {
    id: "starter",
    label: "Default starter universe",
    symbols: ["AAPL", "MSFT", "KO", "JNJ"],
  },
  {
    id: "quality",
    label: "US large-cap quality",
    symbols: ["AAPL", "MSFT", "GOOGL", "BRK.B", "V"],
  },
  {
    id: "dividend",
    label: "Dividend candidates",
    symbols: ["KO", "JNJ", "PG", "PEP", "MCD"],
  },
  {
    id: "value",
    label: "Value shortlist",
    symbols: ["BRK.B", "JPM", "XOM", "CVX", "PFE"],
  },
];

type Preview = {
  symbols: string[];
  duplicates: string[];
  invalid: string[];
};

function parseTickers(value: string): Preview {
  const seen = new Set<string>();
  const symbols: string[] = [];
  const duplicates: string[] = [];
  const invalid: string[] = [];

  value
    .split(/[\s,;]+/)
    .map((token) => token.trim())
    .filter(Boolean)
    .forEach((token) => {
      const normalized = token.toUpperCase();
      if (!tickerPattern.test(normalized)) {
        invalid.push(token);
        return;
      }
      if (seen.has(normalized)) {
        duplicates.push(normalized);
        return;
      }
      seen.add(normalized);
      symbols.push(normalized);
    });

  return { symbols, duplicates, invalid };
}

function money(value: number | null | undefined): string {
  return value == null
    ? "-"
    : new Intl.NumberFormat("en-US", {
        style: "currency",
        currency: "USD",
        maximumFractionDigits: 2,
      }).format(value);
}

function number(value: number | null | undefined): string {
  return value == null
    ? "-"
    : new Intl.NumberFormat("en-US", { maximumFractionDigits: 1 }).format(
        value,
      );
}

function percent(value: number | null | undefined): string {
  return value == null ? "-" : `${number(value)}%`;
}

function sourceLabel(source: string | null | undefined): string {
  if (!source) return "Unavailable";
  const lower = source.toLowerCase();
  if (lower.includes("fmp") && lower.includes("yahoo")) return "Mixed";
  if (lower.includes("yahoo")) return "Yahoo Finance";
  if (lower.includes("fmp")) return "FMP";
  return source;
}

function sourceClass(source: string | null | undefined): string {
  const label = sourceLabel(source);
  if (label === "FMP") return "bg-emerald-400/15 text-emerald-100 ring-1 ring-emerald-300/25";
  if (label === "Mixed") return "bg-sky-400/15 text-sky-100 ring-1 ring-sky-300/25";
  if (label === "Yahoo Finance") return "bg-amber-300/15 text-amber-100 ring-1 ring-amber-300/25";
  return "bg-slate-700 text-slate-200";
}

function statusClass(result: SeedResult): string {
  if (result.error) return "bg-rose-400/15 text-rose-100 ring-1 ring-rose-300/25";
  if (result.status === "seeded_partial") return "bg-sky-400/15 text-sky-100 ring-1 ring-sky-300/25";
  if (result.status === "unavailable") return "bg-amber-300/15 text-amber-100 ring-1 ring-amber-300/25";
  return "bg-emerald-400/15 text-emerald-100 ring-1 ring-emerald-300/25";
}

function hasDecisionContext(results: SeedResult[]): boolean {
  return results.some(
    (result) =>
      result.compositeFairValue != null ||
      result.marginOfSafety != null ||
      result.totalScore != null ||
      result.recommendation != null,
  );
}

function categoryCoverage(result: SeedResult): Array<[string, string]> {
  const label = sourceLabel(result.source);
  return [
    ["Profile", label],
    ["Fundamentals", label],
    ["Ratios", label],
    ["Quote", label],
    ["Valuation", result.compositeFairValue == null ? "Guardrail blocked" : "Local model"],
    ["Score", result.totalScore == null ? "Not returned" : "Local model"],
  ];
}

export function SeedUniversePage(): JSX.Element {
  const { session } = useAuth();
  const [tickers, setTickers] = useState("AAPL, MSFT, KO, JNJ");
  const [selectedPackId, setSelectedPackId] = useState(adminPacks[0].id);
  const [results, setResults] = useState<SeedResult[] | null>(null);
  const [runId, setRunId] = useState<string | null>(() => localStorage.getItem("seed-universe-run-id"));
  const preview = useMemo(() => parseTickers(tickers), [tickers]);
  const selectedPack =
    adminPacks.find((pack) => pack.id === selectedPackId) ?? adminPacks[0];
  const isAdmin = session?.role === "ADMIN";

  const csvMutation = useMutation({
    mutationFn: seedUniverseApi.seedCsv,
    onSuccess: (data) => acceptSubmission(data),
  });
  const packMutation = useMutation({
    mutationFn: seedUniverseApi.seedAdminPack,
    onSuccess: (data) => acceptSubmission(data),
  });
  const submitting = csvMutation.isPending || packMutation.isPending;
  const error = csvMutation.error ?? packMutation.error;

  function trackRun(id: string): void {
    setRunId(id);
    localStorage.setItem("seed-universe-run-id", id);
  }
  function acceptSubmission(data: Awaited<ReturnType<typeof seedUniverseApi.seedCsv>>): void {
    if (Array.isArray(data)) setResults(data);
    else trackRun(data.seedRunId);
  }

  function submitCsv(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault();
    setResults(null);
    if (preview.symbols.length === 0 || preview.invalid.length > 0) return;
    csvMutation.mutate(preview.symbols);
  }

  function submitPack(): void {
    setResults(null);
    packMutation.mutate(selectedPack.symbols);
  }

  return (
    <section className="space-y-6">
      <div className="flex flex-col justify-between gap-4 md:flex-row md:items-end">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[.22em] text-emerald-300">
            Shared research universe
          </p>
          <h1 className="mt-2 text-3xl font-semibold text-white">
            Seed Universe
          </h1>
          <p className="mt-3 max-w-3xl leading-7 text-slate-300">
            Seed symbols into platform-wide reference data so every
            authenticated user can discover them through screener, search,
            security detail, and review workflows.
          </p>
        </div>
        <Link
          to="/screener"
          className="rounded-lg border border-slate-700 px-4 py-2 text-sm font-medium text-slate-200 hover:border-emerald-300 hover:text-white"
        >
          Open screener
        </Link>
      </div>

      <section className="rounded-2xl border border-slate-800 bg-slate-900/60 p-5 sm:p-6">
        <h2 className="text-lg font-semibold text-white">Scope</h2>
        <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-300">
          Seeding creates or refreshes shared securities, profiles,
          fundamentals, ratios, quotes, valuations, and scores. It does not add
          symbols to your personal watchlist or portfolio; those actions stay
          user-owned and separate.
        </p>
      </section>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(20rem,.45fr)]">
        <form
          onSubmit={submitCsv}
          className="rounded-2xl border border-slate-800 bg-slate-900/50 p-5 sm:p-6"
        >
          <div>
            <h2 className="text-lg font-semibold text-white">CSV tickers</h2>
            <p className="mt-1 text-sm text-slate-400">
              Available to investors, advisors, and admins.
            </p>
          </div>
          <label className="mt-5 block text-sm font-medium text-slate-200">
            Ticker list
            <textarea
              value={tickers}
              onChange={(event) => setTickers(event.target.value)}
              rows={5}
              className="mt-2 w-full resize-y rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none transition placeholder:text-slate-600 focus:border-emerald-400 focus:ring-2 focus:ring-emerald-400/25"
              placeholder="AAPL, MSFT, KO"
            />
          </label>

          <PreviewPanel preview={preview} />

          <div className="mt-5 flex flex-wrap items-center gap-3">
            <button
              disabled={
                submitting ||
                preview.symbols.length === 0 ||
                preview.invalid.length > 0
              }
              className="rounded-lg bg-emerald-400 px-4 py-2.5 text-sm font-semibold text-slate-950 transition hover:bg-emerald-300 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {csvMutation.isPending ? "Seeding..." : "Seed CSV list"}
            </button>
            {error && (
              <p role="alert" className="text-sm text-rose-200">
                {error.message}
              </p>
            )}
          </div>
        </form>

        <section className="rounded-2xl border border-slate-800 bg-slate-900/50 p-5 sm:p-6">
          <h2 className="text-lg font-semibold text-white">Named packs</h2>
          {isAdmin ? (
            <>
              <p className="mt-1 text-sm text-slate-400">
                Admin-only packs for common research starting points.
              </p>
              <label className="mt-5 block text-sm font-medium text-slate-200">
                Pack
                <select
                  value={selectedPackId}
                  onChange={(event) => setSelectedPackId(event.target.value)}
                  className="mt-2 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-emerald-400"
                >
                  {adminPacks.map((pack) => (
                    <option key={pack.id} value={pack.id}>
                      {pack.label}
                    </option>
                  ))}
                </select>
              </label>
              <div className="mt-4 flex flex-wrap gap-2">
                {selectedPack.symbols.map((symbol) => (
                  <span
                    key={symbol}
                    className="rounded-full bg-slate-800 px-2.5 py-1 text-xs font-semibold text-slate-200"
                  >
                    {symbol}
                  </span>
                ))}
              </div>
              <button
                type="button"
                disabled={submitting}
                onClick={submitPack}
                className="mt-5 rounded-lg border border-emerald-400/40 px-4 py-2.5 text-sm font-semibold text-emerald-200 transition hover:bg-emerald-400/10 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {packMutation.isPending ? "Seeding pack..." : "Seed pack"}
              </button>
            </>
          ) : (
            <p className="mt-2 text-sm leading-6 text-slate-400">
              Named packs are hidden for non-admin roles until the backend quota
              and cost policy allows them. CSV seeding remains available for
              your research list.
            </p>
          )}
        </section>
      </div>

      {runId && <SeedRunProgress runId={runId} onRunChange={trackRun} onDismiss={() => { setRunId(null); localStorage.removeItem("seed-universe-run-id"); }} />}

      {results && <ResultsTable results={results} />}

      {results && hasDecisionContext(results) && (
        <p className="rounded-xl border border-slate-800 bg-slate-950/40 px-4 py-3 text-xs leading-5 text-slate-400">
          {disclaimer}
        </p>
      )}
    </section>
  );
}

function PreviewPanel({ preview }: { preview: Preview }): JSX.Element {
  return (
    <div className="mt-4 rounded-xl border border-slate-800 bg-slate-950/50 p-4">
      <div className="flex flex-col justify-between gap-2 sm:flex-row sm:items-center">
        <h3 className="text-sm font-semibold text-white">Preview</h3>
        <span className="text-xs text-slate-400">
          {preview.symbols.length} unique ticker
          {preview.symbols.length === 1 ? "" : "s"}
        </span>
      </div>
      {preview.symbols.length ? (
        <div className="mt-3 flex flex-wrap gap-2">
          {preview.symbols.map((symbol) => (
            <span
              key={symbol}
              className="rounded-full bg-emerald-400/10 px-2.5 py-1 text-xs font-semibold text-emerald-200"
            >
              {symbol}
            </span>
          ))}
        </div>
      ) : (
        <p className="mt-3 text-sm text-slate-500">No valid tickers yet.</p>
      )}
      {preview.duplicates.length > 0 && (
        <p className="mt-3 text-xs text-amber-100">
          Removed duplicates: {preview.duplicates.join(", ")}
        </p>
      )}
      {preview.invalid.length > 0 && (
        <p role="alert" className="mt-3 text-xs text-rose-100">
          Invalid entries: {preview.invalid.join(", ")}
        </p>
      )}
    </div>
  );
}

function ResultsTable({ results }: { results: SeedResult[] }): JSX.Element {
  const successCount = results.filter((result) => !result.error && result.status !== "seeded_partial").length;
  const partialCount = results.filter((result) => result.status === "seeded_partial").length;
  return (
    <section className="overflow-hidden rounded-2xl border border-slate-800 bg-slate-900/50">
      <div className="flex flex-col justify-between gap-3 border-b border-slate-800 px-5 py-4 sm:flex-row sm:items-center">
        <div>
          <h2 className="text-lg font-semibold text-white">Seed results</h2>
          <p className="mt-1 text-sm text-slate-400">
            {successCount} fully seeded · {partialCount} partially seeded · {results.length - successCount - partialCount} failed or unavailable.
          </p>
        </div>
        <Link
          to="/screener"
          className="text-sm font-semibold text-emerald-300 hover:text-emerald-200"
        >
          Continue in screener
        </Link>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[1180px] text-left text-sm">
          <thead className="bg-slate-950/50 text-xs uppercase tracking-wide text-slate-400">
            <tr>
              <th className="px-4 py-3">Company</th>
              <th className="px-4 py-3">Context</th>
              <th className="px-4 py-3">Price</th>
              <th className="px-4 py-3">Fair value</th>
              <th className="px-4 py-3">MoS</th>
              <th className="px-4 py-3">Score</th>
              <th className="px-4 py-3">Recommendation</th>
              <th className="px-4 py-3">Source</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800">
            {results.map((result) => (
              <tr key={result.symbol} className="align-top text-slate-200">
                <td className="px-4 py-4">
                  <span className="block font-semibold text-white">
                    {result.companyName ?? result.symbol}
                  </span>
                  <span className="text-xs font-medium text-emerald-300">
                    {result.symbol}
                  </span>
                  {result.description && (
                    <p className="mt-2 max-w-xs text-xs leading-5 text-slate-400">
                      {result.description}
                    </p>
                  )}
                </td>
                <td className="px-4 py-4 text-slate-300">
                  <span className="block">{result.sector ?? "-"}</span>
                  <span className="text-xs text-slate-500">
                    {[result.exchange, result.country].filter(Boolean).join(" / ") ||
                      "-"}
                  </span>
                </td>
                <td className="px-4 py-4">{money(result.currentPrice)}</td>
                <td className="px-4 py-4">{money(result.compositeFairValue)}</td>
                <td className="px-4 py-4">{percent(result.marginOfSafety)}</td>
                <td className="px-4 py-4">{number(result.totalScore)}</td>
                <td className="px-4 py-4">{result.recommendation ?? "-"}</td>
                <td className="px-4 py-4">
                  <span
                    className={`rounded-full px-2.5 py-1 text-xs font-semibold ${sourceClass(
                      result.source,
                    )}`}
                  >
                    {sourceLabel(result.source)}
                  </span>
                  <details className="mt-2 text-xs text-slate-400">
                    <summary className="cursor-pointer text-slate-300">
                      Coverage
                    </summary>
                    <dl className="mt-2 grid grid-cols-2 gap-x-3 gap-y-1">
                      {categoryCoverage(result).map(([label, value]) => (
                        <div key={label} className="contents">
                          <dt>{label}</dt>
                          <dd className="text-slate-300">{value}</dd>
                        </div>
                      ))}
                    </dl>
                    {result.refreshedAt && (
                      <p className="mt-2">Refreshed {result.refreshedAt}</p>
                    )}
                    {result.fallbackReason && (
                      <p className="mt-2 text-amber-100">
                        {result.fallbackReason}
                      </p>
                    )}
                  </details>
                </td>
                <td className="px-4 py-4">
                  <span
                    className={`rounded-full px-2.5 py-1 text-xs font-semibold ${statusClass(
                      result,
                    )}`}
                  >
                    {result.error ?? result.status ?? "seeded"}
                  </span>
                  {result.reason && <p className="mt-2 max-w-xs text-xs leading-5 text-sky-100">{result.reason}</p>}
                </td>
                <td className="space-y-2 px-4 py-4">
                  {!result.error ? (
                    <>
                      <Link
                        className="block font-semibold text-emerald-300 hover:text-emerald-200"
                        to={`/securities/${encodeURIComponent(result.symbol)}`}
                      >
                        Detail
                      </Link>
                      <Link
                        className="block font-semibold text-emerald-300 hover:text-emerald-200"
                        to={`/securities/${encodeURIComponent(result.symbol)}/review`}
                      >
                        Review
                      </Link>
                    </>
                  ) : (
                    <span className="text-xs text-slate-500">No handoff</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
