import { useQuery } from '@tanstack/react-query'
import { useMemo, useRef, useState } from 'react'
import type { FormEvent, KeyboardEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { apiFetch } from '../api/client'
import { professionalApi } from '../api/professional'
import { availabilityClass, availabilityLabel } from '../lib/availability'

type SortField = 'totalScore' | 'marginOfSafety' | 'symbol' | 'companyName' | 'sector' | 'exchange' | 'priceToFfo'
type SortDirection = 'ASC' | 'DESC'
type Filters = {
  sector: string
  exchange: string
  minMarginOfSafety: string
  maxMarginOfSafety: string
  minValueScore: string
  minRoic: string
  maxDebtToEquity: string
  minDividendYield: string
  minRevenueGrowth: string
  piotroskiMin: string
  piotroskiMax: string
  altmanZone: string
  moatStrength: string
  sharesOutstandingTrend: string
  // RM3 (specs/2026-09-02-rm3-screener-security-detail-surfacing/): REIT-only filters — setting
  // any of these implicitly scopes results to REIT-classified securities, since every non-REIT
  // row has a null value for these fields (see the filter form's disclosure note below).
  maxPriceToFfo: string
  maxNetDebtToEbitda: string
  maxAffoPayoutRatio: string
}
type QueryState = Filters & { sortField: SortField; sortDirection: SortDirection; page: number; pageSize: number }
type Result = {
  symbol: string
  companyName: string
  sector: string | null
  exchange: string | null
  currentPrice: number | null
  compositeFairValue: number | null
  marginOfSafety: number | null
  totalScore: number | null
  recommendation: string | null
  scoreDate: string | null
  scoreAvailability: { status: string; reason: string; dataAsOf: string | null } | null
  piotroskiScore: number | null
  piotroskiAvailabilityStatus: string | null
  altmanZone: string | null
  altmanAvailabilityStatus: string | null
  moatStrength: string | null
  sharesOutstandingTrend: string | null
  sectorMetricCaveat: string | null
  // RM3: RM2-persisted REIT sector metrics — null for every non-REIT security.
  ffoPerShare: number | null
  affoPerShare: number | null
  priceToFfo: number | null
  priceToAffo: number | null
  netDebtToEbitda: number | null
  interestCoverageEbitda: number | null
  affoPayoutRatio: number | null
}
type Response = { results: Result[]; page: number; pageSize: number; totalElements: number; totalPages: number }
type Presets = Record<string, Partial<QueryState>>
type ConservativeCriterion = { key: string; label: string; currentValue: string; whyItMatters: string; relaxation: string }
type ConservativePreset = { name: string; description: string; criteria: Partial<QueryState>; criteriaSummary: ConservativeCriterion[]; decisionSupportNote: string }
type ConservativeEmptyStateDiagnostic = { likelyEliminators: ConservativeCriterion[]; suggestedRelaxations: string[]; currentCriteriaPreserved: boolean; decisionSupportNote: string }
type ComparisonMetric = { group: string; label: string; value: string; availabilityStatus: string; coverageNote: string }
type ComparisonRow = { symbol: string; companyName: string; metrics: ComparisonMetric[] }

const emptyFilters: Filters = {
  sector: '',
  exchange: '',
  minMarginOfSafety: '',
  maxMarginOfSafety: '',
  minValueScore: '',
  minRoic: '',
  maxDebtToEquity: '',
  minDividendYield: '',
  minRevenueGrowth: '',
  piotroskiMin: '',
  piotroskiMax: '',
  altmanZone: '',
  moatStrength: '',
  sharesOutstandingTrend: '',
  maxPriceToFfo: '',
  maxNetDebtToEbitda: '',
  maxAffoPayoutRatio: '',
}
const initialQuery: QueryState = { ...emptyFilters, sortField: 'totalScore', sortDirection: 'DESC', page: 0, pageSize: 20 }
const numericFields: Array<keyof Filters> = [
  'minMarginOfSafety',
  'maxMarginOfSafety',
  'minValueScore',
  'minRoic',
  'maxDebtToEquity',
  'minDividendYield',
  'minRevenueGrowth',
  'piotroskiMin',
  'piotroskiMax',
  'maxPriceToFfo',
  'maxNetDebtToEbitda',
  'maxAffoPayoutRatio',
]

async function getJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init)
  if (!response.ok) throw new Error(`Request failed (${response.status}).`)
  return response.json() as Promise<T>
}

function serialise(query: QueryState): Record<string, string | number | null> {
  const values: Record<string, string | number | null> = {
    sortField: query.sortField,
    sortDirection: query.sortDirection,
    page: query.page,
    pageSize: query.pageSize,
  }
  for (const [key, value] of Object.entries(query)) {
    if (key in values || value === '') continue
    values[key] = numericFields.includes(key as keyof Filters) ? Number(value) : value
  }
  return values
}

function formatNumber(value: number | null, options: Intl.NumberFormatOptions = {}): string {
  return value == null ? '-' : new Intl.NumberFormat('en-US', { maximumFractionDigits: 2, ...options }).format(value)
}

function formatPercent(value: number | null): string {
  return value == null ? '-' : `${formatNumber(value)}%`
}

function statusClass(value: number | null): string {
  if (value == null) return 'bg-slate-700 text-slate-200'
  if (value >= 15) return 'bg-emerald-400/15 text-emerald-200 ring-1 ring-emerald-300/25'
  if (value >= 5) return 'bg-amber-300/15 text-amber-100 ring-1 ring-amber-300/25'
  return 'bg-rose-400/15 text-rose-100 ring-1 ring-rose-300/25'
}

function zoneClass(zone: string | null | undefined): string {
  if (zone === 'SAFE') return 'bg-emerald-300/15 text-emerald-100'
  if (zone === 'GREY') return 'bg-amber-300/15 text-amber-100'
  if (zone === 'DISTRESS') return 'bg-rose-400/15 text-rose-100'
  return 'bg-slate-700 text-slate-200'
}

function qualityClass(value: string | null | undefined): string {
  if (value === 'WIDE' || value === 'NARROW' || value === 'NET_BUYBACK' || value === 'STABLE') return 'bg-emerald-300/15 text-emerald-100'
  if (value === 'NONE' || value === 'NET_DILUTER') return 'bg-rose-400/15 text-rose-100'
  return 'bg-slate-700 text-slate-200'
}

function statusText(value: string | null | undefined): string {
  return availabilityLabel(value)
}

function Field({ label, value, onChange, placeholder }: { label: string; value: string; onChange: (value: string) => void; placeholder?: string }): JSX.Element {
  return (
    <label className="block text-sm font-medium text-slate-200">
      {label}
      <input value={value} onChange={(event) => onChange(event.target.value)} inputMode="decimal" placeholder={placeholder} className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none transition placeholder:text-slate-600 focus:border-emerald-400 focus:ring-2 focus:ring-emerald-400/25" />
    </label>
  )
}

export function ScreenerPage(): JSX.Element {
  const navigate = useNavigate()
  const [filters, setFilters] = useState<Filters>(emptyFilters)
  const [query, setQuery] = useState<QueryState>(initialQuery)
  const [error, setError] = useState<string | null>(null)
  const [filtersOpen, setFiltersOpen] = useState(false)
  const [expandedSymbol, setExpandedSymbol] = useState<string | null>(null)
  const filterButton = useRef<HTMLButtonElement>(null)
  const sectors = useQuery({ queryKey: ['screener', 'sectors'], queryFn: () => getJson<string[]>('/api/v1/screener/sectors') })
  const exchanges = useQuery({ queryKey: ['screener', 'exchanges'], queryFn: () => getJson<string[]>('/api/v1/screener/exchanges') })
  const presets = useQuery({ queryKey: ['screener', 'presets'], queryFn: () => getJson<Presets>('/api/v1/screener/presets') })
  const conservativePreset = useQuery({ queryKey: ['conservative-workflow', 'preset'], queryFn: () => getJson<ConservativePreset>('/api/v1/conservative-workflow/preset') })
  const comparison = useQuery({ queryKey: ['conservative-workflow', 'agent-one-comparison'], queryFn: () => getJson<ComparisonRow[]>('/api/v1/conservative-workflow/agent-one-comparison') })
  const competence = useQuery({ queryKey: ['competence-preferences'], queryFn: professionalApi.competence, retry: false })
  const results = useQuery({
    queryKey: ['screener', 'results', query],
    queryFn: () => getJson<Response>('/api/v1/screener', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(serialise(query)) }),
    placeholderData: (previous) => previous,
  })
  const emptyStateDiagnostics = useQuery({
    queryKey: ['conservative-workflow', 'empty-state-diagnostics', query],
    queryFn: () => getJson<ConservativeEmptyStateDiagnostic>('/api/v1/conservative-workflow/empty-state-diagnostics', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(serialise(query)) }),
    enabled: Boolean(results.data && results.data.results.length === 0),
  })
  const activeFilterCount = useMemo(() => Object.keys(emptyFilters).filter((key) => query[key as keyof Filters] !== '').length, [query])
  const update = (field: keyof Filters, value: string) => setFilters((current) => ({ ...current, [field]: value }))

  function apply(event?: FormEvent): void {
    event?.preventDefault()
    const min = Number(filters.minMarginOfSafety)
    const max = Number(filters.maxMarginOfSafety)
    if (filters.minMarginOfSafety !== '' && filters.maxMarginOfSafety !== '' && min > max) {
      setError('Minimum margin of safety cannot exceed the maximum.')
      return
    }
    const invalid = numericFields.some((field) => filters[field] !== '' && !Number.isFinite(Number(filters[field])))
    if (invalid) {
      setError('Use valid numeric values for all numeric filters.')
      return
    }
    setError(null)
    setQuery((current) => ({ ...current, ...filters, page: 0 }))
    setFiltersOpen(false)
    filterButton.current?.focus()
  }

  function reset(): void {
    setFilters(emptyFilters)
    setError(null)
    setQuery(initialQuery)
  }

  function usePreset(name: string): void {
    const preset = presets.data?.[name]
    if (!preset) return
    const next = { ...emptyFilters, ...Object.fromEntries(Object.entries(preset).filter(([key]) => key in emptyFilters).map(([key, value]) => [key, value == null ? '' : String(value)])) } as Filters
    setFilters(next)
    setError(null)
    setQuery((current) => ({ ...current, ...next, sortField: (preset.sortField as SortField) || current.sortField, sortDirection: (preset.sortDirection as SortDirection) || current.sortDirection, page: 0 }))
  }

  function sort(field: SortField): void {
    setQuery((current) => ({ ...current, sortField: field, sortDirection: current.sortField === field && current.sortDirection === 'DESC' ? 'ASC' : 'DESC', page: 0 }))
  }

  function rowKeyDown(event: KeyboardEvent<HTMLTableRowElement>, symbol: string): void {
    if (event.target !== event.currentTarget) return
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      navigate(`/securities/${symbol}`)
    }
  }

  const header = (label: string, field: SortField): JSX.Element => (
    <th scope="col" aria-sort={query.sortField === field ? (query.sortDirection === 'ASC' ? 'ascending' : 'descending') : 'none'} className="whitespace-nowrap px-4 py-3 text-left">
      <button type="button" onClick={() => sort(field)} className="inline-flex items-center gap-1 font-semibold text-slate-300 hover:text-white focus:outline-none focus:ring-2 focus:ring-emerald-400/50">
        {label}{query.sortField === field && <span aria-hidden="true">{query.sortDirection === 'ASC' ? 'up' : 'down'}</span>}
      </button>
    </th>
  )

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-white">Screener</h1>
          <p aria-live="polite" className="mt-1 text-sm text-slate-400">{results.isLoading ? 'Finding companies...' : results.isError ? 'Results unavailable' : `${formatNumber(results.data?.totalElements ?? 0)} companies found`}{results.isFetching && !results.isLoading ? ' · Updating...' : ''}</p>
        </div>
        <button ref={filterButton} type="button" aria-expanded={filtersOpen} aria-controls="screen-filters" onClick={() => setFiltersOpen((open) => !open)} className="min-h-11 rounded-lg border border-emerald-400/40 px-4 py-2 text-sm font-semibold text-emerald-200 hover:bg-emerald-400/10 focus-visible:outline focus-visible:outline-2 focus-visible:outline-emerald-400">Filters · {activeFilterCount}</button>
      </div>

      <section aria-labelledby="filter-heading" className="rounded-2xl border border-slate-800 bg-slate-900/50 p-4 sm:p-5">
        <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
          <div>
            <h2 id="filter-heading" className="text-sm font-semibold text-white">Research presets</h2>
            {competence.data?.preferredSectors.length ? <p className="mt-2 text-xs text-amber-100">Rows outside your marked sectors are labelled in the results table.</p> : null}
          </div>
          <div className="flex flex-wrap gap-2">{['graham', 'dividend', 'quality', 'conservative'].map((preset) => <button key={preset} type="button" disabled={presets.isLoading || !presets.data?.[preset]} onClick={() => usePreset(preset)} className="min-h-11 rounded-lg border border-emerald-400/30 px-3 py-2 text-sm font-medium capitalize text-emerald-200 transition hover:bg-emerald-400/10 disabled:cursor-wait disabled:opacity-50">{preset}</button>)}</div>
        </div>
        {presets.isError && <p role="alert" className="mt-3 text-sm text-amber-200">Research presets are unavailable right now.</p>}
        {conservativePreset.data && (
          <details className="mt-3 rounded-xl border border-slate-800 bg-slate-950/40 p-3">
            <summary className="min-h-11 cursor-pointer py-3 text-sm font-medium text-slate-300 focus-visible:outline focus-visible:outline-2 focus-visible:outline-emerald-400">About the conservative preset</summary>
            <div className="flex flex-col justify-between gap-3 lg:flex-row lg:items-start">
              <div>
                <p className="text-sm font-semibold text-white">Conservative preset</p>
                <p className="mt-1 max-w-3xl text-sm leading-6 text-slate-400">{conservativePreset.data.description}</p>
              </div>
              <button type="button" onClick={() => usePreset('conservative')} className="w-fit rounded-lg bg-emerald-400 px-3 py-2 text-sm font-semibold text-slate-950 transition hover:bg-emerald-300">Apply preset</button>
            </div>
            <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
              {conservativePreset.data.criteriaSummary.map((criterion) => (
                <div key={criterion.key} className="rounded-lg border border-slate-800 bg-slate-900/60 p-3">
                  <p className="text-sm font-semibold text-slate-100">{criterion.label}</p>
                  <p className="mt-1 text-xs font-medium text-emerald-200">{criterion.currentValue}</p>
                  <p className="mt-2 text-xs leading-5 text-slate-400">{criterion.whyItMatters}</p>
                </div>
              ))}
            </div>
          </details>
        )}
        <form id="screen-filters" hidden={!filtersOpen} className="mt-5 space-y-5" onSubmit={apply}>
          <h3 className="text-lg font-semibold text-white">Screen criteria</h3>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <label className="block text-sm font-medium text-slate-200">Sector<select value={filters.sector} onChange={(event) => update('sector', event.target.value)} className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-emerald-400 focus:ring-2 focus:ring-emerald-400/25"><option value="">All sectors</option>{sectors.data?.map((sector) => <option key={sector} value={sector}>{sector}</option>)}</select></label>
            <label className="block text-sm font-medium text-slate-200">Exchange<select value={filters.exchange} onChange={(event) => update('exchange', event.target.value)} className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-emerald-400 focus:ring-2 focus:ring-emerald-400/25"><option value="">All exchanges</option>{exchanges.data?.map((exchange) => <option key={exchange} value={exchange}>{exchange}</option>)}</select></label>
            <Field label="Min. margin of safety (%)" value={filters.minMarginOfSafety} onChange={(value) => update('minMarginOfSafety', value)} placeholder="e.g. 15" />
            <Field label="Max. margin of safety (%)" value={filters.maxMarginOfSafety} onChange={(value) => update('maxMarginOfSafety', value)} placeholder="Optional" />
            <Field label="Min. value score" value={filters.minValueScore} onChange={(value) => update('minValueScore', value)} />
            <Field label="Min. ROIC (%)" value={filters.minRoic} onChange={(value) => update('minRoic', value)} />
            <Field label="Max. debt / equity" value={filters.maxDebtToEquity} onChange={(value) => update('maxDebtToEquity', value)} />
            <Field label="Min. dividend yield (%)" value={filters.minDividendYield} onChange={(value) => update('minDividendYield', value)} />
            <Field label="Min. revenue growth (%)" value={filters.minRevenueGrowth} onChange={(value) => update('minRevenueGrowth', value)} />
            <Field label="Min. Piotroski F-Score" value={filters.piotroskiMin} onChange={(value) => update('piotroskiMin', value)} placeholder="0-9" />
            <Field label="Max. Piotroski F-Score" value={filters.piotroskiMax} onChange={(value) => update('piotroskiMax', value)} placeholder="0-9" />
            <label className="block text-sm font-medium text-slate-200">Altman zone<select value={filters.altmanZone} onChange={(event) => update('altmanZone', event.target.value)} className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-emerald-400 focus:ring-2 focus:ring-emerald-400/25"><option value="">Any zone</option><option value="SAFE">Safe</option><option value="GREY">Grey</option><option value="DISTRESS">Distress</option></select></label>
            <label className="block text-sm font-medium text-slate-200">Moat strength<select value={filters.moatStrength} onChange={(event) => update('moatStrength', event.target.value)} className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-emerald-400 focus:ring-2 focus:ring-emerald-400/25"><option value="">Any moat</option><option value="WIDE">Wide</option><option value="NARROW">Narrow</option><option value="NONE">None</option><option value="INSUFFICIENT_DATA">Insufficient data</option></select></label>
            <label className="block text-sm font-medium text-slate-200">Shares trend<select value={filters.sharesOutstandingTrend} onChange={(event) => update('sharesOutstandingTrend', event.target.value)} className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-emerald-400 focus:ring-2 focus:ring-emerald-400/25"><option value="">Any trend</option><option value="NET_BUYBACK">Net buyback</option><option value="STABLE">Stable</option><option value="NET_DILUTER">Net diluter</option><option value="INSUFFICIENT_DATA">Insufficient data</option></select></label>
            <Field label="Max. P/FFO (REIT)" value={filters.maxPriceToFfo} onChange={(value) => update('maxPriceToFfo', value)} placeholder="REIT only" />
            <Field label="Max. Net Debt/EBITDA (REIT)" value={filters.maxNetDebtToEbitda} onChange={(value) => update('maxNetDebtToEbitda', value)} placeholder="REIT only" />
            <Field label="Max. AFFO payout ratio (REIT)" value={filters.maxAffoPayoutRatio} onChange={(value) => update('maxAffoPayoutRatio', value)} placeholder="REIT only" />
          </div>
          {(filters.maxPriceToFfo || filters.maxNetDebtToEbitda || filters.maxAffoPayoutRatio) && (
            <p className="text-xs leading-5 text-amber-100">These three filters are REIT-specific — setting any of them narrows results to REIT-classified securities, since every other sector has no P/FFO, Net Debt/EBITDA, or AFFO payout value to compare against.</p>
          )}
          {error && <p role="alert" className="rounded-lg border border-rose-300/30 bg-rose-400/10 px-3 py-2 text-sm text-rose-100">{error}</p>}
          {(sectors.isError || exchanges.isError) && <p role="alert" className="text-sm text-amber-200">Some filter choices could not be loaded; you can still run a screen.</p>}
          <div className="flex flex-wrap gap-3">
            <button className="rounded-lg bg-emerald-400 px-4 py-2.5 text-sm font-semibold text-slate-950 transition hover:bg-emerald-300">Apply screen</button>
            <button type="button" onClick={reset} className="rounded-lg border border-slate-700 px-4 py-2.5 text-sm font-semibold text-slate-200 transition hover:bg-slate-800">Reset</button>
          </div>
        </form>
      </section>

      <section aria-labelledby="results-heading" className="overflow-hidden rounded-2xl border border-slate-800 bg-slate-900/50">
        <div className="flex flex-col justify-between gap-3 border-b border-slate-800 px-5 py-5 sm:flex-row sm:items-center sm:px-6">
          <div>
            <h2 id="results-heading" className="text-lg font-semibold text-white">Screen results</h2>
            <p aria-live="polite" className="mt-1 text-sm text-slate-400">{results.isLoading ? 'Finding companies...' : `${formatNumber(results.data?.totalElements ?? 0)} ${results.data?.totalElements === 1 ? 'company' : 'companies'} found`}{results.isFetching && !results.isLoading ? ' - Updating...' : ''}</p>
          </div>
          <label className="flex items-center gap-2 text-sm text-slate-300">Rows<select value={query.pageSize} onChange={(event) => setQuery((current) => ({ ...current, pageSize: Number(event.target.value), page: 0 }))} className="rounded-lg border border-slate-700 bg-slate-950 px-2 py-1.5 text-white focus:border-emerald-400"><option value={10}>10</option><option value={20}>20</option><option value={50}>50</option></select></label>
        </div>
        {results.isError ? (
          <div role="alert" className="m-6 rounded-xl border border-rose-300/30 bg-rose-400/10 p-4 text-sm text-rose-100">Unable to load the screener right now. <button type="button" onClick={() => void results.refetch()} className="font-semibold underline">Try again</button></div>
        ) : results.isLoading ? (
          <div className="p-12 text-center text-slate-400">Loading screening data...</div>
        ) : results.data?.results.length === 0 ? (
          <div className="p-6 sm:p-8">
            <div className="mx-auto max-w-4xl text-center">
              <p className="text-lg font-medium text-white">No companies match these criteria.</p>
              <p className="mt-2 text-sm text-slate-400">The current criteria are still preserved. Review which filters may be narrowing the universe before changing them.</p>
              <p className="mt-2 text-sm text-slate-400">Agent 1 comparison rows below come from a separate validation snapshot, so they can still appear when the live screener table is empty.</p>
              <button type="button" onClick={reset} className="mt-4 text-sm font-semibold text-emerald-300 underline">Reset filters</button>
            </div>
            {emptyStateDiagnostics.data && (
              <div className="mt-8 grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(18rem,0.8fr)]">
                <div className="rounded-xl border border-slate-800 bg-slate-950/40 p-4">
                  <h3 className="text-sm font-semibold text-white">Likely filter pressure</h3>
                  <div className="mt-4 grid gap-3 md:grid-cols-2">
                    {emptyStateDiagnostics.data.likelyEliminators.map((criterion) => (
                      <div key={criterion.key} className="rounded-lg border border-slate-800 bg-slate-900/60 p-3 text-left">
                        <p className="text-sm font-semibold text-slate-100">{criterion.label}</p>
                        <p className="mt-1 text-xs font-medium text-emerald-200">{criterion.currentValue}</p>
                        <p className="mt-2 text-xs leading-5 text-slate-400">{criterion.relaxation}</p>
                      </div>
                    ))}
                  </div>
                </div>
                <div className="rounded-xl border border-slate-800 bg-slate-950/40 p-4 text-left">
                  <h3 className="text-sm font-semibold text-white">Research relaxations</h3>
                  <ul className="mt-3 space-y-2 text-sm leading-6 text-slate-300">
                    {emptyStateDiagnostics.data.suggestedRelaxations.map((item) => <li key={item}>{item}</li>)}
                  </ul>
                  <p className="mt-4 text-xs leading-5 text-slate-400">{emptyStateDiagnostics.data.decisionSupportNote}</p>
                </div>
              </div>
            )}
          </div>
        ) : (
          <>
            <div className="lg:hidden">
              <div className="flex flex-wrap items-end gap-3 border-b border-slate-800 p-4">
                <label className="min-w-0 flex-1 text-sm text-slate-300">Sort by
                  <select value={query.sortField} onChange={(event) => setQuery((current) => ({ ...current, sortField: event.target.value as SortField, page: 0 }))} className="mt-1 min-h-11 w-full rounded-lg border border-slate-700 bg-slate-950 px-2 text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-emerald-400">
                    <option value="totalScore">Value score</option><option value="marginOfSafety">Margin of safety</option><option value="companyName">Company</option><option value="sector">Sector</option><option value="exchange">Exchange</option><option value="priceToFfo">P/FFO</option>
                  </select>
                </label>
                <button type="button" onClick={() => setQuery((current) => ({ ...current, sortDirection: current.sortDirection === 'DESC' ? 'ASC' : 'DESC', page: 0 }))} className="min-h-11 rounded-lg border border-slate-700 px-3 text-sm text-slate-200 focus-visible:outline focus-visible:outline-2 focus-visible:outline-emerald-400">{query.sortDirection === 'DESC' ? 'Descending' : 'Ascending'}</button>
              </div>
              <ul aria-label="Companies found" className="divide-y divide-slate-700">
                {results.data?.results.map((item) => (
                  <li key={item.symbol}>
                    <h3>
                      <button type="button" id={`company-toggle-${item.symbol}`} aria-expanded={expandedSymbol === item.symbol} aria-controls={`company-panel-${item.symbol}`} onClick={() => setExpandedSymbol((current) => current === item.symbol ? null : item.symbol)} className="flex min-h-11 w-full items-center justify-between gap-3 p-4 text-left hover:bg-slate-800/50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-emerald-400">
                        <span className="min-w-0"><span className="block text-sm font-semibold text-white">{item.companyName}</span><span className="mt-1 block text-xs font-medium text-emerald-300">{item.symbol}</span></span>
                        <svg aria-hidden="true" className="shrink-0 text-slate-300" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d={expandedSymbol === item.symbol ? 'm6 15 6-6 6 6' : 'm6 9 6 6 6-6'} /></svg>
                      </button>
                    </h3>
                    <div id={`company-panel-${item.symbol}`} hidden={expandedSymbol !== item.symbol} className="space-y-3 px-4 pb-4">
                    <p className="text-xs text-slate-400">{item.sector ?? 'Sector unavailable'} · {item.exchange ?? 'Exchange unavailable'}</p>
                    <p className="text-sm font-medium text-slate-200">{item.recommendation?.replace(/_/g, ' ') ?? 'Recommendation unavailable'}</p>
                    <dl className="grid grid-cols-2 gap-3 text-sm">
                      <div><dt className="text-xs text-slate-400">Price</dt><dd className="mt-1 font-medium text-white">{formatNumber(item.currentPrice, { style: 'currency', currency: 'USD' })}</dd></div>
                      <div><dt className="text-xs text-slate-400">Fair value</dt><dd className="mt-1 font-medium text-white">{formatNumber(item.compositeFairValue, { style: 'currency', currency: 'USD' })}</dd></div>
                      <div><dt className="text-xs text-slate-400">Margin of safety</dt><dd className={`mt-1 w-fit rounded px-2 py-1 font-semibold ${statusClass(item.marginOfSafety)}`}>{formatPercent(item.marginOfSafety)}</dd></div>
                      <div><dt className="text-xs text-slate-400">Value score</dt><dd className="mt-1 font-medium text-white">{formatNumber(item.totalScore)}<span className="block text-xs font-normal text-slate-400">{statusText(item.scoreAvailability?.status)}</span></dd></div>
                    </dl>
                    {item.sector && competence.data?.preferredSectors.length ? <p className="text-xs text-amber-100">{competence.data.preferredSectors.includes(item.sector) ? 'Inside' : 'Outside'} your preferred sectors</p> : null}
                    {item.sectorMetricCaveat && <p className="text-xs leading-5 text-amber-100">{item.sectorMetricCaveat}</p>}
                    <details>
                      <summary className="min-h-11 cursor-pointer py-3 text-sm text-emerald-200 focus-visible:outline focus-visible:outline-2 focus-visible:outline-emerald-400">More indicators</summary>
                      <dl className="grid grid-cols-2 gap-3 py-2 text-sm">
                        {[
                          ['Piotroski', `${item.piotroskiScore == null ? '—' : `${item.piotroskiScore}/9`} · ${statusText(item.piotroskiAvailabilityStatus)}`],
                          ['Altman', `${item.altmanZone ?? 'Unavailable'} · ${statusText(item.altmanAvailabilityStatus)}`],
                          ['Moat', item.moatStrength?.replace(/_/g, ' ') ?? 'Unavailable'],
                          ['Shares trend', item.sharesOutstandingTrend?.replace(/_/g, ' ') ?? 'Unavailable'],
                          ['P/FFO', formatNumber(item.priceToFfo)], ['P/AFFO', formatNumber(item.priceToAffo)],
                          ['Debt/EBITDA', formatNumber(item.netDebtToEbitda)], ['AFFO payout', item.affoPayoutRatio == null ? '—' : formatPercent(item.affoPayoutRatio * 100)],
                        ].map(([label, value]) => <div key={label}><dt className="text-xs text-slate-400">{label}</dt><dd className="mt-1 break-words text-slate-200">{value}</dd></div>)}
                      </dl>
                      {item.scoreAvailability?.reason && <p className="text-xs leading-5 text-slate-400">{item.scoreAvailability.reason}</p>}
                    </details>
                    <p className="text-xs text-slate-400">As of {item.scoreDate ?? 'unavailable'}</p>
                    <div className="grid grid-cols-2 gap-3">
                      <Link to={`/securities/${item.symbol}`} aria-label={`Details ${item.symbol}`} className="flex min-h-11 items-center justify-center rounded-lg border border-slate-700 px-3 py-2 text-sm font-semibold text-slate-200 hover:bg-slate-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-emerald-400">Details</Link>
                      <Link to={`/securities/${item.symbol}/review`} aria-label={`Review ${item.symbol}`} className="flex min-h-11 items-center justify-center rounded-lg border border-emerald-400/40 px-3 py-2 text-sm font-semibold text-emerald-200 hover:bg-emerald-400/10 focus-visible:outline focus-visible:outline-2 focus-visible:outline-emerald-400">Review</Link>
                    </div>
                    </div>
                  </li>
                ))}
              </ul>
            </div>
            <div className="hidden overflow-x-auto lg:block">
              <table className="min-w-[1900px] w-full border-collapse text-sm">
                <thead className="bg-slate-950/50 text-xs uppercase tracking-wide text-slate-400">
                  <tr>{header('Company', 'companyName')}{header('Sector', 'sector')}<th className="whitespace-nowrap px-4 py-3 text-left">Competence</th>{header('Exchange', 'exchange')}<th className="whitespace-nowrap px-4 py-3 text-left">Price</th><th className="whitespace-nowrap px-4 py-3 text-left">Fair value</th>{header('MoS', 'marginOfSafety')}{header('Value score', 'totalScore')}<th className="whitespace-nowrap px-4 py-3 text-left">Piotroski</th><th className="whitespace-nowrap px-4 py-3 text-left">Altman</th><th className="whitespace-nowrap px-4 py-3 text-left">Moat</th><th className="whitespace-nowrap px-4 py-3 text-left">Shares trend</th>{header('P/FFO', 'priceToFfo')}<th className="whitespace-nowrap px-4 py-3 text-left">P/AFFO</th><th className="whitespace-nowrap px-4 py-3 text-left">Debt/EBITDA</th><th className="whitespace-nowrap px-4 py-3 text-left">AFFO payout</th><th className="whitespace-nowrap px-4 py-3 text-left">Recommendation</th><th className="whitespace-nowrap px-4 py-3 text-left">As of</th><th className="whitespace-nowrap px-4 py-3 text-left">Review</th></tr>
                </thead>
                <tbody className="divide-y divide-slate-800">
                  {results.data?.results.map((item) => (
                    <tr key={item.symbol} tabIndex={0} onClick={() => navigate(`/securities/${item.symbol}`)} onKeyDown={(event) => rowKeyDown(event, item.symbol)} className="cursor-pointer text-slate-200 outline-none transition hover:bg-slate-800/70 focus:bg-slate-800/70 focus:ring-2 focus:ring-inset focus:ring-emerald-400">
                      <td className="px-4 py-4"><span className="block font-semibold text-white">{item.companyName}</span><span className="text-xs font-medium text-emerald-300">{item.symbol}</span></td>
                      <td className="px-4 py-4">
                        <span>{item.sector ?? '-'}</span>
                        {item.sectorMetricCaveat && (
                          <span
                            title={item.sectorMetricCaveat}
                            className="ml-1.5 inline-flex cursor-help rounded-full bg-amber-300/15 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-amber-200"
                          >
                            sector caveat
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-4">{item.sector && competence.data?.preferredSectors.length ? <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${competence.data.preferredSectors.includes(item.sector) ? 'bg-emerald-300/15 text-emerald-100' : 'bg-amber-300/15 text-amber-100'}`}>{competence.data.preferredSectors.includes(item.sector) ? 'inside' : 'outside'}</span> : '-'}</td>
                      <td className="px-4 py-4">{item.exchange ?? '-'}</td>
                      <td className="px-4 py-4">{formatNumber(item.currentPrice, { style: 'currency', currency: 'USD' })}</td>
                      <td className="px-4 py-4">{formatNumber(item.compositeFairValue, { style: 'currency', currency: 'USD' })}</td>
                      <td className="px-4 py-4"><span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${statusClass(item.marginOfSafety)}`}>{formatPercent(item.marginOfSafety)}</span></td>
                      <td className="px-4 py-4"><span className="block font-medium text-white">{formatNumber(item.totalScore)}</span><span className={`mt-1 inline-flex rounded-full px-2 py-0.5 text-[11px] font-semibold ${availabilityClass(item.scoreAvailability?.status)}`}>{statusText(item.scoreAvailability?.status)}</span></td>
                      <td className="px-4 py-4"><span className="block font-medium text-white">{item.piotroskiScore == null ? '-' : `${item.piotroskiScore}/9`}</span><span className={`mt-1 inline-flex rounded-full px-2 py-0.5 text-[11px] font-semibold ${availabilityClass(item.piotroskiAvailabilityStatus)}`}>{statusText(item.piotroskiAvailabilityStatus)}</span></td>
                      <td className="px-4 py-4"><span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${zoneClass(item.altmanZone)}`}>{item.altmanZone?.replace(/_/g, ' ').toLowerCase() || 'unavailable'}</span></td>
                      <td className="px-4 py-4"><span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${qualityClass(item.moatStrength)}`}>{item.moatStrength?.replace(/_/g, ' ').toLowerCase() || 'unavailable'}</span></td>
                      <td className="px-4 py-4"><span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${qualityClass(item.sharesOutstandingTrend)}`}>{item.sharesOutstandingTrend?.replace(/_/g, ' ').toLowerCase() || 'unavailable'}</span></td>
                      <td className="px-4 py-4">{formatNumber(item.priceToFfo)}</td>
                      <td className="px-4 py-4">{formatNumber(item.priceToAffo)}</td>
                      <td className="px-4 py-4">{formatNumber(item.netDebtToEbitda)}</td>
                      <td className="px-4 py-4">{item.affoPayoutRatio == null ? '-' : formatPercent(item.affoPayoutRatio * 100)}</td>
                      <td className="px-4 py-4">{item.recommendation ?? '-'}</td>
                      <td className="px-4 py-4 text-slate-400">{item.scoreDate ?? '-'}</td>
                      <td className="px-4 py-4"><button type="button" onClick={(event) => { event.stopPropagation(); navigate(`/securities/${item.symbol}/review`) }} className="rounded-md border border-emerald-400/30 px-3 py-1.5 text-xs font-semibold text-emerald-200 hover:bg-emerald-400/10">Review</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="flex flex-col gap-3 border-t border-slate-800 px-5 py-4 text-sm text-slate-300 sm:flex-row sm:items-center sm:justify-between">
              <span>Page {results.data?.totalPages ? (results.data.page + 1) : 0} of {results.data?.totalPages ?? 0}</span>
              <div className="flex flex-wrap gap-2">
                <button type="button" disabled={!results.data || results.data.page === 0} onClick={() => setQuery((current) => ({ ...current, page: 0 }))} className="rounded-md border border-slate-700 px-3 py-1.5 disabled:cursor-not-allowed disabled:opacity-40">First</button>
                <button type="button" disabled={!results.data || results.data.page === 0} onClick={() => setQuery((current) => ({ ...current, page: Math.max(0, current.page - 1) }))} className="rounded-md border border-slate-700 px-3 py-1.5 disabled:cursor-not-allowed disabled:opacity-40">Previous</button>
                <button type="button" disabled={!results.data || results.data.page >= results.data.totalPages - 1} onClick={() => setQuery((current) => ({ ...current, page: current.page + 1 }))} className="rounded-md border border-slate-700 px-3 py-1.5 disabled:cursor-not-allowed disabled:opacity-40">Next</button>
                <button type="button" disabled={!results.data || results.data.page >= results.data.totalPages - 1} onClick={() => setQuery((current) => ({ ...current, page: Math.max(0, (results.data?.totalPages ?? 1) - 1) }))} className="rounded-md border border-slate-700 px-3 py-1.5 disabled:cursor-not-allowed disabled:opacity-40">Last</button>
              </div>
            </div>
          </>
        )}
      </section>

      <section aria-labelledby="agent-one-comparison-heading" className="overflow-hidden rounded-2xl border border-slate-800 bg-slate-900/50">
        <div className="border-b border-slate-800 px-5 py-5 sm:px-6">
          <p className="text-xs font-semibold uppercase tracking-[.22em] text-emerald-300">Conservative workflow</p>
          <h2 id="agent-one-comparison-heading" className="mt-2 text-lg font-semibold text-white">Agent 1 selected-symbol comparison</h2>
          <p className="mt-1 max-w-3xl text-sm leading-6 text-slate-400">Compare the validation symbols across valuation, score, quality, resilience, growth, dividend, and source coverage before opening individual review packets. These rows are replay evidence from the Agent 1 validation snapshot.</p>
        </div>
        {comparison.isLoading ? (
          <div className="p-8 text-sm text-slate-400">Loading comparison evidence...</div>
        ) : comparison.isError ? (
          <div className="m-6 rounded-xl border border-amber-300/30 bg-amber-300/5 p-4 text-sm text-amber-100">Comparison evidence is unavailable right now.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-[1200px] w-full border-collapse text-sm">
              <thead className="bg-slate-950/50 text-xs uppercase tracking-wide text-slate-400">
                <tr><th className="px-4 py-3 text-left">Symbol</th><th className="px-4 py-3 text-left">Valuation</th><th className="px-4 py-3 text-left">Score</th><th className="px-4 py-3 text-left">Quality</th><th className="px-4 py-3 text-left">Resilience</th><th className="px-4 py-3 text-left">Growth</th><th className="px-4 py-3 text-left">Dividend</th><th className="px-4 py-3 text-left">Coverage</th></tr>
              </thead>
              <tbody className="divide-y divide-slate-800">
                {comparison.data?.map((row) => {
                  const byGroup = Object.fromEntries(row.metrics.map((metric) => [metric.group, metric]))
                  return (
                    <tr key={row.symbol} className="text-slate-200">
                      <td className="px-4 py-4"><span className="block font-semibold text-white">{row.symbol}</span><span className="text-xs text-slate-400">{row.companyName}</span></td>
                      {['valuation', 'score', 'quality', 'resilience', 'growth', 'dividend', 'coverage'].map((group) => (
                        <td key={group} className="px-4 py-4">
                          <span className="block font-medium text-slate-100">{byGroup[group]?.value ?? '-'}</span>
                          <span className={`mt-1 inline-flex rounded-full px-2 py-0.5 text-[11px] font-semibold ${availabilityClass(byGroup[group]?.availabilityStatus)}`}>{statusText(byGroup[group]?.availabilityStatus)}</span>
                        </td>
                      ))}
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
      <p className="rounded-xl border border-slate-800 bg-slate-950/40 px-4 py-3 text-xs leading-5 text-slate-400">This screener is decision-support software, not personalised investment advice. Fair value and scoring are model outputs based on available data; review the underlying research before acting.</p>
    </div>
  )
}
