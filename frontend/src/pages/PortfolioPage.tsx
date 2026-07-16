import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Pie, PieChart, ResponsiveContainer, Cell, Tooltip } from "recharts";
import { professionalApi } from "../api/professional";
import { watchlistApi, type WatchlistItem } from "../api/watchlist";
import { useAuth } from "../auth/AuthProvider";
import { PortfolioImportPanel } from "../components/PortfolioImportPanel";
import { PortfolioAnalysisPanel } from "../components/PortfolioAnalysisPanel";
import {
  portfolioApi,
  type BenchmarkComparison,
  type Holding,
  type HoldingConcentration,
  type LiquidityResult,
  type Portfolio,
  type PortfolioAnalytics,
  type PortfolioDetail,
  type PortfolioPreconditions,
  type Simulation,
  type SimulationInput,
} from "../api/portfolio";

const inputClass =
  "mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-emerald-400";
const buttonClass =
  "rounded-lg bg-emerald-400 px-4 py-2.5 text-sm font-semibold text-slate-950 transition hover:bg-emerald-300 disabled:cursor-not-allowed disabled:opacity-50";
const palette = [
  "#34d399",
  "#60a5fa",
  "#fbbf24",
  "#f472b6",
  "#a78bfa",
  "#fb7185",
];
const defaults: SimulationInput = {
  budget: 100000,
  maxStockPercent: 10,
  maxSectorPercent: 25,
  maxCountryPercent: 60,
  minimumMarginOfSafety: 15,
  minimumDividendYield: 0,
};
const maxHoldingQuantity = 1_000_000_000;

function money(value: number | null | undefined): string {
  return value == null
    ? "—"
    : new Intl.NumberFormat("en-US", {
        style: "currency",
        currency: "USD",
        maximumFractionDigits: 0,
      }).format(value);
}
function percent(value: number | null | undefined): string {
  return value == null ? "—" : `${Number(value).toFixed(1)}%`;
}
function ratio(value: number | null | undefined): string {
  return value == null ? "N/A" : Number(value).toFixed(1);
}
function compactMoney(value: number | null | undefined): string {
  return value == null
    ? "N/A"
    : new Intl.NumberFormat("en-US", {
        style: "currency",
        currency: "USD",
        notation: "compact",
        maximumFractionDigits: 1,
      }).format(value);
}
function label(value: string | null | undefined): string {
  return value
    ? value
        .toLowerCase()
        .split("_")
        .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
        .join(" ")
    : "Unavailable";
}
function statusClass(status: string | null | undefined): string {
  const normalized = status?.toLowerCase() ?? "";
  if (
    normalized.includes("illiquid") ||
    normalized.includes("concentrated") ||
    normalized.includes("must")
  )
    return "border-rose-300/30 bg-rose-400/10 text-rose-100";
  if (
    normalized.includes("moderate") ||
    normalized.includes("immaterial") ||
    normalized.includes("could")
  )
    return "border-amber-300/30 bg-amber-300/10 text-amber-100";
  if (
    normalized.includes("liquid") ||
    normalized.includes("normal") ||
    normalized.includes("hold")
  )
    return "border-emerald-300/30 bg-emerald-400/10 text-emerald-100";
  return "border-slate-600 bg-slate-800/80 text-slate-200";
}
function ErrorNotice({ error }: { error: unknown }): JSX.Element | null {
  return error instanceof Error ? (
    <p
      role="alert"
      className="rounded-lg border border-rose-300/30 bg-rose-400/10 p-3 text-sm text-rose-100"
    >
      {error.message}
    </p>
  ) : null;
}

function ReadinessPanel({
  readiness,
  loading,
  error,
  retry,
}: {
  readiness?: PortfolioPreconditions;
  loading: boolean;
  error: unknown;
  retry: () => void;
}): JSX.Element {
  if (loading) {
    return <p role="status" className="mt-5 text-sm text-slate-400">Checking portfolio readiness…</p>;
  }
  if (error && !readiness) {
    return (
      <div role="alert" className="mt-5 rounded-lg border border-amber-300/30 bg-amber-300/10 p-4 text-sm text-amber-100">
        Readiness could not be checked. Backend validation will still run when you submit.{" "}
        <button type="button" onClick={retry} className="font-semibold underline">Retry</button>
      </div>
    );
  }
  if (!readiness) return <></>;
  const recovery = (action: string): JSX.Element => {
    if (action === "WATCHLIST") return <span className="flex gap-3"><Link to="/watchlist" className="font-semibold text-emerald-200 underline">Open watchlist</Link><Link to="/screener" className="font-semibold text-emerald-200 underline">Open screener</Link></span>;
    if (action === "SCREENER") return <Link to="/screener" className="font-semibold text-emerald-200 underline">Open screener</Link>;
    if (action === "HOLDINGS") return <a href="#portfolio-holdings" className="font-semibold text-emerald-200 underline">Edit holdings</a>;
    return <a href="#simulation-constraints" className="font-semibold text-emerald-200 underline">Review constraints</a>;
  };
  return (
    <div className="mt-5 rounded-xl border border-slate-700 bg-slate-950/60 p-4" aria-label="Simulation readiness">
      <div className="flex flex-wrap gap-3 text-sm">
        <span className={readiness.simulationAvailable ? "text-emerald-200" : "text-amber-100"}>
          Simulation: {readiness.simulationAvailable ? "ready" : "not ready"}
        </span>
        <span className={readiness.rebalanceAvailable ? "text-emerald-200" : "text-amber-100"}>
          Rebalance: {readiness.rebalanceAvailable ? "ready" : "not ready"}
        </span>
        <span className="text-slate-400">{readiness.eligibleCandidateCount} eligible of {readiness.watchlistCount} watchlist companies</span>
      </div>
      {readiness.diagnostics.length > 0 && (
        <ul className="mt-3 space-y-2 text-sm text-slate-300">
          {readiness.diagnostics.map((item) => (
            <li key={item.code} className="flex flex-wrap items-baseline justify-between gap-2">
              <span>{item.message}</span>
              {recovery(item.recoveryAction)}
            </li>
          ))}
        </ul>
      )}
      {Object.keys(readiness.exclusionCounts).length > 0 && (
        <p className="mt-3 text-xs text-slate-400">
          Exclusions: {Object.entries(readiness.exclusionCounts).map(([reason, count]) => `${label(reason)} (${count})`).join(" · ")}
        </p>
      )}
      <p className="mt-3 text-xs text-slate-500">Platform facts describe data readiness only. Your constraints are never changed automatically.</p>
    </div>
  );
}

function ConcentrationWarnings({
  warnings,
}: {
  warnings?: { type: string; key: string; weightPercent: number | null; thresholdPercent: number | null; message: string }[];
}): JSX.Element | null {
  if (!warnings?.length) return null;
  return (
    <div className="mt-5 space-y-2">
      {warnings.map((warning) => (
        <p
          key={`${warning.type}-${warning.key}`}
          className="rounded-lg border border-amber-300/20 bg-amber-300/5 p-3 text-sm leading-6 text-amber-100"
        >
          {warning.message}
          {warning.weightPercent != null &&
            ` Current weight: ${percent(warning.weightPercent)}.`}
        </p>
      ))}
    </div>
  );
}

function StatusChip({ value }: { value: string | null | undefined }): JSX.Element {
  return (
    <span
      className={`inline-flex rounded-full border px-2.5 py-1 text-xs font-semibold ${statusClass(value)}`}
    >
      {label(value)}
    </span>
  );
}

function MetricTile({
  labelText,
  value,
  helper,
}: {
  labelText: string;
  value: string;
  helper?: string;
}): JSX.Element {
  return (
    <div className="rounded-xl border border-slate-800 bg-slate-950/50 p-4">
      <p className="text-xs uppercase text-slate-500">{labelText}</p>
      <p className="mt-2 text-2xl font-semibold text-white">{value}</p>
      {helper && <p className="mt-1 text-xs text-slate-400">{helper}</p>}
    </div>
  );
}

function ProgressBar({
  labelText,
  value,
  tone,
}: {
  labelText: string;
  value: number | null | undefined;
  tone?: "emerald" | "amber" | "rose" | "blue";
}): JSX.Element {
  const width = Math.max(0, Math.min(100, Number(value ?? 0)));
  const color =
    tone === "rose"
      ? "bg-rose-400"
      : tone === "amber"
        ? "bg-amber-300"
        : tone === "blue"
          ? "bg-sky-400"
          : "bg-emerald-400";
  return (
    <div>
      <div className="flex justify-between gap-3 text-xs text-slate-400">
        <span className="truncate">{labelText}</span>
        <span>{percent(value)}</span>
      </div>
      <div className="mt-1 h-2 rounded-full bg-slate-800">
        <div
          className={`h-2 rounded-full ${color}`}
          style={{ width: `${width}%` }}
        />
      </div>
    </div>
  );
}

function LiquidityCell({
  liquidity,
}: {
  liquidity: LiquidityResult | undefined;
}): JSX.Element {
  if (!liquidity) return <span className="text-slate-500">N/A</span>;
  return (
    <div className="space-y-1">
      <StatusChip value={liquidity.classification} />
      <p className="text-xs text-slate-400">
        {liquidity.daysToLiquidate == null
          ? label(liquidity.availabilityStatus)
          : `${ratio(liquidity.daysToLiquidate)} days`}
      </p>
      {liquidity.averageDailyDollarVolume != null && (
        <p className="text-xs text-slate-500">
          ADV {compactMoney(liquidity.averageDailyDollarVolume)}
        </p>
      )}
    </div>
  );
}

function BenchmarkPanel({
  benchmark,
}: {
  benchmark: BenchmarkComparison;
}): JSX.Element {
  const rows = [
    {
      name: "P/E",
      portfolio: ratio(benchmark.portfolioPeRatio),
      benchmark: ratio(benchmark.benchmarkPeRatio),
    },
    {
      name: "Dividend yield",
      portfolio: percent(benchmark.portfolioDividendYield),
      benchmark: percent(benchmark.benchmarkDividendYield),
    },
    {
      name: "MoS",
      portfolio: percent(benchmark.portfolioMarginOfSafety),
      benchmark: percent(benchmark.benchmarkMarginOfSafety),
    },
  ];
  return (
    <div className="rounded-xl border border-slate-800 bg-slate-950/50 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="font-semibold text-white">Benchmark comparison</h3>
        <StatusChip value={benchmark.availabilityStatus} />
      </div>
      <p className="mt-1 text-xs text-slate-400">
        Compared with {benchmark.benchmarkSymbol || "default benchmark"}.
      </p>
      <div className="mt-4 overflow-x-auto">
        <table className="w-full min-w-[24rem] text-left text-sm">
          <thead className="text-xs uppercase text-slate-500">
            <tr>
              <th>Metric</th>
              <th>Portfolio</th>
              <th>Benchmark</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800">
            {rows.map((row) => (
              <tr key={row.name}>
                <td className="py-2 text-slate-300">{row.name}</td>
                <td>{row.portfolio}</td>
                <td>{row.benchmark}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {Object.keys(benchmark.sectorWeightDifference ?? {}).length > 0 && (
        <div className="mt-4 space-y-2">
          <p className="text-xs uppercase text-slate-500">Sector difference</p>
          {Object.entries(benchmark.sectorWeightDifference).map(
            ([sector, value]) => (
              <ProgressBar
                key={sector}
                labelText={`${sector} ${Number(value) >= 0 ? "over" : "under"}`}
                value={Math.abs(Number(value))}
                tone={Number(value) >= 0 ? "amber" : "blue"}
              />
            ),
          )}
        </div>
      )}
    </div>
  );
}

function AnalyticsDashboard({
  analytics,
}: {
  analytics: PortfolioAnalytics;
}): JSX.Element {
  const sectorData = Object.entries(analytics.sectorWeights ?? {}).map(
    ([key, weightPercent]) => ({ key, weightPercent }),
  );
  const qualityData = Object.entries(
    analytics.qualityDistribution.earningsQualityPercent ?? {},
  );
  const moat = analytics.moatProfile;
  const concentrationTone = (
    item: HoldingConcentration,
  ): "emerald" | "amber" | "rose" =>
    item.status.toLowerCase().includes("concentrated")
      ? "rose"
      : item.status.toLowerCase().includes("immaterial")
        ? "amber"
        : "emerald";

  return (
    <section className="rounded-2xl border border-slate-800 bg-slate-900/50 p-5 sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-xl font-semibold text-white">
            Portfolio intelligence
          </h2>
          <p className="mt-1 text-sm text-slate-400">
            Portfolio-level diagnostics for concentration, liquidity, benchmark characteristics, and quality.
          </p>
        </div>
        <div className="text-right text-sm">
          <p className="text-slate-400">Snapshot</p>
          <p className="font-semibold text-white">
            {new Date(analytics.capturedAt).toLocaleString()}
          </p>
        </div>
      </div>

      <div className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
        <MetricTile
          labelText="Weighted MoS"
          value={percent(analytics.weightedMetrics.marginOfSafety)}
        />
        <MetricTile
          labelText="Weighted P/E"
          value={ratio(analytics.weightedMetrics.peRatio)}
        />
        <MetricTile
          labelText="Dividend yield"
          value={percent(analytics.weightedMetrics.dividendYield)}
        />
        <MetricTile
          labelText="Value score"
          value={ratio(analytics.weightedMetrics.valueScore)}
        />
        <MetricTile
          labelText="F-Score"
          value={ratio(analytics.weightedMetrics.piotroskiFScore)}
          helper="Piotroski"
        />
      </div>

      <div className="mt-5 grid gap-5 xl:grid-cols-2">
        <div className="rounded-xl border border-slate-800 bg-slate-950/50 p-4">
          <h3 className="font-semibold text-white">Sector allocation</h3>
          <div className="mt-4 grid gap-4 md:grid-cols-[15rem_1fr]">
            <div
              className="h-52"
              aria-label="Portfolio sector allocation chart"
            >
              <ResponsiveContainer>
                <PieChart>
                  <Pie
                    data={sectorData}
                    dataKey="weightPercent"
                    nameKey="key"
                    innerRadius="55%"
                    outerRadius="80%"
                  >
                    {sectorData.map((entry, index) => (
                      <Cell
                        key={entry.key}
                        fill={palette[index % palette.length]}
                      />
                    ))}
                  </Pie>
                  <Tooltip formatter={(value) => percent(Number(value))} />
                </PieChart>
              </ResponsiveContainer>
            </div>
            <div className="space-y-2">
              {sectorData.map((entry, index) => (
                <ProgressBar
                  key={entry.key}
                  labelText={entry.key}
                  value={entry.weightPercent}
                  tone={
                    analytics.sectorConcentrationFlags.includes(entry.key)
                      ? "rose"
                      : index % 2
                        ? "blue"
                        : "emerald"
                  }
                />
              ))}
              {analytics.sectorConcentrationFlags.length > 0 && (
                <p className="rounded-lg border border-amber-300/20 bg-amber-300/5 p-3 text-sm text-amber-100">
                  Concentration flags:{" "}
                  {analytics.sectorConcentrationFlags.join(", ")}
                </p>
              )}
            </div>
          </div>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-950/50 p-4">
          <h3 className="font-semibold text-white">Holding concentration</h3>
          <div className="mt-4 space-y-3">
            {analytics.holdingConcentration.map((item) => (
              <div key={item.symbol}>
                <div className="flex items-center justify-between gap-3">
                  <span className="font-semibold text-emerald-200">
                    {item.symbol}
                  </span>
                  <StatusChip value={item.status} />
                </div>
                <ProgressBar
                  labelText="Portfolio weight"
                  value={item.weightPercent}
                  tone={concentrationTone(item)}
                />
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="mt-5 grid gap-5 xl:grid-cols-3">
        <div className="rounded-xl border border-slate-800 bg-slate-950/50 p-4">
          <h3 className="font-semibold text-white">Moat profile</h3>
          <div className="mt-4 space-y-3">
            <ProgressBar labelText="Wide" value={moat.widePercent} />
            <ProgressBar labelText="Narrow" value={moat.narrowPercent} tone="blue" />
            <ProgressBar labelText="None" value={moat.nonePercent} tone="amber" />
            <ProgressBar labelText="Unknown" value={moat.unknownPercent} tone="rose" />
          </div>
        </div>
        <div className="rounded-xl border border-slate-800 bg-slate-950/50 p-4">
          <h3 className="font-semibold text-white">Quality distribution</h3>
          <div className="mt-4 grid grid-cols-2 gap-3">
            <MetricTile
              labelText="Avg. ROIC"
              value={percent(analytics.qualityDistribution.averageRoic)}
            />
            <MetricTile
              labelText="Avg. ROE"
              value={percent(analytics.qualityDistribution.averageRoe)}
            />
          </div>
          <div className="mt-4 space-y-2">
            {qualityData.map(([key, value]) => (
              <ProgressBar
                key={key}
                labelText={label(key)}
                value={value}
                tone="blue"
              />
            ))}
          </div>
        </div>
        <BenchmarkPanel benchmark={analytics.benchmarkComparison} />
      </div>

      {analytics.warnings.length > 0 && (
        <div className="mt-5 space-y-2">
          {analytics.warnings.map((warning) => (
            <p
              key={`${warning.type}-${warning.key}-${warning.message}`}
              className="rounded-lg border border-amber-300/20 bg-amber-300/5 p-3 text-sm text-amber-100"
            >
              {warning.message}
            </p>
          ))}
        </div>
      )}
    </section>
  );
}

function ConservativeReviewPack({
  portfolio,
  analytics,
  watchlist,
}: {
  portfolio: PortfolioDetail;
  analytics: PortfolioAnalytics | undefined;
  watchlist: WatchlistItem[];
}): JSX.Element {
  const watchlistBySymbol = new Map(
    watchlist.map((item) => [item.symbol.toUpperCase(), item]),
  );
  const sectorWeights =
    analytics?.sectorWeights ??
    portfolio.holdings.reduce<Record<string, number>>((weights, holding) => {
      const key = holding.sector ?? "Sector missing";
      weights[key] = (weights[key] ?? 0) + (holding.weightPercent ?? 0);
      return weights;
    }, {});
  const validationFindings = portfolio.holdings.flatMap((holding) =>
    conservativeFindings(holding),
  );
  const rationaleCovered = portfolio.holdings.filter((holding) => {
    const item = watchlistBySymbol.get(holding.symbol.toUpperCase());
    return Boolean(item?.monitoringReason || item?.rationaleNote);
  }).length;
  const qualitySignals =
    (analytics?.moatProfile.widePercent ?? 0) +
      (analytics?.moatProfile.narrowPercent ?? 0) >
      0 ||
    (analytics?.qualityDistribution.averageRoic ?? 0) >= 12;
  const qualityConflicts = portfolio.holdings.filter(
    (holding) => qualitySignals && (holding.marginOfSafety ?? 0) < 0,
  );

  return (
    <section className="review-pack rounded-2xl border border-slate-800 bg-slate-900/50 p-5 sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[.22em] text-emerald-300">
            Conservative review
          </p>
          <h2 className="mt-2 text-xl font-semibold text-white">
            Portfolio review pack
          </h2>
          <p className="mt-1 max-w-3xl text-sm leading-6 text-slate-400">
            Journal-style evidence for portfolio concentration, data coverage,
            valuation availability, and watchlist rationale. This is
            decision-support only and does not prescribe an investment action.
          </p>
        </div>
        <button
          type="button"
          onClick={() => window.print()}
          className="no-print rounded-lg border border-emerald-400/40 px-4 py-2 text-sm font-semibold text-emerald-100 hover:bg-emerald-400/10"
        >
          Print journal summary
        </button>
      </div>

      <div className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <MetricTile labelText="Holdings" value={`${portfolio.holdings.length}`} />
        <MetricTile
          labelText="Weighted MoS"
          value={percent(
            analytics?.weightedMetrics.marginOfSafety ?? portfolio.weightedMoS,
          )}
        />
        <MetricTile
          labelText="Rationale coverage"
          value={`${rationaleCovered}/${portfolio.holdings.length}`}
          helper="Holdings with watchlist reason or note"
        />
        <MetricTile
          labelText="Incomplete checks"
          value={`${validationFindings.length}`}
          helper="Missing price, sector, score, or valuation status"
        />
      </div>

      <div className="mt-5 grid gap-5 xl:grid-cols-[1fr_20rem]">
        <div className="overflow-x-auto rounded-xl border border-slate-800 bg-slate-950/50">
          <table className="w-full min-w-[64rem] text-left text-sm">
            <thead className="bg-slate-950/60 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-3">Holding</th>
                <th>Weight</th>
                <th>Sector</th>
                <th>Price</th>
                <th>MoS</th>
                <th>Valuation</th>
                <th>Score</th>
                <th>Rationale</th>
                <th>Validation</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              {portfolio.holdings.map((holding) => {
                const watchItem = watchlistBySymbol.get(
                  holding.symbol.toUpperCase(),
                );
                const findings = conservativeFindings(holding);
                return (
                  <tr key={holding.id} className="align-top">
                    <td className="px-4 py-3 font-semibold text-emerald-300">
                      {holding.symbol}
                    </td>
                    <td>{percent(holding.weightPercent)}</td>
                    <td>{holding.sector ?? "Missing"}</td>
                    <td>{money(holding.currentPrice)}</td>
                    <td>{percent(holding.marginOfSafety)}</td>
                    <td>
                      <StatusChip value={holding.valueStatus} />
                    </td>
                    <td>
                      <StatusChip value="not reported by holding API" />
                    </td>
                    <td className="max-w-[14rem] text-xs leading-5 text-slate-400">
                      {watchItem?.rationaleNote ||
                        (watchItem?.monitoringReason
                          ? label(watchItem.monitoringReason)
                          : null) ||
                        "No watchlist rationale linked"}
                    </td>
                    <td className="max-w-[16rem] text-xs leading-5 text-slate-300">
                      {findings.length ? findings.join("; ") : "Complete"}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        <aside className="space-y-4">
          <div className="rounded-xl border border-slate-800 bg-slate-950/50 p-4">
            <h3 className="font-semibold text-white">Sector weights</h3>
            <div className="mt-4 space-y-3">
              {Object.entries(sectorWeights).map(([sector, weight]) => (
                <ProgressBar
                  key={sector}
                  labelText={sector}
                  value={weight}
                  tone={
                    analytics?.sectorConcentrationFlags.includes(sector)
                      ? "rose"
                      : "blue"
                  }
                />
              ))}
            </div>
          </div>
          <div className="rounded-xl border border-slate-800 bg-slate-950/50 p-4">
            <h3 className="font-semibold text-white">Data-quality blockers</h3>
            {validationFindings.length ? (
              <ul className="mt-3 space-y-2 text-sm leading-6 text-amber-100">
                {validationFindings.map((finding) => (
                  <li key={finding}>{finding}</li>
                ))}
              </ul>
            ) : (
              <p className="mt-3 text-sm text-slate-400">
                No required validation blockers detected for the current
                holdings.
              </p>
            )}
          </div>
        </aside>
      </div>

      {qualityConflicts.length > 0 && (
        <div className="mt-5 rounded-xl border border-amber-300/20 bg-amber-300/5 p-4 text-sm leading-6 text-amber-100">
          <h3 className="font-semibold text-amber-50">
            Quality and valuation conflicts
          </h3>
          <p className="mt-2">
            {qualityConflicts.map((holding) => holding.symbol).join(", ")} show
            negative margin of safety while portfolio-level quality signals are
            present. Review valuation assumptions before treating business
            quality as sufficient evidence.
          </p>
        </div>
      )}

      <div className="mt-5 rounded-xl border border-slate-800 bg-slate-950/50 p-4 text-sm leading-6 text-slate-300">
        <h3 className="font-semibold text-white">Journal summary</h3>
        <p className="mt-2">
          Portfolio: {portfolio.name}. Current value:{" "}
          {money(portfolio.totalValue)}. Concentration warnings:{" "}
          {portfolio.concentrationWarnings.length || "none"}. Analytics
          snapshot: {analytics?.capturedAt ?? "not available"}. This report
          records available evidence and missing-data conditions for research
          review; it is not personalised investment advice.
        </p>
      </div>
    </section>
  );
}

function conservativeFindings(holding: Holding): string[] {
  const findings: string[] = [];
  if (holding.currentPrice == null) findings.push(`${holding.symbol}: missing current price`);
  if (!holding.sector) findings.push(`${holding.symbol}: missing sector`);
  findings.push(`${holding.symbol}: score status not reported by holding API`);
  if (!holding.valueStatus) findings.push(`${holding.symbol}: missing valuation status`);
  return findings;
}

export function PortfolioPage(): JSX.Element {
  const { session } = useAuth();
  const client = useQueryClient();
  const [selected, setSelected] = useState<string | null | undefined>(
    undefined,
  );
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [form, setForm] = useState<SimulationInput>(defaults);
  const [simulation, setSimulation] = useState<Simulation | null>(null);
  const [rebalance, setRebalance] = useState<Awaited<
    ReturnType<typeof portfolioApi.rebalance>
  > | null>(null);
  const [newSymbol, setNewSymbol] = useState("");
  const [newQuantity, setNewQuantity] = useState("");
  const [holdingValidation, setHoldingValidation] = useState<string | null>(
    null,
  );
  const [portfolioNotice, setPortfolioNotice] = useState<string | null>(null);
  const portfolios = useQuery({
    queryKey: ["portfolios"],
    queryFn: portfolioApi.list,
  });
  const advisorAcknowledgement = useQuery({
    queryKey: ["advisor-acknowledgement"],
    queryFn: professionalApi.advisorAcknowledgement,
    enabled: session?.role === "ADVISOR",
  });
  const acknowledgeAdvisor = useMutation({
    mutationFn: professionalApi.acknowledgeAdvisor,
    onSuccess: () =>
      void client.invalidateQueries({ queryKey: ["advisor-acknowledgement"] }),
  });
  const activeId =
    selected === undefined ? (portfolios.data?.[0]?.id ?? null) : selected;
  const detail = useQuery({
    queryKey: ["portfolio", activeId],
    queryFn: () => portfolioApi.detail(activeId!),
    enabled: Boolean(activeId),
  });
  const analytics = useQuery({
    queryKey: ["portfolio", activeId, "analytics"],
    queryFn: () => portfolioApi.analytics(activeId!),
    enabled: Boolean(activeId),
  });
  const watchlist = useQuery({
    queryKey: ["watchlist"],
    queryFn: watchlistApi.list,
  });
  const localFormValid =
    Boolean(activeId) &&
    form.budget > 0 &&
    (form.maxStockPercent ?? 0) > 0 &&
    (form.maxSectorPercent ?? 0) > 0 &&
    (form.maxCountryPercent ?? 0) > 0;
  const preconditions = useQuery({
    queryKey: ["portfolio", activeId, "simulation-preconditions", form],
    queryFn: () => portfolioApi.preconditions(activeId!, form),
    enabled: localFormValid,
    staleTime: 5_000,
  });
  const create = useMutation({
    mutationFn: () => portfolioApi.create(name, description),
    onSuccess: (created) => {
      setSelected(created.id);
      setName("");
      setDescription("");
      void client.invalidateQueries({ queryKey: ["portfolios"] });
    },
  });
  const deletePortfolio = useMutation({
    mutationFn: (portfolio: Portfolio) => portfolioApi.remove(portfolio.id),
    onSuccess: async (_, deleted) => {
      setPortfolioNotice(`Portfolio "${deleted.name}" deleted.`);
      setSimulation(null);
      setRebalance(null);
      client.removeQueries({ queryKey: ["portfolio", deleted.id] });
      const remaining = (portfolios.data ?? []).filter(
        (portfolio) => portfolio.id !== deleted.id,
      );
      setSelected(remaining[0]?.id ?? null);
      await client.invalidateQueries({ queryKey: ["portfolios"] });
    },
  });
  const runSimulation = useMutation({
    mutationFn: () => portfolioApi.simulate(activeId!, form),
    onSuccess: setSimulation,
  });
  const runRebalance = useMutation({
    mutationFn: () => portfolioApi.rebalance(activeId!, form),
    onSuccess: setRebalance,
  });
  const refreshDetail = (): void => {
    void client.invalidateQueries({ queryKey: ["portfolio", activeId] });
    void client.invalidateQueries({
      queryKey: ["portfolio", activeId, "analytics"],
    });
    void client.invalidateQueries({ queryKey: ["portfolios"] });
    void client.invalidateQueries({
      queryKey: ["portfolio", activeId, "simulation-preconditions"],
    });
  };
  const addHolding = useMutation({
    mutationFn: () =>
      portfolioApi.addHolding(activeId!, {
        symbol: newSymbol.trim().toUpperCase(),
        quantity: Number(newQuantity),
      }),
    onSuccess: () => {
      setNewSymbol("");
      setNewQuantity("");
      refreshDetail();
    },
  });
  const saveProposal = useMutation({
    mutationFn: async () => {
      if (!simulation) return;
      for (const item of simulation.proposals)
        await portfolioApi.addHolding(activeId!, {
          symbol: item.symbol,
          quantity: item.proposedShares,
          averageCostBasis: item.currentPrice ?? undefined,
          currency: "USD",
        });
    },
    onSuccess: refreshDetail,
  });
  const removeHolding = useMutation({
    mutationFn: (holdingId: string) =>
      portfolioApi.removeHolding(activeId!, holdingId),
    onSuccess: refreshDetail,
  });
  const totalWeight = useMemo(
    () =>
      simulation?.proposals.reduce(
        (sum, item) => sum + item.actualWeightPercent,
        0,
      ) ?? 0,
    [simulation],
  );
  const liquidityBySymbol = useMemo(
    () =>
      new Map(
        (analytics.data?.liquidity ?? []).map((item) => [
          item.symbol.toUpperCase(),
          item,
        ]),
      ),
    [analytics.data],
  );
  const commandDiagnostics = (
    (runSimulation.error ?? runRebalance.error) as
      | (Error & { diagnostics?: PortfolioPreconditions })
      | null
  )?.diagnostics;
  const readiness = commandDiagnostics ?? preconditions.data;
  const canSimulate =
    localFormValid && readiness?.simulationAvailable !== false;
  const canRebalance =
    localFormValid && readiness?.rebalanceAvailable !== false;
  const update = (field: keyof SimulationInput, raw: string): void =>
    setForm((current) => ({
      ...current,
      [field]: raw === "" ? undefined : Number(raw),
    }));
  const quantityIsValid =
    Number.isInteger(Number(newQuantity)) &&
    Number(newQuantity) > 0 &&
    Number(newQuantity) <= maxHoldingQuantity;

  return (
    <div className="space-y-6">
      {session?.role === "ADVISOR" &&
        advisorAcknowledgement.data &&
        !advisorAcknowledgement.data.acknowledged && (
          <section className="rounded-2xl border border-amber-300/30 bg-amber-300/10 p-5 text-amber-50">
            <div className="flex flex-col justify-between gap-4 md:flex-row md:items-center">
              <p className="max-w-4xl text-sm leading-6">
                {advisorAcknowledgement.data.disclaimer ||
                  "This tool supports your research process. Suitability assessment, client risk profiling, and regulatory record-keeping remain your responsibility."}
              </p>
              <button
                type="button"
                disabled={acknowledgeAdvisor.isPending}
                onClick={() => acknowledgeAdvisor.mutate()}
                className="rounded-lg border border-amber-100/50 px-4 py-2 text-sm font-semibold text-amber-50 hover:bg-amber-100/10 disabled:opacity-50"
              >
                Acknowledge
              </button>
            </div>
          </section>
        )}
      <section className="rounded-2xl border border-slate-800 bg-slate-900/60 p-6 sm:p-8">
        <p className="text-xs font-semibold uppercase tracking-[.22em] text-emerald-300">
          Construct
        </p>
        <h1 className="mt-3 text-3xl font-semibold text-white sm:text-4xl">
          Build with constraints, not hunches.
        </h1>
        <p className="mt-3 max-w-3xl leading-7 text-slate-300">
          Create a model portfolio, test its diversification limits, then review
          the practical trades required to rebalance it.
        </p>
      </section>

      <PortfolioImportPanel
        portfolios={portfolios.data ?? []}
        activeId={activeId}
        onCommitted={(portfolioId) => {
          setSelected(portfolioId);
          setSimulation(null);
          setRebalance(null);
          setPortfolioNotice("Portfolio import committed. Source values remain available in the reconciliation report.");
        }}
      />
      {activeId && <PortfolioAnalysisPanel key={activeId} portfolioId={activeId} />}

      <section className="grid gap-6 xl:grid-cols-[20rem_1fr]">
        <aside className="rounded-2xl border border-slate-800 bg-slate-900/50 p-5">
          <h2 className="text-lg font-semibold text-white">Your portfolios</h2>
          <div className="mt-4 space-y-2">
            {portfolios.isLoading && (
              <p className="text-sm text-slate-400">Loading portfolios…</p>
            )}
            {portfolios.data?.map((portfolio: Portfolio) => (
              <button
                key={portfolio.id}
                onClick={() => {
                  setSelected(portfolio.id);
                  setSimulation(null);
                  setRebalance(null);
                }}
                className={`w-full rounded-xl border p-3 text-left transition ${activeId === portfolio.id ? "border-emerald-400/60 bg-emerald-400/10" : "border-slate-800 hover:bg-slate-800/70"}`}
              >
                <span className="block font-medium text-white">
                  {portfolio.name}
                </span>
                <span className="text-xs text-slate-400">
                  {portfolio.holdingCount} holdings
                </span>
              </button>
            ))}
          </div>
          <form
            className="mt-5 border-t border-slate-800 pt-5"
            onSubmit={(event) => {
              event.preventDefault();
              if (name.trim()) create.mutate();
            }}
          >
            <label className="text-sm font-medium text-slate-200">
              New portfolio
              <input
                required
                value={name}
                onChange={(event) => setName(event.target.value)}
                className={inputClass}
                placeholder="Long-term value"
              />
            </label>
            <label className="mt-3 block text-sm font-medium text-slate-200">
              Description
              <input
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                className={inputClass}
                placeholder="Optional"
              />
            </label>
            <button
              disabled={create.isPending}
              className={`mt-4 w-full ${buttonClass}`}
            >
              {create.isPending ? "Creating…" : "Create portfolio"}
            </button>
          </form>
          <ErrorNotice error={portfolios.error ?? create.error} />
        </aside>

        <div className="space-y-6">
          <section id="simulation-constraints" className="rounded-2xl border border-slate-800 bg-slate-900/50 p-5 sm:p-6">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h2 className="text-xl font-semibold text-white">
                  {detail.data?.name ?? "Select or create a portfolio"}
                </h2>
                <p className="mt-1 text-sm text-slate-400">
                  {detail.data?.description ??
                    "A simulation needs a portfolio to receive its allocation."}
                </p>
              </div>
              {detail.data && (
                <div className="flex flex-col items-end gap-3 text-right text-sm">
                  <div>
                    <p className="text-slate-400">Current value</p>
                    <p className="font-semibold text-white">
                      {money(detail.data.totalValue)}
                    </p>
                  </div>
                  <button
                    type="button"
                    disabled={deletePortfolio.isPending}
                    onClick={() => {
                      setPortfolioNotice(null);
                      deletePortfolio.reset();
                      const confirmed = window.confirm(
                        `Delete portfolio "${detail.data.name}"? Its holdings, analytics snapshots, and saved rebalance proposals will be permanently removed.`,
                      );
                      if (confirmed) deletePortfolio.mutate(detail.data);
                    }}
                    className="rounded-lg border border-rose-300/40 px-3 py-2 text-xs font-semibold text-rose-100 transition hover:bg-rose-400/10 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {deletePortfolio.isPending
                      ? "Deleting portfolio…"
                      : "Delete portfolio"}
                  </button>
                </div>
              )}
            </div>
            {portfolioNotice && (
              <p
                role="status"
                className="mt-4 rounded-lg border border-emerald-300/20 bg-emerald-300/5 p-3 text-sm text-emerald-100"
              >
                {portfolioNotice}
              </p>
            )}
            {detail.data?.holdings?.length ? (
              <div className="mt-5 overflow-x-auto">
                <table className="w-full min-w-[46rem] text-left text-sm">
                  <thead className="text-xs uppercase text-slate-400">
                    <tr>
                      <th>Holding</th>
                      <th>Quantity</th>
                      <th>Value</th>
                      <th>Weight</th>
                      <th>MoS</th>
                      <th>Liquidity</th>
                      <th>
                        <span className="sr-only">Actions</span>
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800">
                    {detail.data.holdings.map((holding) => (
                      <tr key={holding.id}>
                        <td className="py-3 font-semibold text-emerald-300">
                          {holding.symbol}
                        </td>
                        <td>{holding.quantity}</td>
                        <td>{money(holding.currentValue)}</td>
                        <td>{percent(holding.weightPercent)}</td>
                        <td>{percent(holding.marginOfSafety)}</td>
                        <td>
                          <LiquidityCell
                            liquidity={liquidityBySymbol.get(
                              holding.symbol.toUpperCase(),
                            )}
                          />
                        </td>
                        <td>
                          <div className="flex flex-wrap gap-3">
                            <Link
                              to={`/securities/${encodeURIComponent(holding.symbol)}/review`}
                              className="text-xs font-semibold text-emerald-200 underline"
                            >
                              Review
                            </Link>
                            <button
                              type="button"
                              onClick={() => removeHolding.mutate(holding.id)}
                              className="text-xs font-semibold text-rose-200 underline"
                            >
                              Remove
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              activeId && (
                <p className="mt-5 rounded-lg bg-slate-950/60 p-4 text-sm text-slate-400">
                  No holdings yet. Run a simulation to assess a proposed
                  allocation; add holdings through the existing portfolio API
                  while portfolio editing is being expanded.
                </p>
              )
            )}
            <ConcentrationWarnings warnings={detail.data?.concentrationWarnings} />
            {activeId && (
              <form
                className="mt-5 flex flex-wrap items-end gap-3 border-t border-slate-800 pt-5"
                onSubmit={(event) => {
                  event.preventDefault();
                  if (!quantityIsValid) {
                    setHoldingValidation(
                      `Quantity must be a whole number from 1 to ${maxHoldingQuantity.toLocaleString()}.`,
                    );
                    return;
                  }
                  setHoldingValidation(null);
                  if (newSymbol) addHolding.mutate();
                }}
              >
                <label className="text-sm font-medium text-slate-200">
                  Ticker
                  <input
                    required
                    value={newSymbol}
                    onChange={(event) => setNewSymbol(event.target.value)}
                    className={inputClass}
                    placeholder="AAPL"
                  />
                </label>
                <label className="text-sm font-medium text-slate-200">
                  Quantity
                  <input
                    required
                    type="number"
                    min="1"
                    step="1"
                    max={maxHoldingQuantity}
                    value={newQuantity}
                    onChange={(event) => {
                      setNewQuantity(event.target.value);
                      setHoldingValidation(null);
                    }}
                    className={inputClass}
                    placeholder="10"
                  />
                </label>
                <button
                  disabled={
                    addHolding.isPending || !newSymbol || !quantityIsValid
                  }
                  className={buttonClass}
                >
                  {addHolding.isPending ? "Adding…" : "Add holding"}
                </button>
              </form>
            )}
            {holdingValidation && (
              <p role="alert" className="mt-3 text-sm text-rose-200">
                {holdingValidation}
              </p>
            )}
            <ErrorNotice
              error={
                detail.error ??
                addHolding.error ??
                removeHolding.error ??
                deletePortfolio.error
              }
            />
          </section>

          {analytics.data && <AnalyticsDashboard analytics={analytics.data} />}
          {!analytics.data && analytics.isLoading && activeId && (
            <section className="rounded-2xl border border-slate-800 bg-slate-900/50 p-5 sm:p-6">
              <p className="text-sm text-slate-400">
                Loading portfolio intelligence...
              </p>
            </section>
          )}
          <ErrorNotice error={analytics.error} />
          {detail.data?.holdings?.length ? (
            <ConservativeReviewPack
              portfolio={detail.data}
              analytics={analytics.data}
              watchlist={watchlist.data ?? []}
            />
          ) : null}
          <ErrorNotice error={watchlist.error} />

          <section id="portfolio-holdings" className="rounded-2xl border border-slate-800 bg-slate-900/50 p-5 sm:p-6">
            <h2 className="text-xl font-semibold text-white">
              Allocation constraints
            </h2>
            <p className="mt-1 text-sm text-slate-400">
              The risk profile establishes a conservative starting point; every
              limit remains explicit and editable.
            </p>
            <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <label className="text-sm font-medium text-slate-200">
                Risk profile
                <select
                  onChange={(event) => {
                    const profile = event.target.value;
                    setForm(
                      profile === "conservative"
                        ? {
                            ...form,
                            maxStockPercent: 7,
                            maxSectorPercent: 20,
                            maxCountryPercent: 50,
                          }
                        : profile === "growth"
                          ? {
                              ...form,
                              maxStockPercent: 15,
                              maxSectorPercent: 35,
                              maxCountryPercent: 75,
                            }
                          : {
                              ...form,
                              maxStockPercent: 10,
                              maxSectorPercent: 25,
                              maxCountryPercent: 60,
                            },
                    );
                  }}
                  className={inputClass}
                >
                  <option value="balanced">Balanced</option>
                  <option value="conservative">Conservative</option>
                  <option value="growth">Growth</option>
                </select>
              </label>
              <label className="text-sm font-medium text-slate-200">
                Budget (USD)
                <input
                  type="number"
                  min="1"
                  value={form.budget ?? ""}
                  onChange={(event) => update("budget", event.target.value)}
                  className={inputClass}
                />
              </label>
              <label className="text-sm font-medium text-slate-200">
                Minimum yield (%)
                <input
                  type="number"
                  min="0"
                  value={form.minimumDividendYield ?? ""}
                  onChange={(event) =>
                    update("minimumDividendYield", event.target.value)
                  }
                  className={inputClass}
                />
              </label>
              <label className="text-sm font-medium text-slate-200">
                Max. stock (%)
                <input
                  type="number"
                  min="1"
                  max="100"
                  value={form.maxStockPercent ?? ""}
                  onChange={(event) =>
                    update("maxStockPercent", event.target.value)
                  }
                  className={inputClass}
                />
              </label>
              <label className="text-sm font-medium text-slate-200">
                Max. sector (%)
                <input
                  type="number"
                  min="1"
                  max="100"
                  value={form.maxSectorPercent ?? ""}
                  onChange={(event) =>
                    update("maxSectorPercent", event.target.value)
                  }
                  className={inputClass}
                />
              </label>
              <label className="text-sm font-medium text-slate-200">
                Max. country (%)
                <input
                  type="number"
                  min="1"
                  max="100"
                  value={form.maxCountryPercent ?? ""}
                  onChange={(event) =>
                    update("maxCountryPercent", event.target.value)
                  }
                  className={inputClass}
                />
              </label>
              <label className="text-sm font-medium text-slate-200">
                Minimum MoS (%)
                <input
                  type="number"
                  min="0"
                  value={form.minimumMarginOfSafety ?? ""}
                  onChange={(event) =>
                    update("minimumMarginOfSafety", event.target.value)
                  }
                  className={inputClass}
                />
              </label>
            </div>
            <ReadinessPanel
              readiness={readiness}
              loading={preconditions.isFetching && !preconditions.data}
              error={preconditions.error}
              retry={() => void preconditions.refetch()}
            />
            <div className="mt-5 flex flex-wrap gap-3">
              <button
                disabled={!canSimulate || runSimulation.isPending}
                title={!canSimulate ? readiness?.diagnostics.find((item) => item.blocksSimulation)?.message : undefined}
                onClick={() => runSimulation.mutate()}
                className={buttonClass}
              >
                {runSimulation.isPending
                  ? "Simulating…"
                  : "Run allocation simulation"}
              </button>
              <button
                disabled={!canRebalance || runRebalance.isPending}
                title={!canRebalance ? readiness?.diagnostics.find((item) => item.blocksRebalance)?.message : undefined}
                onClick={() => runRebalance.mutate()}
                className="rounded-lg border border-slate-600 px-4 py-2.5 text-sm font-semibold text-slate-200 hover:bg-slate-800 disabled:opacity-50"
              >
                {runRebalance.isPending
                  ? "Preparing…"
                  : "Create rebalance proposal"}
              </button>
            </div>
            <ErrorNotice error={runSimulation.error ?? runRebalance.error} />
          </section>
        </div>
      </section>

      {simulation && (
        <section className="grid gap-6 xl:grid-cols-[1fr_20rem]">
          <div className="overflow-hidden rounded-2xl border border-slate-800 bg-slate-900/50">
            <div className="border-b border-slate-800 p-5 sm:p-6">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <h2 className="text-xl font-semibold text-white">
                  Proposed allocation
                </h2>
                <button
                  disabled={saveProposal.isPending}
                  onClick={() => saveProposal.mutate()}
                  className={buttonClass}
                >
                  {saveProposal.isPending
                    ? "Saving…"
                    : "Save proposed holdings"}
                </button>
              </div>
              <p className="mt-1 text-sm text-slate-400">
                {money(simulation.investedAmount)} invested ·{" "}
                {money(simulation.unallocatedCash)} unallocated ·{" "}
                {percent(totalWeight)} allocated
              </p>
            </div>
            <div className="overflow-x-auto">
              <table className="min-w-[50rem] w-full text-left text-sm">
                <thead className="bg-slate-950/40 text-xs uppercase text-slate-400">
                  <tr>
                    <th className="px-4 py-3">Security</th>
                    <th>Sector</th>
                    <th>Shares</th>
                    <th>Amount</th>
                    <th>Weight</th>
                    <th>MoS</th>
                    <th>Yield</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800">
                  {simulation.proposals.map((item) => (
                    <tr key={item.symbol}>
                      <td className="px-4 py-3 font-semibold text-emerald-300">
                        {item.symbol}
                      </td>
                      <td>{item.sector ?? "—"}</td>
                      <td>{item.proposedShares}</td>
                      <td>{money(item.actualAmount)}</td>
                      <td>{percent(item.actualWeightPercent)}</td>
                      <td>{percent(item.marginOfSafety)}</td>
                      <td>{percent(item.dividendYield)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {simulation.excludedSymbols.length > 0 && (
              <p className="m-5 text-sm text-amber-100">
                Excluded:{" "}
                {simulation.excludedSymbols
                  .map((item) => `${item.symbol} (${item.reason})`)
                  .join(", ")}
              </p>
            )}
            <p className="m-5 text-xs leading-5 text-slate-400">
              {simulation.disclaimer}
            </p>
          </div>
          <aside className="rounded-2xl border border-slate-800 bg-slate-900/50 p-5">
            <h2 className="text-lg font-semibold text-white">
              Sector allocation
            </h2>
            <div className="mt-4 h-56" aria-label="Sector allocation chart">
              <ResponsiveContainer>
                <PieChart>
                  <Pie
                    data={simulation.sectorWeights}
                    dataKey="weightPercent"
                    nameKey="key"
                    innerRadius="55%"
                    outerRadius="80%"
                  >
                    {simulation.sectorWeights.map((entry, index) => (
                      <Cell
                        key={entry.key}
                        fill={palette[index % palette.length]}
                      />
                    ))}
                  </Pie>
                  <Tooltip formatter={(value) => percent(Number(value))} />
                </PieChart>
              </ResponsiveContainer>
            </div>
            <ul className="space-y-2 text-sm">
              {simulation.sectorWeights.map((entry, index) => (
                <li key={entry.key} className="flex justify-between">
                  <span>
                    <span
                      className="mr-2 inline-block h-2.5 w-2.5 rounded-full"
                      style={{ background: palette[index % palette.length] }}
                    />
                    {entry.key}
                  </span>
                  <span>{percent(entry.weightPercent)}</span>
                </li>
              ))}
            </ul>
          </aside>
        </section>
      )}
      {rebalance && (
        <section className="overflow-hidden rounded-2xl border border-slate-800 bg-slate-900/50">
          <div className="flex flex-wrap justify-between gap-4 border-b border-slate-800 p-5 sm:p-6">
            <div>
              <h2 className="text-xl font-semibold text-white">
                Rebalance proposal
              </h2>
              <p className="mt-1 text-sm text-slate-400">
                Buy {money(rebalance.estimatedBuyValue)} · Sell{" "}
                {money(rebalance.estimatedSellValue)} - Cost{" "}
                {money(rebalance.totalEstimatedTransactionCost)}
              </p>
            </div>
            <span className="rounded-full bg-emerald-400/10 px-3 py-1 text-sm text-emerald-200">
              {rebalance.status}
            </span>
          </div>
          <div className="overflow-x-auto">
            <table className="min-w-[62rem] w-full text-left text-sm">
              <thead className="bg-slate-950/40 text-xs uppercase text-slate-400">
                <tr>
                  <th className="px-4 py-3">Security</th>
                  <th>Action</th>
                  <th>Urgency</th>
                  <th>Current</th>
                  <th>Target</th>
                  <th>Change</th>
                  <th>Estimated value</th>
                  <th>Cost</th>
                  <th>Holding period</th>
                  <th>Position note</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800">
                {rebalance.lines.map((line) => (
                  <tr key={line.symbol}>
                    <td className="px-4 py-3 font-semibold text-emerald-300">
                      {line.symbol}
                    </td>
                    <td>{line.side}</td>
                    <td>
                      <StatusChip value={line.urgency} />
                    </td>
                    <td>{line.currentQuantity}</td>
                    <td>{line.targetQuantity}</td>
                    <td>{line.deltaQuantity}</td>
                    <td>{money(line.estimatedTradeValue)}</td>
                    <td>{money(line.estimatedTransactionCost)}</td>
                    <td>{label(line.holdingPeriod)}</td>
                    <td className="max-w-[14rem] text-xs text-slate-400">
                      {line.positionSizeWarning ?? "N/A"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="m-5 text-xs leading-5 text-slate-400">
            {rebalance.disclaimer}
          </p>
        </section>
      )}
    </div>
  );
}
