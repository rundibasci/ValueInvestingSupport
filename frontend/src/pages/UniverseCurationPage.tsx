import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  universeCurationApi,
  type UniversePreview,
  type UniversePreviewRow,
  type UniverseSelectionCriteria,
  type UniverseSortBy,
  type UniverseTemplate,
} from "../api/universeCuration";
import type { SeedResult } from "../api/seedUniverse";

const defaultCriteria: UniverseSelectionCriteria = {
  exchanges: ["NASDAQ", "NYSE"],
  countries: ["US"],
  sectors: [],
  excludeSectors: false,
  marketCapMin: 1_000_000_000,
  marketCapMax: null,
  volumeMin: 250_000,
  maxSymbols: 100,
  sortBy: "MARKET_CAP_DESC",
};

const exchangeOptions = ["NASDAQ", "NYSE", "AMEX", "LSE", "TSX", "XETRA"];
const countryOptions = ["US", "CA", "GB", "DE", "FR", "IT", "CH"];
const sectorOptions = [
  "Basic Materials",
  "Communication Services",
  "Consumer Cyclical",
  "Consumer Defensive",
  "Energy",
  "Financial Services",
  "Healthcare",
  "Industrials",
  "Real Estate",
  "Technology",
  "Utilities",
];

const sortOptions: Array<{ value: UniverseSortBy; label: string }> = [
  { value: "MARKET_CAP_DESC", label: "Market cap high to low" },
  { value: "MARKET_CAP_ASC", label: "Market cap low to high" },
  { value: "VOLUME_DESC", label: "Volume high to low" },
  { value: "SYMBOL_ASC", label: "Symbol A to Z" },
];

function parseList(value: string): string[] {
  return value
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function formatMoney(value: number | null | undefined): string {
  if (value == null) return "-";
  return new Intl.NumberFormat("en-US", {
    notation: "compact",
    maximumFractionDigits: 1,
    style: "currency",
    currency: "USD",
  }).format(value);
}

function formatNumber(value: number | null | undefined): string {
  if (value == null) return "-";
  return new Intl.NumberFormat("en-US", {
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(value);
}

function mergeTemplate(criteria: UniverseTemplate["criteria"]): UniverseSelectionCriteria {
  return {
    ...defaultCriteria,
    ...criteria,
    exchanges: criteria?.exchanges ?? defaultCriteria.exchanges,
    countries: criteria?.countries ?? defaultCriteria.countries,
    sectors: criteria?.sectors ?? defaultCriteria.sectors,
    excludeSectors: criteria?.excludeSectors ?? defaultCriteria.excludeSectors,
    marketCapMin: criteria?.marketCapMin ?? defaultCriteria.marketCapMin,
    marketCapMax: criteria?.marketCapMax ?? defaultCriteria.marketCapMax,
    volumeMin: criteria?.volumeMin ?? defaultCriteria.volumeMin,
    maxSymbols: criteria?.maxSymbols ?? defaultCriteria.maxSymbols,
    sortBy: criteria?.sortBy ?? defaultCriteria.sortBy,
  };
}

function distribution(
  rows: UniversePreviewRow[],
  key: "sector" | "exchange",
): Array<[string, number]> {
  const counts = new Map<string, number>();
  rows.forEach((row) => {
    const label = row[key] || "Unknown";
    counts.set(label, (counts.get(label) ?? 0) + 1);
  });
  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
    .slice(0, 6);
}

function seedStatusClass(result: SeedResult): string {
  if (result.error) return "bg-rose-400/15 text-rose-100 ring-1 ring-rose-300/25";
  if (result.status === "unavailable") return "bg-amber-300/15 text-amber-100 ring-1 ring-amber-300/25";
  return "bg-emerald-400/15 text-emerald-100 ring-1 ring-emerald-300/25";
}

export function UniverseCurationPage(): JSX.Element {
  const queryClient = useQueryClient();
  const [criteria, setCriteria] = useState(defaultCriteria);
  const [selectedTemplateId, setSelectedTemplateId] = useState("");
  const [preview, setPreview] = useState<UniversePreview | null>(null);
  const [seedResults, setSeedResults] = useState<SeedResult[] | null>(null);

  const templatesQuery = useQuery({
    queryKey: ["universe-curation", "templates"],
    queryFn: universeCurationApi.templates,
  });

  const previewMutation = useMutation({
    mutationFn: universeCurationApi.preview,
    onSuccess: (data) => {
      setPreview(data);
      setSeedResults(null);
    },
  });

  const seedMutation = useMutation({
    mutationFn: universeCurationApi.seed,
    onSuccess: (data) => {
      setPreview(data.preview);
      setSeedResults(data.results);
      void queryClient.invalidateQueries({ queryKey: ["screener"] });
      void queryClient.invalidateQueries({ queryKey: ["security-search"] });
    },
  });

  const rows = preview?.symbols ?? [];
  const sectorDistribution = useMemo(() => distribution(rows, "sector"), [rows]);
  const exchangeDistribution = useMemo(() => distribution(rows, "exchange"), [rows]);
  const successfulSeeds = seedResults?.filter((result) => !result.error).length ?? 0;
  const selectedTemplate = templatesQuery.data?.find(
    (template) => template.id === selectedTemplateId,
  );

  function update<K extends keyof UniverseSelectionCriteria>(
    key: K,
    value: UniverseSelectionCriteria[K],
  ): void {
    setCriteria((current) => ({ ...current, [key]: value }));
  }

  function submitPreview(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault();
    previewMutation.mutate(criteria);
  }

  function applyTemplate(templateId: string): void {
    setSelectedTemplateId(templateId);
    const template = templatesQuery.data?.find((item) => item.id === templateId);
    if (template) {
      setCriteria(mergeTemplate(template.criteria));
      setPreview(null);
      setSeedResults(null);
    }
  }

  return (
    <section className="space-y-6">
      <div className="flex flex-col justify-between gap-4 md:flex-row md:items-end">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[.22em] text-emerald-300">
            Shared universe
          </p>
          <h1 className="mt-2 text-3xl font-semibold text-white">
            Universe Curation
          </h1>
          <p className="mt-3 max-w-3xl leading-7 text-slate-300">
            Build a criteria-based research universe, preview the exact symbols,
            then seed only the reviewed list into shared reference data.
          </p>
        </div>
        <div className="flex flex-wrap gap-3">
          <Link
            to="/seed"
            className="rounded-lg border border-slate-700 px-4 py-2 text-sm font-medium text-slate-200 hover:border-emerald-300 hover:text-white"
          >
            CSV seed
          </Link>
          <Link
            to="/screener"
            className="rounded-lg border border-slate-700 px-4 py-2 text-sm font-medium text-slate-200 hover:border-emerald-300 hover:text-white"
          >
            Screener
          </Link>
        </div>
      </div>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]">
        <form
          onSubmit={submitPreview}
          className="rounded-2xl border border-slate-800 bg-slate-900/50 p-5 sm:p-6"
        >
          <div className="grid gap-5 lg:grid-cols-2">
            <label className="block text-sm font-medium text-slate-200">
              Template
              <select
                value={selectedTemplateId}
                onChange={(event) => applyTemplate(event.target.value)}
                className="mt-2 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-emerald-400"
              >
                <option value="">Custom criteria</option>
                {(templatesQuery.data ?? []).map((template) => (
                  <option key={template.id} value={template.id}>
                    {template.name}
                  </option>
                ))}
              </select>
              {selectedTemplate && (
                <span className="mt-2 block text-xs leading-5 text-slate-400">
                  {selectedTemplate.description}
                </span>
              )}
              {templatesQuery.error && (
                <span className="mt-2 block text-xs text-rose-200">
                  {templatesQuery.error.message}
                </span>
              )}
            </label>

            <label className="block text-sm font-medium text-slate-200">
              Sort
              <select
                value={criteria.sortBy}
                onChange={(event) =>
                  update("sortBy", event.target.value as UniverseSortBy)
                }
                className="mt-2 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-emerald-400"
              >
                {sortOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
          </div>

          <div className="mt-5 grid gap-5 lg:grid-cols-3">
            <MultiSelect
              label="Exchanges"
              options={exchangeOptions}
              value={criteria.exchanges}
              onChange={(value) => update("exchanges", value)}
            />
            <MultiSelect
              label="Countries"
              options={countryOptions}
              value={criteria.countries}
              onChange={(value) => update("countries", value)}
            />
            <MultiSelect
              label={criteria.excludeSectors ? "Excluded sectors" : "Sectors"}
              options={sectorOptions}
              value={criteria.sectors}
              onChange={(value) => update("sectors", value)}
            />
          </div>

          <label className="mt-4 flex items-center gap-3 text-sm text-slate-300">
            <input
              type="checkbox"
              checked={criteria.excludeSectors}
              onChange={(event) => update("excludeSectors", event.target.checked)}
              className="h-4 w-4 rounded border-slate-600 bg-slate-950 text-emerald-400"
            />
            Treat selected sectors as exclusions
          </label>

          <div className="mt-5 grid gap-5 md:grid-cols-2 xl:grid-cols-4">
            <NumberField
              label="Market cap min"
              value={criteria.marketCapMin}
              onChange={(value) => update("marketCapMin", value)}
            />
            <NumberField
              label="Market cap max"
              value={criteria.marketCapMax}
              onChange={(value) => update("marketCapMax", value)}
            />
            <NumberField
              label="Minimum volume"
              value={criteria.volumeMin}
              onChange={(value) => update("volumeMin", value)}
            />
            <NumberField
              label="Max symbols"
              min={1}
              max={500}
              value={criteria.maxSymbols}
              onChange={(value) => update("maxSymbols", value ?? 100)}
            />
          </div>

          <div className="mt-6 flex flex-wrap items-center gap-3">
            <button
              disabled={previewMutation.isPending || seedMutation.isPending}
              className="rounded-lg bg-emerald-400 px-4 py-2.5 text-sm font-semibold text-slate-950 transition hover:bg-emerald-300 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {previewMutation.isPending ? "Previewing..." : "Preview universe"}
            </button>
            <button
              type="button"
              disabled={!preview || seedMutation.isPending || previewMutation.isPending}
              onClick={() => seedMutation.mutate(criteria)}
              className="rounded-lg border border-emerald-400/40 px-4 py-2.5 text-sm font-semibold text-emerald-200 transition hover:bg-emerald-400/10 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {seedMutation.isPending ? "Seeding..." : "Seed preview"}
            </button>
            {(previewMutation.error || seedMutation.error) && (
              <p role="alert" className="text-sm text-rose-200">
                {(previewMutation.error ?? seedMutation.error)?.message}
              </p>
            )}
          </div>
        </form>

        <aside className="space-y-6">
          <SummaryPanel
            preview={preview}
            seededCount={successfulSeeds}
            sectorDistribution={sectorDistribution}
            exchangeDistribution={exchangeDistribution}
          />
          <section className="rounded-2xl border border-slate-800 bg-slate-900/50 p-5">
            <h2 className="text-base font-semibold text-white">
              Restrictions
            </h2>
            <p className="mt-2 text-sm leading-6 text-slate-400">
              Persistent symbol exclusion is waiting on a backend contract. Use
              criteria filters before seeding; no local exclusion state is saved.
            </p>
            <button
              type="button"
              disabled
              className="mt-4 rounded-lg border border-slate-700 px-3 py-2 text-sm font-medium text-slate-500"
            >
              Exclusion persistence unavailable
            </button>
          </section>
        </aside>
      </div>

      {preview && <PreviewTable preview={preview} />}
      {seedResults && <SeedResultsTable results={seedResults} />}
    </section>
  );
}

function MultiSelect({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: string[];
  value: string[];
  onChange: (value: string[]) => void;
}): JSX.Element {
  return (
    <label className="block text-sm font-medium text-slate-200">
      {label}
      <select
        multiple
        value={value}
        onChange={(event) =>
          onChange(
            Array.from(event.currentTarget.selectedOptions).map(
              (option) => option.value,
            ),
          )
        }
        className="mt-2 h-36 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-emerald-400"
      >
        {options.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
      <input
        value={value.join(", ")}
        onChange={(event) => onChange(parseList(event.target.value))}
        className="mt-2 w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-xs text-slate-300 outline-none focus:border-emerald-400"
      />
    </label>
  );
}

function NumberField({
  label,
  value,
  onChange,
  min = 0,
  max,
}: {
  label: string;
  value: number | null;
  onChange: (value: number | null) => void;
  min?: number;
  max?: number;
}): JSX.Element {
  return (
    <label className="block text-sm font-medium text-slate-200">
      {label}
      <input
        type="number"
        min={min}
        max={max}
        value={value ?? ""}
        onChange={(event) =>
          onChange(event.target.value === "" ? null : Number(event.target.value))
        }
        className="mt-2 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-emerald-400"
      />
    </label>
  );
}

function SummaryPanel({
  preview,
  seededCount,
  sectorDistribution,
  exchangeDistribution,
}: {
  preview: UniversePreview | null;
  seededCount: number;
  sectorDistribution: Array<[string, number]>;
  exchangeDistribution: Array<[string, number]>;
}): JSX.Element {
  return (
    <section className="rounded-2xl border border-slate-800 bg-slate-900/50 p-5">
      <h2 className="text-base font-semibold text-white">
        Active workflow summary
      </h2>
      <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
        <div className="rounded-xl bg-slate-950/60 p-3">
          <dt className="text-slate-400">Matches</dt>
          <dd className="mt-1 text-xl font-semibold text-white">
            {preview?.totalMatches ?? "-"}
          </dd>
        </div>
        <div className="rounded-xl bg-slate-950/60 p-3">
          <dt className="text-slate-400">Returned</dt>
          <dd className="mt-1 text-xl font-semibold text-white">
            {preview?.returnedCount ?? "-"}
          </dd>
        </div>
        <div className="rounded-xl bg-slate-950/60 p-3">
          <dt className="text-slate-400">Seeded</dt>
          <dd className="mt-1 text-xl font-semibold text-white">
            {seededCount || "-"}
          </dd>
        </div>
        <div className="rounded-xl bg-slate-950/60 p-3">
          <dt className="text-slate-400">Last refresh</dt>
          <dd className="mt-1 text-sm font-semibold text-white">This session</dd>
        </div>
      </dl>
      {preview?.warning && (
        <p className="mt-4 rounded-lg border border-amber-300/30 bg-amber-300/10 px-3 py-2 text-sm text-amber-100">
          {preview.warning}
        </p>
      )}
      <DistributionList title="Sectors" items={sectorDistribution} />
      <DistributionList title="Exchanges" items={exchangeDistribution} />
    </section>
  );
}

function DistributionList({
  title,
  items,
}: {
  title: string;
  items: Array<[string, number]>;
}): JSX.Element {
  return (
    <div className="mt-4">
      <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-400">
        {title}
      </h3>
      {items.length ? (
        <div className="mt-2 space-y-2">
          {items.map(([label, count]) => (
            <div key={label} className="flex items-center justify-between gap-3 text-sm">
              <span className="truncate text-slate-300">{label}</span>
              <span className="font-semibold text-slate-100">{count}</span>
            </div>
          ))}
        </div>
      ) : (
        <p className="mt-2 text-sm text-slate-500">No preview yet.</p>
      )}
    </div>
  );
}

function PreviewTable({ preview }: { preview: UniversePreview }): JSX.Element {
  return (
    <section className="overflow-hidden rounded-2xl border border-slate-800 bg-slate-900/50">
      <div className="flex flex-col justify-between gap-3 border-b border-slate-800 px-5 py-4 sm:flex-row sm:items-center">
        <div>
          <h2 className="text-lg font-semibold text-white">Preview</h2>
          <p className="mt-1 text-sm text-slate-400">
            {preview.returnedCount} of {preview.totalMatches} matching symbols.
          </p>
        </div>
        {preview.capped && (
          <span className="rounded-full bg-amber-300/15 px-3 py-1 text-xs font-semibold text-amber-100 ring-1 ring-amber-300/25">
            Capped
          </span>
        )}
      </div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[820px] text-left text-sm">
          <thead className="bg-slate-950/50 text-xs uppercase tracking-wide text-slate-400">
            <tr>
              <th className="px-4 py-3">Symbol</th>
              <th className="px-4 py-3">Company</th>
              <th className="px-4 py-3">Exchange</th>
              <th className="px-4 py-3">Country</th>
              <th className="px-4 py-3">Sector</th>
              <th className="px-4 py-3">Market cap</th>
              <th className="px-4 py-3">Volume</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800">
            {preview.symbols.map((row) => (
              <tr key={row.symbol} className="text-slate-200">
                <td className="px-4 py-4 font-semibold text-emerald-300">
                  {row.symbol}
                </td>
                <td className="px-4 py-4">{row.companyName ?? "-"}</td>
                <td className="px-4 py-4">{row.exchange ?? "-"}</td>
                <td className="px-4 py-4">{row.country ?? "-"}</td>
                <td className="px-4 py-4">{row.sector ?? "-"}</td>
                <td className="px-4 py-4">{formatMoney(row.marketCap)}</td>
                <td className="px-4 py-4">{formatNumber(row.volume)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function SeedResultsTable({ results }: { results: SeedResult[] }): JSX.Element {
  return (
    <section className="overflow-hidden rounded-2xl border border-slate-800 bg-slate-900/50">
      <div className="border-b border-slate-800 px-5 py-4">
        <h2 className="text-lg font-semibold text-white">Seed results</h2>
        <p className="mt-1 text-sm text-slate-400">
          {results.filter((result) => !result.error).length}/{results.length} symbols seeded or refreshed.
        </p>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[900px] text-left text-sm">
          <thead className="bg-slate-950/50 text-xs uppercase tracking-wide text-slate-400">
            <tr>
              <th className="px-4 py-3">Symbol</th>
              <th className="px-4 py-3">Company</th>
              <th className="px-4 py-3">Context</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3">Monitoring</th>
              <th className="px-4 py-3">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800">
            {results.map((result) => (
              <tr key={result.symbol} className="align-top text-slate-200">
                <td className="px-4 py-4 font-semibold text-emerald-300">
                  {result.symbol}
                </td>
                <td className="px-4 py-4">{result.companyName ?? "-"}</td>
                <td className="px-4 py-4 text-slate-300">
                  <span className="block">{result.sector ?? "-"}</span>
                  <span className="text-xs text-slate-500">
                    {[result.exchange, result.country].filter(Boolean).join(" / ") || "-"}
                  </span>
                </td>
                <td className="px-4 py-4">
                  <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${seedStatusClass(result)}`}>
                    {result.error ?? result.status ?? "seeded"}
                  </span>
                </td>
                <td className="px-4 py-4 text-sm text-slate-400">
                  Ingestion-event links appear here when backend seed responses include event identifiers.
                </td>
                <td className="space-y-2 px-4 py-4">
                  {!result.error ? (
                    <>
                      <Link className="block font-semibold text-emerald-300 hover:text-emerald-200" to={`/securities/${encodeURIComponent(result.symbol)}`}>
                        Detail
                      </Link>
                      <Link className="block font-semibold text-emerald-300 hover:text-emerald-200" to={`/securities/${encodeURIComponent(result.symbol)}/review`}>
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
