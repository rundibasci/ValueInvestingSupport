import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { apiFetch } from '../api/client'
import { portfolioApi, type Portfolio } from '../api/portfolio'
import { professionalApi, type ChecklistEvaluation, type Confidence, type Verification } from '../api/professional'
import { watchlistApi } from '../api/watchlist'
import { availabilityClass, availabilityLabel } from '../lib/availability'

type Detail = {
  symbol: string
  companyName: string
  sector: string | null
  exchange: string | null
  country: string | null
  currency: string | null
  marketCap: number | null
  description: string | null
  website: string | null
  currentPrice: number | null
  priceDate: string | null
  revenue: number | null
  netIncome: number | null
  fcf: number | null
  eps: number | null
  bvps: number | null
  pe: number | null
  roic: number | null
  dividendYield: number | null
  dataAsOf: string | null
}
type Annual = { fiscalYear: number; revenue: number | null; netIncome: number | null; fcf: number | null; eps: number | null; bvps: number | null; sharesOutstanding: number | null }
type Financials = { annuals: Annual[]; quarters: Array<{ period: string; revenue: number | null; netIncome: number | null; fcf: number | null; eps: number | null }>; ttm: { revenue: number | null; netIncome: number | null; fcf: number | null; eps: number | null } | null }
type RatioItem = { date: string; pe: number | null; roic: number | null; roe: number | null; debtToEquity: number | null; currentRatio: number | null; quickRatio: number | null; interestCoverage: number | null; grossMargin: number | null; dividendYield: number | null }
type Ratios = { ratios: RatioItem[] }
type DcfSensitivityCell = { wacc: number; terminalRate: number; fairValue: number | null; terminalValuePercentage: number | null; highTerminalDependence: boolean }
type DcfSensitivity = { waccValues: number[]; terminalRateValues: number[]; cells: DcfSensitivityCell[]; baseWacc: number | null; baseTerminalRate: number | null }
type WaccDetail = { wacc: number | null; riskFreeRate: number | null; equityRiskPremium: number | null; beta: number | null; costOfEquity: number | null; costOfDebt: number | null; debtWeight: number | null; equityWeight: number | null; effectiveTaxRate: number | null; fallbackUsed: boolean; source: string | null }
type GrahamChecklist = { passed: number; failed: number; insufficient: number; criteria: Array<{ code: string; label: string; status: string; actualValue: number | null }> }
type Valuation = { currentPrice: number | null; dcf: { base: number | null; low: number | null; high: number | null } | null; dcfTerminalValuePercentage: number | null; dcfHighTerminalDependence: boolean; sensitivity: DcfSensitivity | null; grahamNumber: number | null; ddmValue: number | null; epv: { fairValue: number | null; normalizedEarnings: number | null; yearsAveraged: number | null } | null; ownerEarnings: { value: number | null; maintenanceCapexEstimate: number | null } | null; compositeFairValue: number | null; marginOfSafety: number | null; mosLow: number | null; mosHigh: number | null; recommendation: string | null; analystEstimates: { priceTargetMean: number | null; priceTargetLow: number | null; priceTargetHigh: number | null; analystCount: number; consensus: string | null } | null; wacc: WaccDetail | null; grahamChecklist: GrahamChecklist | null; dataAsOf: string | null; disclaimer: string }
type Dividends = { history: Array<{ exDividendDate: string | null; paymentDate: string | null; amount: number | null; currency: string | null }>; streak: number; cagr3y: number | null; cagr5y: number | null; cagr10y: number | null }
type Metrics = { cagr3y: number | null; cagr5y: number | null; cagr10y: number | null }
type Growth = { revenue: Metrics; fcf: Metrics; eps: Metrics }
type Peers = { peers: Array<{ symbol: string; companyName: string; currentPrice: number | null; compositeFairValue: number | null; marginOfSafety: number | null; totalScore: number | null; pe: number | null; roic: number | null }> }
type Score = { totalScore: number | null; mosScore: number | null; qualityScore: number | null; safetyScore: number | null; growthScore: number | null; dividendScore: number | null; rawTotalScore: number | null; mosGateApplied: boolean; weightProfile: string | null; scoreDate: string | null; availability: Availability | null }
type Piotroski = { totalScore: number; factors: Record<string, boolean>; resultDate: string | null; availabilityStatus: string | null; availabilityMessage: string | null }
type Altman = { score: number | null; zone: string | null; formulaVariant: string | null; workingCapitalToAssets: number | null; retainedEarningsToAssets: number | null; ebitToAssets: number | null; marketValueEquityToLiabilities: number | null; salesToAssets: number | null; resultDate: string | null; availabilityStatus: string | null; availabilityMessage: string | null }
type Cyclicality = { classification: string | null; revenueCoefficient: number | null; earningsCoefficient: number | null; normalizedEarnings: number | null; cycleAdjustedPe: number | null; yearsAnalyzed: number; resultDate: string | null; availabilityStatus: string | null; availabilityMessage: string | null }
type EarningsQuality = { fcfToNetIncome: number | null; sloanAccrualsRatio: number | null; classification: string | null; deteriorating: boolean; yearsAnalyzed: number; resultDate: string | null; availabilityStatus: string | null; availabilityMessage: string | null }
type StabilityCriterion = { criterionCode: string; label: string; status: string; actualValue: number | null; message: string | null }
type Moat = { symbol: string; resultDate: string | null; moatStrength: string | null; roicTrend: string | null; yearsAnalyzed: number | null; yearsRoicAboveWacc: number | null; roicConsistencyPercentage: number | null; averageRoic: number | null; estimatedWacc: number | null; averageRoicSpread: number | null; trendSlope: number | null; reinvestmentRate: number | null; availabilityMessage: string | null; stabilityCriteria: StabilityCriterion[] }
type CapitalAllocation = { symbol: string; resultDate: string | null; sharesOutstandingTrend: string | null; classification: string | null; yearsAnalyzed: number | null; sharesChangePercentage: number | null; sharesCagr: number | null; dividendYield: number | null; netBuybackYield: number | null; totalShareholderYield: number | null; insiderOwnershipPercentage: number | null; acquisitionSpendToFcf: number | null; availabilityMessage: string | null }
type ValuationBandItem = { metric: string; yearsAnalyzed: number | null; currentValue: number | null; medianValue: number | null; percentile25: number | null; percentile75: number | null; currentPercentile: number | null; position: string | null; availabilityMessage: string | null }
type ValuationBands = { symbol: string; resultDate: string | null; bands: ValuationBandItem[] }
type FinancialHealth = { totalDebt: number | null; cash: number | null; netDebt: number | null; debtToEquity: number | null; currentRatio: number | null; quickRatio: number | null; interestCoverage: number | null; payoutRatio: number | null; dividendYield: number | null; grossMargin: number | null; operatingMargin: number | null; netMargin: number | null; dataAsOf: string | null }
type SourceCoverageItem = { category: string; provider: string | null; status: string; message: string | null }
type FreshnessItem = { category: string; dataAsOf: string | null; status: string; message: string | null }
type Availability = { status: string; reason: string; dataAsOf: string | null }
type AvailabilityItem = { category: string; state: Availability }
type DataQualityNote = { category: string; severity: string; message: string }
type AvailabilityDiagnostic = { status: string; exampleCategory: string; exampleReason: string; surfaces: string[]; conservativeInterpretation: string; decisionSupportNote: string }
type Review = {
  symbol: string
  detail: Detail
  financials: Financials
  ratios: Ratios
  valuation: Valuation | null
  dividends: Dividends
  growth: Growth
  peers: Peers
  score: Score | null
  piotroski: Piotroski | null
  altman: Altman | null
  cyclicality: Cyclicality | null
  earningsQuality: EarningsQuality | null
  moat: Moat | null
  capitalAllocation: CapitalAllocation | null
  valuationBands: ValuationBands | null
  financialHealth: FinancialHealth
  sourceCoverage: SourceCoverageItem[]
  freshness: FreshnessItem[]
  availability: AvailabilityItem[]
  dataQualityNotes: DataQualityNote[]
}

const money = (value: number | null | undefined, currency = 'USD') =>
  value == null ? 'Unavailable' : new Intl.NumberFormat('en-US', { style: 'currency', currency, maximumFractionDigits: 2, notation: Math.abs(value) >= 1000000 ? 'compact' : 'standard' }).format(value)
const number = (value: number | null | undefined) => value == null ? 'Unavailable' : new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(value)
const compact = (value: number | null | undefined) => value == null ? 'Unavailable' : new Intl.NumberFormat('en-US', { maximumFractionDigits: 2, notation: 'compact' }).format(value)
const percentPoint = (value: number | null | undefined) => value == null ? 'Unavailable' : `${new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(value)}%`
const ratioPercent = (value: number | null | undefined) => value == null ? 'Unavailable' : `${new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(value * 100)}%`
const date = (value: string | null | undefined) => value ? new Intl.DateTimeFormat('en-US', { dateStyle: 'medium' }).format(new Date(`${value}T00:00:00`)) : 'Unavailable'

class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message)
  }
}

async function json<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init)
  if (!response.ok) {
    const body = await response.json().catch(() => null) as { message?: string; detail?: string; error?: string } | null
    throw new ApiError(body?.message || body?.detail || body?.error || `Request failed (${response.status}).`, response.status)
  }
  return response.json() as Promise<T>
}

function Section({ id, title, children, aside }: { id: string; title: string; children: ReactNode; aside?: ReactNode }): JSX.Element {
  return (
    <section id={id} className="scroll-mt-20 border-t border-slate-800 py-8 first:border-t-0 first:pt-0">
      <div className="mb-5 flex flex-col justify-between gap-3 sm:flex-row sm:items-end">
        <h2 className="text-xl font-semibold text-white">{title}</h2>
        {aside}
      </div>
      {children}
    </section>
  )
}

function Panel({ title, children }: { title: string; children: ReactNode }): JSX.Element {
  return <div className="rounded-lg border border-slate-800 bg-slate-900/60 p-5"><h3 className="text-sm font-semibold uppercase tracking-wide text-slate-300">{title}</h3><div className="mt-4">{children}</div></div>
}

function Metric({ label, value, note }: { label: string; value: string; note?: string }): JSX.Element {
  const unavailable = value === 'Unavailable'
  return <div className={`rounded-lg border p-3 ${unavailable ? 'border-slate-800 bg-slate-950/40' : 'border-slate-800 bg-slate-950/60'}`}><dt className="text-xs uppercase tracking-wide text-slate-500">{label}</dt><dd className={`mt-1 font-medium ${unavailable ? 'text-slate-500' : 'text-white'}`}>{value}</dd>{note && <p className="mt-1 text-xs leading-5 text-slate-500">{note}</p>}</div>
}

function DataGap({ children }: { children: ReactNode }): JSX.Element {
  return <p className="rounded-lg border border-amber-300/20 bg-amber-300/5 p-3 text-sm leading-6 text-amber-100">{children}</p>
}

function InfoNote({ children }: { children: ReactNode }): JSX.Element {
  return <p className="rounded-lg border border-slate-700 bg-slate-950/50 p-3 text-sm leading-6 text-slate-300">{children}</p>
}

function professionalLevelClass(level: string | null | undefined): string {
  const normalized = (level || '').toUpperCase()
  if (normalized === 'HIGH' || normalized === 'PASS') return 'bg-emerald-300/15 text-emerald-100'
  if (normalized === 'LOW' || normalized === 'FAIL' || normalized === 'CRITICAL') return 'bg-rose-400/15 text-rose-100'
  if (normalized === 'INFO') return 'bg-slate-700/60 text-slate-200'
  return 'bg-amber-300/15 text-amber-100'
}

function ProfessionalReviewPanel({
  symbol,
  sector,
  confidence,
  verification,
  checklists,
}: {
  symbol: string
  sector: string | null
  confidence?: Confidence
  verification?: Verification
  checklists?: Array<{ id: string; name: string }>
}): JSX.Element {
  const [selectedChecklist, setSelectedChecklist] = useState('')
  const [evaluation, setEvaluation] = useState<ChecklistEvaluation | null>(null)
  const evaluate = useMutation({
    mutationFn: () => professionalApi.evaluateChecklist(selectedChecklist, symbol),
    onSuccess: setEvaluation,
  })

  return (
    <div className="grid gap-5 lg:grid-cols-3">
      <Panel title="Confidence">
        {confidence ? (
          <div className="space-y-3">
            <span className={`inline-flex rounded-full px-3 py-1 text-sm font-semibold ${professionalLevelClass(confidence.overallLevel)}`}>
              {confidence.overallLevel.toLowerCase()} confidence
            </span>
            {confidence.factors.map((factor) => (
              <div key={factor.name} className="rounded-lg border border-slate-800 bg-slate-950/50 p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="font-medium text-white">{factor.name}</p>
                  <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${professionalLevelClass(factor.level)}`}>{factor.level.toLowerCase()}</span>
                </div>
                <p className="mt-1 text-sm leading-5 text-slate-400">{factor.message}</p>
              </div>
            ))}
          </div>
        ) : <DataGap>Valuation confidence is unavailable for this symbol.</DataGap>}
      </Panel>
      <Panel title="Verification warnings">
        {verification?.flags.length ? (
          <div className="space-y-2">
            {verification.flags.map((flag) => (
              <p key={`${flag.field}-${flag.message}`} className={`rounded-lg p-3 text-sm leading-6 ${professionalLevelClass(flag.severity)}`}>
                <span className="font-semibold">{flag.field}:</span> {flag.message}
              </p>
            ))}
          </div>
        ) : <InfoNote>No cross-verification warnings are currently reported. This does not replace source filing review.</InfoNote>}
      </Panel>
      <Panel title="Checklist evaluation">
        {checklists?.length ? (
          <div className="space-y-3">
            <select value={selectedChecklist} onChange={(event) => { setSelectedChecklist(event.target.value); setEvaluation(null) }} className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white">
              <option value="">Choose checklist</option>
              {checklists.map((checklist) => <option key={checklist.id} value={checklist.id}>{checklist.name}</option>)}
            </select>
            <button type="button" disabled={!selectedChecklist || evaluate.isPending} onClick={() => evaluate.mutate()} className="rounded-lg bg-emerald-400 px-4 py-2 text-sm font-semibold text-slate-950 disabled:opacity-50">{evaluate.isPending ? 'Evaluating...' : 'Apply checklist'}</button>
            {evaluation && (
              <div className="space-y-2">
                <p className="text-sm text-slate-300">{evaluation.items.filter((item) => item.status === 'PASS').length} of {evaluation.items.length} criteria met.</p>
                {evaluation.items.map((item) => (
                  <div key={item.label} className="rounded-lg border border-slate-800 bg-slate-950/50 p-3 text-sm">
                    <div className="flex items-center justify-between gap-2"><span className="text-white">{item.label}</span><span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${professionalLevelClass(item.status)}`}>{item.status.toLowerCase()}</span></div>
                    <p className="mt-1 text-slate-400">{item.message || (item.actualValue == null ? 'Manual or unavailable criterion.' : `Actual value: ${number(item.actualValue)}`)}</p>
                  </div>
                ))}
              </div>
            )}
          </div>
        ) : <InfoNote>Create a checklist from the Checklists page before evaluating this symbol.</InfoNote>}
      </Panel>
      {sector && <p className="lg:col-span-3 rounded-lg border border-slate-800 bg-slate-950/50 p-3 text-sm text-slate-400">Circle-of-competence indicators use your saved sectors. Current sector: {sector}.</p>}
    </div>
  )
}

function AvailabilityBadge({ state }: { state?: Availability | null }): JSX.Element {
  const status = state?.status || 'MISSING_INTERNAL_COMPUTATION'
  return <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${availabilityClass(status)}`}>{availabilityLabel(status)}</span>
}

function AvailabilityDiagnosticsPanel({ diagnostics }: { diagnostics?: AvailabilityDiagnostic[] }): JSX.Element {
  if (!diagnostics?.length) return <DataGap>Availability diagnostics are unavailable. Existing status badges still show local endpoint evidence.</DataGap>
  return (
    <div className="mt-5 grid gap-3 lg:grid-cols-2">
      {diagnostics.map((item) => (
        <div key={item.status} className="rounded-lg border border-slate-800 bg-slate-950/50 p-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${availabilityClass(item.status)}`}>{availabilityLabel(item.status)}</span>
            <span className="text-xs uppercase tracking-wide text-slate-500">{item.exampleCategory}</span>
          </div>
          <p className="mt-3 text-sm leading-6 text-slate-300">{item.exampleReason}</p>
          <p className="mt-2 text-xs leading-5 text-slate-500">{item.conservativeInterpretation}</p>
          <p className="mt-2 text-xs leading-5 text-amber-100">{item.decisionSupportNote}</p>
          <p className="mt-2 text-xs text-slate-500">Surfaces: {item.surfaces.join(', ')}</p>
        </div>
      ))}
    </div>
  )
}

function StatusPill({ value }: { value: string | null | undefined }): JSX.Element {
  const normalized = value || 'MISSING_INTERNAL_COMPUTATION'
  const ok = normalized === 'AVAILABLE' || normalized === 'SAFE' || normalized === 'STRONG' || normalized === 'STABLE'
  const warn = normalized === 'STALE' || normalized === 'GREY' || normalized === 'MODERATE' || normalized === 'ACCEPTABLE'
  const classes = ok ? 'bg-emerald-300/15 text-emerald-100' : warn ? 'bg-amber-300/15 text-amber-100' : 'bg-slate-700 text-slate-200'
  return <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${classes}`}>{normalized.replace(/_/g, ' ').toLowerCase()}</span>
}

function QualityBadge({ value }: { value: string | null | undefined }): JSX.Element {
  const normalized = value || 'INSUFFICIENT_DATA'
  const favorable = ['WIDE', 'NARROW', 'IMPROVING', 'STABLE', 'NET_BUYBACK', 'DISCIPLINED_ALLOCATOR', 'HISTORICALLY_CHEAP', 'CHEAP', 'PASS']
  const caution = ['NONE', 'DECLINING', 'NET_DILUTER', 'EMPIRE_BUILDER', 'HISTORICALLY_EXPENSIVE', 'EXPENSIVE', 'FAIL']
  const classes = favorable.includes(normalized)
    ? 'bg-emerald-300/15 text-emerald-100'
    : caution.includes(normalized)
      ? 'bg-rose-400/15 text-rose-100'
      : normalized === 'INSUFFICIENT_DATA'
        ? 'bg-slate-700/60 text-slate-200'
      : 'bg-amber-300/15 text-amber-100'
  return <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${classes}`}>{normalized.replace(/_/g, ' ').toLowerCase()}</span>
}

const percentFromRatio = (value: number | null | undefined) => {
  if (value == null) return null
  return Math.abs(value) <= 1 ? value * 100 : value
}

function PercentTrendChart({ data, lines, summary }: { data: Array<Record<string, number | string | null>>; lines: Array<[string, string]>; summary: string }): JSX.Element {
  const visible = data.filter((item) => lines.some(([key]) => typeof item[key] === 'number'))
  if (!visible.length) return <DataGap>No historical series is available for this chart.</DataGap>
  const populatedSeries = lines.map(([key]) => numericSeries(visible, key)).filter((values) => values.length >= 2)
  if (!populatedSeries.length || populatedSeries.every((values) => new Set(values.map((value) => value.toFixed(6))).size <= 1)) {
    return textOnlySeries(visible, lines)
  }
  return (
    <div>
      <p className="mb-3 text-sm leading-6 text-slate-400">{summary}</p>
      <div className="h-72 min-h-72 min-w-0 w-full" role="img" aria-label={summary}>
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={visible}>
            <CartesianGrid stroke="#334155" strokeDasharray="3 3" />
            <XAxis dataKey="label" stroke="#94a3b8" />
            <YAxis stroke="#94a3b8" tickFormatter={(v) => `${number(Number(v))}%`} width={72} />
            <Tooltip formatter={(v) => `${number(Number(v))}%`} contentStyle={{ background: '#0f172a', border: '1px solid #334155', color: '#e2e8f0' }} />
            <Legend />
            {lines.map(([key, label], index) => <Line key={key} type="monotone" dataKey={key} name={label} stroke={['#34d399', '#fbbf24', '#60a5fa'][index]} strokeWidth={2} dot={false} connectNulls />)}
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

function ValuationBandMiniChart({ band }: { band: ValuationBandItem }): JSX.Element {
  const values = [band.percentile25, band.percentile75, band.medianValue, band.currentValue].filter((value): value is number => value != null)
  if (values.length < 3 || band.percentile25 == null || band.percentile75 == null || band.medianValue == null || band.currentValue == null) {
    return <DataGap>{band.availabilityMessage || `${band.metric} historical band is unavailable.`}</DataGap>
  }
  const min = Math.min(...values)
  const max = Math.max(...values)
  const span = max - min || 1
  const pct = (value: number) => `${Math.max(0, Math.min(100, ((value - min) / span) * 100))}%`
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-950/50 p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h4 className="font-semibold text-white">{band.metric}</h4>
          <p className="mt-1 text-xs text-slate-500">{band.yearsAnalyzed ?? 0} years analyzed</p>
        </div>
        <QualityBadge value={band.position} />
      </div>
      <div className="relative mt-5 h-10">
        <div className="absolute left-0 right-0 top-4 h-2 rounded-full bg-slate-800" />
        <div className="absolute top-3 h-4 rounded-full bg-emerald-300/25" style={{ left: pct(band.percentile25), right: `calc(100% - ${pct(band.percentile75)})` }} />
        <span className="absolute top-0 h-10 w-0.5 bg-slate-300" style={{ left: pct(band.medianValue) }} title="Median" />
        <span className="absolute top-1 h-8 w-8 -translate-x-1/2 rounded-full border-2 border-amber-200 bg-amber-300 shadow-lg shadow-amber-950/30" style={{ left: pct(band.currentValue) }} title="Current value" />
      </div>
      <dl className="mt-3 grid gap-2 text-xs text-slate-400 sm:grid-cols-4">
        <div><dt>25th</dt><dd className="font-medium text-slate-200">{number(band.percentile25)}</dd></div>
        <div><dt>Median</dt><dd className="font-medium text-slate-200">{number(band.medianValue)}</dd></div>
        <div><dt>75th</dt><dd className="font-medium text-slate-200">{number(band.percentile75)}</dd></div>
        <div><dt>Current</dt><dd className="font-medium text-slate-200">{number(band.currentValue)}</dd></div>
      </dl>
    </div>
  )
}

const factorLabels: Record<string, string> = {
  positiveNetIncome: 'Positive net income',
  positiveOperatingCashFlow: 'Positive operating cash flow',
  improvingRoa: 'Improving ROA',
  cashFlowQuality: 'Cash flow quality',
  lowerLeverage: 'Lower leverage',
  improvingCurrentRatio: 'Improving current ratio',
  noShareDilution: 'No share dilution',
  improvingGrossMargin: 'Improving gross margin',
  improvingAssetTurnover: 'Improving asset turnover',
}

function RiskIntelligence({ review, currency }: { review: Review; currency: string }): JSX.Element {
  const piotroski = review.piotroski
  const altman = review.altman
  const cyclicality = review.cyclicality
  const earningsQuality = review.earningsQuality

  return (
    <div className="grid gap-5 lg:grid-cols-2">
      <Panel title="Piotroski F-Score">
        {piotroski ? (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div><p className="text-3xl font-semibold text-white">{piotroski.totalScore}<span className="text-base text-slate-400"> / 9</span></p><p className="mt-1 text-sm text-slate-400">Strong &gt;= 7, moderate 4-6, weak &lt;= 3.</p></div>
              <StatusPill value={piotroski.availabilityStatus} />
            </div>
            <div className="grid gap-2 sm:grid-cols-2">
              {Object.entries(piotroski.factors).map(([key, passed]) => (
                <div key={key} className="flex items-center justify-between rounded-lg border border-slate-800 bg-slate-950/50 px-3 py-2 text-sm">
                  <span className="text-slate-300">{factorLabels[key] || key}</span>
                  <span className={passed ? 'font-semibold text-emerald-200' : 'font-semibold text-rose-200'}>{passed ? 'Pass' : 'Fail'}</span>
                </div>
              ))}
            </div>
            <p className="text-xs leading-5 text-slate-500">{piotroski.availabilityMessage || `Result date: ${date(piotroski.resultDate)}`}</p>
          </div>
        ) : <DataGap>Piotroski result is unavailable. Seed or recompute scoring data for this symbol.</DataGap>}
      </Panel>
      <Panel title="Altman Z-Score">
        {altman ? (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3"><Metric label="Z-score" value={number(altman.score)} /><StatusPill value={altman.zone} /></div>
            <dl className="grid gap-3 sm:grid-cols-2">
              <Metric label="Formula variant" value={altman.formulaVariant?.replace(/_/g, ' ') || 'Unavailable'} />
              <Metric label="Working capital / assets" value={ratioPercent(altman.workingCapitalToAssets)} />
              <Metric label="Retained earnings / assets" value={ratioPercent(altman.retainedEarningsToAssets)} />
              <Metric label="EBIT / assets" value={ratioPercent(altman.ebitToAssets)} />
              <Metric label="Market equity / liabilities" value={ratioPercent(altman.marketValueEquityToLiabilities)} />
              <Metric label="Sales / assets" value={ratioPercent(altman.salesToAssets)} />
            </dl>
            <p className="text-xs leading-5 text-slate-500">{altman.availabilityMessage || `Result date: ${date(altman.resultDate)}`}</p>
          </div>
        ) : <DataGap>Altman Z-Score is unavailable. Distress-zone classification cannot be shown from the current local data.</DataGap>}
      </Panel>
      <Panel title="Cyclicality">
        {cyclicality ? (
          <dl className="grid gap-3 sm:grid-cols-2">
            <Metric label="Classification" value={cyclicality.classification?.replace(/_/g, ' ') || 'Unavailable'} />
            <Metric label="Years analyzed" value={cyclicality.yearsAnalyzed ? String(cyclicality.yearsAnalyzed) : 'Unavailable'} />
            <Metric label="Revenue volatility" value={ratioPercent(cyclicality.revenueCoefficient)} />
            <Metric label="Earnings volatility" value={ratioPercent(cyclicality.earningsCoefficient)} />
            <Metric label="Normalized earnings" value={money(cyclicality.normalizedEarnings, currency)} />
            <Metric label="Cycle-adjusted P/E" value={number(cyclicality.cycleAdjustedPe)} />
          </dl>
        ) : <DataGap>Cyclicality assessment is unavailable. The current review cannot distinguish stable versus cycle-sensitive earnings.</DataGap>}
      </Panel>
      <Panel title="Earnings Quality">
        {earningsQuality ? (
          <div className="space-y-4">
            <dl className="grid gap-3 sm:grid-cols-2">
              <Metric label="Classification" value={earningsQuality.classification?.replace(/_/g, ' ') || 'Unavailable'} />
              <Metric label="FCF / net income" value={ratioPercent(earningsQuality.fcfToNetIncome)} />
              <Metric label="Sloan accruals ratio" value={ratioPercent(earningsQuality.sloanAccrualsRatio)} />
              <Metric label="Years analyzed" value={earningsQuality.yearsAnalyzed ? String(earningsQuality.yearsAnalyzed) : 'Unavailable'} />
            </dl>
            {earningsQuality.deteriorating && <p className="rounded-lg border border-amber-300/30 bg-amber-300/10 p-3 text-sm leading-6 text-amber-100">Earnings quality is deteriorating: accruals are rising while cash conversion is weakening.</p>}
          </div>
        ) : <DataGap>Earnings-quality metrics are unavailable. Cash conversion and accruals cannot be classified from the current local data.</DataGap>}
      </Panel>
    </div>
  )
}

function BusinessQuality({ review, annual, ratioData }: { review: Review; annual: Array<Annual & { label: string }>; ratioData: Array<RatioItem & { label: string }> }): JSX.Element {
  const moat = review.moat
  const capital = review.capitalAllocation
  const bands = review.valuationBands?.bands || []
  const bandByMetric = (name: string) => bands.find((band) => band.metric.toUpperCase().replace(/[^A-Z0-9]/g, '').includes(name))
  const peBand = bandByMetric('PE')
  const evEbitdaBand = bandByMetric('EVEBITDA')
  const stability = moat?.stabilityCriteria || []
  const stabilityPassed = stability.filter((criterion) => criterion.status === 'PASS').length
  const roicChart = ratioData.map((item) => ({
    label: item.label,
    roic: percentFromRatio(item.roic),
    wacc: percentFromRatio(moat?.estimatedWacc),
  }))
  const firstShares = annual.find((item) => typeof item.sharesOutstanding === 'number' && item.sharesOutstanding > 0)?.sharesOutstanding
  const sharesData = firstShares
    ? annual.map((item) => ({
      label: item.label,
      sharesIndex: item.sharesOutstanding == null ? null : (item.sharesOutstanding / firstShares) * 100,
    }))
    : []
  const moatText = moat?.moatStrength && moat?.yearsRoicAboveWacc != null && moat?.yearsAnalyzed
    ? `${moat.moatStrength.replace(/_/g, ' ').toLowerCase()} moat: ROIC exceeded estimated cost of capital in ${moat.yearsRoicAboveWacc} of ${moat.yearsAnalyzed} years with a ${moat.roicTrend?.replace(/_/g, ' ').toLowerCase() || 'unavailable'} trend.`
    : 'Moat classification is unavailable because the current local history is incomplete.'

  return (
    <div className="space-y-5">
      <div className="grid gap-5 xl:grid-cols-2">
        <Panel title="Moat assessment">
          {moat ? (
            <div className="space-y-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <QualityBadge value={moat.moatStrength} />
                  <p className="mt-3 text-sm leading-6 text-slate-300">{moatText}</p>
                </div>
                <QualityBadge value={moat.roicTrend} />
              </div>
              <dl className="grid gap-3 sm:grid-cols-2">
                <Metric label="ROIC consistency" value={percentPoint(moat.roicConsistencyPercentage)} />
                <Metric label="Average ROIC spread" value={ratioPercent(moat.averageRoicSpread)} />
                <Metric label="Reinvestment rate" value={ratioPercent(moat.reinvestmentRate)} />
                <Metric label="Years analyzed" value={moat.yearsAnalyzed == null ? 'Unavailable' : String(moat.yearsAnalyzed)} />
              </dl>
              <PercentTrendChart data={roicChart} lines={[['roic', 'ROIC'], ['wacc', 'Estimated WACC']]} summary="ROIC history from stored ratios compared with the MA1 estimated cost of capital." />
              {moat.availabilityMessage && <p className="text-xs leading-5 text-slate-500">{moat.availabilityMessage}</p>}
            </div>
          ) : <DataGap>Moat assessment is unavailable. Recompute or seed MA1 business-quality data for this symbol.</DataGap>}
        </Panel>

        <Panel title="Capital allocation">
          {capital ? (
            <div className="space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <QualityBadge value={capital.classification} />
                <QualityBadge value={capital.sharesOutstandingTrend} />
              </div>
              <dl className="grid gap-3 sm:grid-cols-2">
                <Metric label="Shares change" value={percentPoint(capital.sharesChangePercentage)} />
                <Metric label="Shares CAGR" value={ratioPercent(capital.sharesCagr)} />
                <Metric label="Total shareholder yield" value={ratioPercent(capital.totalShareholderYield)} />
                <Metric label="Insider ownership" value={capital.insiderOwnershipPercentage == null ? 'Unavailable' : percentPoint(capital.insiderOwnershipPercentage)} note={capital.insiderOwnershipPercentage == null ? 'Provider data did not supply insider ownership.' : undefined} />
              </dl>
              <Chart data={sharesData} lines={[['sharesIndex', 'Shares outstanding index']]} summary="Shares outstanding normalized to 100 in the first available annual period. Rising values indicate dilution; falling values indicate net buybacks." />
              {capital.availabilityMessage && <p className="text-xs leading-5 text-slate-500">{capital.availabilityMessage}</p>}
            </div>
          ) : <DataGap>Capital allocation assessment is unavailable. Shares trend and allocator classification cannot be shown from current local data.</DataGap>}
        </Panel>
      </div>

      <div className="grid gap-5 xl:grid-cols-2">
        <Panel title="Historical valuation bands">
          <div className="space-y-4">
            {peBand ? <ValuationBandMiniChart band={peBand} /> : <DataGap>P/E historical valuation band is unavailable.</DataGap>}
            {evEbitdaBand ? <ValuationBandMiniChart band={evEbitdaBand} /> : <DataGap>EV/EBITDA historical valuation band is unavailable.</DataGap>}
          </div>
        </Panel>

        <Panel title="Graham stability scorecard">
          {stability.length ? (
            <div className="space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="text-2xl font-semibold text-white">{stabilityPassed}<span className="text-base text-slate-400"> of {stability.length}</span></p>
                <a className="text-sm font-semibold text-emerald-300 underline" href="#valuation">Graham checklist</a>
              </div>
              <div className="space-y-2">
                {stability.map((criterion) => (
                  <div key={criterion.criterionCode} className="flex flex-col justify-between gap-2 rounded-lg border border-slate-800 bg-slate-950/50 px-3 py-2 text-sm sm:flex-row sm:items-center">
                    <div>
                      <p className="font-medium text-slate-200">{criterion.label}</p>
                      <p className="mt-1 text-xs text-slate-500">{criterion.message || `Actual value: ${number(criterion.actualValue)}`}</p>
                    </div>
                    <QualityBadge value={criterion.status} />
                  </div>
                ))}
              </div>
            </div>
          ) : <DataGap>Stability criteria are unavailable. The current review cannot show individual Graham stability pass/fail evidence.</DataGap>}
        </Panel>
      </div>
    </div>
  )
}

type HistoryWindow = '3y' | '5y' | '10y' | 'max'
const historyWindowSize: Record<HistoryWindow, number | null> = { '3y': 3, '5y': 5, '10y': 10, max: null }

function numericSeries(data: Array<Record<string, number | string | null>>, key: string): number[] {
  return data.map((item) => item[key]).filter((value): value is number => typeof value === 'number' && Number.isFinite(value))
}

function textOnlySeries(data: Array<Record<string, number | string | null>>, lines: Array<[string, string]>): JSX.Element {
  const latest = [...data].reverse().find((item) => lines.some(([key]) => typeof item[key] === 'number'))
  return (
    <div className="space-y-3">
      <DataGap>Historical depth is unavailable or repeated for this metric, so the current value is shown without a chart.</DataGap>
      <dl className="grid gap-3 sm:grid-cols-2">
        {lines.map(([key, label]) => <Metric key={key} label={label} value={number(latest?.[key] as number | null | undefined)} />)}
      </dl>
    </div>
  )
}

function Chart({ data, lines, bar = false, summary }: { data: Array<Record<string, number | string | null>>; lines: Array<[string, string]>; bar?: boolean; summary: string }): JSX.Element {
  const [window, setWindow] = useState<HistoryWindow>('10y')
  if (!data.length) return <DataGap>No historical series is available for this chart.</DataGap>
  const size = historyWindowSize[window]
  const visibleData = size == null || data.length <= size ? data : data.slice(-size)
  const candidateData = visibleData.length >= 2 ? visibleData : data
  const series = lines.map(([key]) => numericSeries(candidateData, key))
  const populatedSeries = series.filter((values) => values.length >= 2)
  if (!populatedSeries.length) return textOnlySeries(data, lines)
  const allPopulatedSeriesAreFlat = populatedSeries.every((values) => new Set(values.map((value) => value.toFixed(6))).size <= 1)
  if (allPopulatedSeriesAreFlat) return textOnlySeries(data, lines)
  const Component = bar ? BarChart : LineChart
  return (
    <div>
      <div className="mb-3 flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
        <p className="text-sm leading-6 text-slate-400">{summary}</p>
        {data.length > 3 && (
          <div className="inline-flex w-fit rounded-lg border border-slate-700 bg-slate-950 p-1">
            {(['3y', '5y', '10y', 'max'] as HistoryWindow[]).map((option) => (
              <button key={option} type="button" onClick={() => setWindow(option)} className={`rounded-md px-2.5 py-1 text-xs font-semibold ${window === option ? 'bg-emerald-400 text-slate-950' : 'text-slate-300 hover:bg-slate-800'}`}>{option}</button>
            ))}
          </div>
        )}
      </div>
      <div className="min-h-72 h-72 min-w-0 w-full" role="img" aria-label={summary}>
        <ResponsiveContainer width="100%" height="100%">
          <Component data={candidateData}>
            <CartesianGrid stroke="#334155" strokeDasharray="3 3" />
            <XAxis dataKey="label" stroke="#94a3b8" />
            <YAxis stroke="#94a3b8" tickFormatter={(v) => compact(Number(v))} width={78} />
            <Tooltip formatter={(v) => compact(Number(v))} contentStyle={{ background: '#0f172a', border: '1px solid #334155', color: '#e2e8f0' }} />
            <Legend />
            {lines.map(([key, label], index) => bar
              ? <Bar key={key} dataKey={key} name={label} fill={['#34d399', '#60a5fa', '#fbbf24', '#f472b6'][index]} />
              : <Line key={key} type="monotone" dataKey={key} name={label} stroke={['#34d399', '#60a5fa', '#fbbf24', '#f472b6'][index]} strokeWidth={2} dot={false} connectNulls />
            )}
          </Component>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

function CagrTable({ growth }: { growth?: Growth }): JSX.Element {
  const rows: Array<[string, Metrics | undefined]> = [['Revenue', growth?.revenue], ['Free cash flow', growth?.fcf], ['EPS', growth?.eps]]
  return <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead className="border-b border-slate-700 text-slate-400"><tr><th className="pb-3">Metric</th><th>3 years</th><th>5 years</th><th>10 years</th></tr></thead><tbody>{rows.map(([label, values]) => <tr key={label} className="border-b border-slate-800"><th className="py-3 font-medium text-white">{label}</th><td>{percentPoint(values?.cagr3y)}</td><td>{percentPoint(values?.cagr5y)}</td><td>{percentPoint(values?.cagr10y)}</td></tr>)}</tbody></table></div>
}

function CoverageGrid({ coverage, freshness }: { coverage: SourceCoverageItem[]; freshness: FreshnessItem[] }): JSX.Element {
  const freshnessByCategory = new Map(freshness.map((item) => [item.category, item]))
  return <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">{coverage.map((item) => {
    const fresh = freshnessByCategory.get(item.category)
    const available = item.status === 'AVAILABLE'
    return (
      <div key={item.category} className="rounded-lg border border-slate-800 bg-slate-950/50 p-3">
        <span className="block text-xs uppercase tracking-wide text-slate-500">{item.category}</span>
        <span className={`mt-1 inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${available ? 'bg-emerald-300/15 text-emerald-100' : 'bg-slate-700 text-slate-200'}`}>{item.provider || item.status.toLowerCase()}</span>
        {fresh && <p className="mt-2 text-xs leading-5 text-slate-500">{fresh.status.toLowerCase()} · {date(fresh.dataAsOf)}</p>}
        {item.message && <p className="mt-2 text-xs leading-5 text-slate-500">{item.message}</p>}
      </div>
    )
  })}</div>
}

const maxHoldingQuantity = 1_000_000_000

function prospectiveWarnings(detail: Awaited<ReturnType<typeof portfolioApi.detail>> | undefined, symbol: string, quantity: number, currentPrice: number | null, sector: string | null): string[] {
  if (!detail || currentPrice == null || !Number.isFinite(quantity) || quantity <= 0) return []
  const addedValue = currentPrice * quantity
  const existingTotal = detail.totalValue ?? detail.holdings.reduce((sum, holding) => sum + (holding.currentValue ?? 0), 0)
  const nextTotal = existingTotal + addedValue
  if (nextTotal <= 0) return []
  const existingSameSymbol = detail.holdings.find((holding) => holding.symbol.toUpperCase() === symbol)?.currentValue ?? 0
  const symbolWeight = ((existingSameSymbol + addedValue) / nextTotal) * 100
  const sectorWeight = sector ? (detail.holdings.filter((holding) => holding.sector === sector).reduce((sum, holding) => sum + (holding.currentValue ?? 0), 0) + addedValue) / nextTotal * 100 : null
  const warnings: string[] = []
  if (symbolWeight > 20) warnings.push(`${symbol} would represent ${symbolWeight.toFixed(1)}% of this model portfolio after adding it.`)
  if (sector && sectorWeight != null && sectorWeight > 35) warnings.push(`${sector} exposure would reach ${sectorWeight.toFixed(1)}% after adding this holding.`)
  if (detail.holdings.some((holding) => holding.currentValue == null)) warnings.push('Some existing holdings are missing prices, so concentration is only partially calculated.')
  return warnings
}

function AddToPortfolio({ symbol, currentPrice, sector }: { symbol: string; currentPrice: number | null; sector: string | null }): JSX.Element {
  const queryClient = useQueryClient()
  const [selectedPortfolioId, setSelectedPortfolioId] = useState('')
  const [quantity, setQuantity] = useState('1')
  const [validation, setValidation] = useState<string | null>(null)
  const [addedPortfolioId, setAddedPortfolioId] = useState<string | null>(null)
  const portfolios = useQuery({ queryKey: ['portfolios'], queryFn: portfolioApi.list })
  const activePortfolioId = selectedPortfolioId || portfolios.data?.[0]?.id || ''
  const activePortfolio = portfolios.data?.find((portfolio) => portfolio.id === activePortfolioId)
  const portfolioDetail = useQuery({
    queryKey: ['portfolio', activePortfolioId],
    queryFn: () => portfolioApi.detail(activePortfolioId),
    enabled: Boolean(activePortfolioId),
  })
  const existingHolding = portfolioDetail.data?.holdings.find((holding) => holding.symbol.toUpperCase() === symbol)
  const alreadyAddedHere = Boolean(existingHolding) || addedPortfolioId === activePortfolioId
  const parsedQuantity = Number(quantity)
  const quantityIsValid = Number.isInteger(parsedQuantity) && parsedQuantity > 0 && parsedQuantity <= maxHoldingQuantity
  const warnings = prospectiveWarnings(portfolioDetail.data, symbol, parsedQuantity, currentPrice, sector)
  const addHolding = useMutation({
    mutationFn: () => portfolioApi.addHolding(activePortfolioId, { symbol, quantity: parsedQuantity }),
    onSuccess: () => {
      setValidation(null)
      setAddedPortfolioId(activePortfolioId)
      void queryClient.invalidateQueries({ queryKey: ['portfolios'] })
      void queryClient.invalidateQueries({ queryKey: ['portfolio', activePortfolioId] })
    },
  })

  useEffect(() => {
    if (!selectedPortfolioId && portfolios.data?.[0]?.id) setSelectedPortfolioId(portfolios.data[0].id)
  }, [portfolios.data, selectedPortfolioId])

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (!activePortfolioId) {
      setValidation('Choose a portfolio before adding this holding.')
      return
    }
    if (!quantityIsValid) {
      setValidation(`Quantity must be a whole number from 1 to ${maxHoldingQuantity.toLocaleString()}.`)
      return
    }
    if (existingHolding) {
      setValidation(`${symbol} is already in ${activePortfolio?.name || 'this portfolio'}. Open the portfolio to change the existing holding.`)
      return
    }
    setValidation(null)
    addHolding.mutate()
  }

  if (portfolios.isLoading) {
    return (
      <div className="rounded-lg border border-slate-800 bg-slate-950/50 p-4">
        <p className="text-sm text-slate-400">Loading your portfolios...</p>
      </div>
    )
  }

  if (portfolios.isError) {
    return (
      <div className="rounded-lg border border-rose-300/30 bg-rose-400/10 p-4">
        <p role="alert" className="text-sm text-rose-100">{portfolios.error instanceof Error ? portfolios.error.message : 'Portfolios could not be loaded.'}</p>
        <button type="button" onClick={() => void portfolios.refetch()} className="mt-3 rounded-lg border border-rose-200/40 px-3 py-2 text-sm font-semibold text-rose-100 hover:bg-rose-200/10">Try again</button>
      </div>
    )
  }

  if (!portfolios.data?.length) {
    return (
      <div className="rounded-lg border border-slate-800 bg-slate-950/50 p-4">
        <p className="text-sm leading-6 text-slate-300">Create a portfolio before adding reviewed symbols to a model portfolio.</p>
        <Link to="/portfolio" className="mt-4 inline-flex rounded-lg bg-emerald-400 px-4 py-2 text-sm font-semibold text-slate-950 hover:bg-emerald-300">Create portfolio</Link>
        <p className="mt-3 text-xs leading-5 text-slate-500">Securities are shared research data; portfolios remain user-owned.</p>
      </div>
    )
  }

  return (
    <form onSubmit={submit} className="rounded-lg border border-slate-800 bg-slate-950/50 p-4">
      <p className="text-sm leading-6 text-slate-300">Add {symbol} to one of your model portfolios. This records a holding only; it does not place a trade.</p>
      <div className="mt-4 grid gap-3 sm:grid-cols-[minmax(0,1fr)_8rem]">
        <label className="text-sm font-medium text-slate-200">
          Portfolio
          <select
            value={activePortfolioId}
            onChange={(event) => {
              setSelectedPortfolioId(event.target.value)
              setValidation(null)
              setAddedPortfolioId(null)
              addHolding.reset()
            }}
            className="mt-1 block w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white"
          >
            {portfolios.data.map((portfolio: Portfolio) => (
              <option key={portfolio.id} value={portfolio.id}>{portfolio.name} ({portfolio.holdingCount} holdings)</option>
            ))}
          </select>
        </label>
        <label className="text-sm font-medium text-slate-200">
          Quantity
          <input
            required
            type="number"
            min="1"
            max={maxHoldingQuantity}
            step="1"
            value={quantity}
            onChange={(event) => {
              setQuantity(event.target.value)
              setValidation(null)
              addHolding.reset()
            }}
            className="mt-1 block w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white"
          />
        </label>
      </div>
      {portfolioDetail.isLoading && <p className="mt-3 text-xs text-slate-500">Checking existing holdings...</p>}
      {portfolioDetail.isError && (
        <p role="alert" className="mt-3 rounded-lg border border-rose-300/30 bg-rose-400/10 p-3 text-sm leading-6 text-rose-100">
          Portfolio holdings could not be checked. Try again before adding this symbol.
        </p>
      )}
      {existingHolding && (
        <p role="status" className="mt-3 rounded-lg border border-amber-300/20 bg-amber-300/5 p-3 text-sm leading-6 text-amber-100">
          {symbol} is already in {activePortfolio?.name || 'this portfolio'} with quantity {existingHolding.quantity}. Open the portfolio to edit the existing holding.
        </p>
      )}
      {warnings.map((warning) => <p key={warning} role="status" className="mt-3 rounded-lg border border-amber-300/20 bg-amber-300/5 p-3 text-sm leading-6 text-amber-100">{warning}</p>)}
      <div className="mt-4 flex flex-wrap items-center gap-3">
        <button
          disabled={addHolding.isPending || portfolioDetail.isFetching || portfolioDetail.isError || !quantityIsValid || alreadyAddedHere}
          className="rounded-lg bg-emerald-400 px-4 py-2 text-sm font-semibold text-slate-950 hover:bg-emerald-300 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {addHolding.isPending ? 'Adding...' : 'Add to portfolio'}
        </button>
        <Link to="/portfolio" className="rounded-lg border border-slate-700 px-4 py-2 text-sm font-semibold text-slate-200 hover:bg-slate-800">Open Portfolio</Link>
      </div>
      {validation && <p role="alert" className="mt-3 text-sm text-rose-200">{validation}</p>}
      {addHolding.isSuccess && addedPortfolioId === activePortfolioId && !existingHolding && <p role="status" className="mt-3 text-sm text-emerald-200">Added {symbol} to {activePortfolio?.name || 'your portfolio'}.</p>}
      {addHolding.isError && <p role="alert" className="mt-3 text-sm text-rose-200">{addHolding.error instanceof Error ? addHolding.error.message : 'Could not add this holding.'}</p>}
      <p className="mt-3 text-xs leading-5 text-slate-500">Fair value, margin of safety, and portfolio context are decision-support outputs, not investment advice.</p>
    </form>
  )
}

function ReviewProgress({ progress }: { progress: number }): JSX.Element {
  return (
    <div className="h-1 rounded-full bg-slate-800" aria-hidden="true">
      <div className="h-full rounded-full bg-emerald-400 transition-[width] duration-150" style={{ width: `${progress}%` }} />
    </div>
  )
}

function CustomDcf({ symbol, currency }: { symbol: string; currency: string }): JSX.Element {
  const [form, setForm] = useState({ wacc: '0.10', growthY1Y5: '0.06', growthY6Y10: '0.04', terminalRate: '0.025' })
  const custom = useMutation({
    mutationFn: () => json<{ dcfFairValue?: number }>(`/api/v1/securities/${encodeURIComponent(symbol)}/valuation/dcf`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...Object.fromEntries(Object.entries(form).map(([key, value]) => [key, Number(value)])), requiredReturn: null, dividendGrowthRate: null }),
    }),
  })
  return (
    <form onSubmit={(event: FormEvent) => { event.preventDefault(); custom.mutate() }} className="grid gap-3 sm:grid-cols-2">
      {Object.entries(form).map(([key, value]) => <label key={key} className="text-sm text-slate-300">{key === 'growthY1Y5' ? 'Growth years 1-5' : key === 'growthY6Y10' ? 'Growth years 6-10' : key === 'terminalRate' ? 'Terminal rate' : 'WACC'}<input required type="number" step="0.001" value={value} onChange={(event) => setForm((current) => ({ ...current, [key]: event.target.value }))} className="mt-1 block w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" /></label>)}
      <button className="rounded-lg bg-emerald-400 px-4 py-2 font-semibold text-slate-950 sm:col-span-2 disabled:opacity-60" disabled={custom.isPending}>{custom.isPending ? 'Calculating...' : 'Run custom DCF'}</button>
      {custom.isError && <p role="alert" className="sm:col-span-2 text-sm text-rose-200">{custom.error instanceof Error ? custom.error.message : 'Custom valuation could not be calculated.'}</p>}
      {custom.data && custom.data.dcfFairValue != null && <p className="sm:col-span-2 text-sm text-emerald-200">Custom valuation calculated. Fair value: {money(custom.data.dcfFairValue, currency)}.</p>}
      {custom.data && custom.data.dcfFairValue == null && <p role="status" className="sm:col-span-2 rounded-lg border border-amber-300/20 bg-amber-300/5 p-3 text-sm leading-6 text-amber-100">DCF is unavailable for this symbol with the current data. The backend fell back to eligible valuation models.</p>}
    </form>
  )
}

function SensitivityTable({ valuation, currentPrice, currency }: { valuation: Valuation | null; currentPrice: number | null; currency: string }): JSX.Element {
  const sensitivity = valuation?.sensitivity
  if (!sensitivity || !sensitivity.cells.length) return <DataGap>DCF sensitivity is unavailable until the backend has enough positive free-cash-flow history and WACC inputs for this symbol.</DataGap>
  const cellByKey = new Map(sensitivity.cells.map((cell) => [`${cell.wacc}:${cell.terminalRate}`, cell]))
  const mosClass = (fairValue: number | null | undefined) => {
    if (fairValue == null || currentPrice == null || currentPrice === 0) return 'bg-slate-950 text-slate-500'
    const mos = ((fairValue - currentPrice) / currentPrice) * 100
    if (mos >= 15) return 'bg-emerald-400/15 text-emerald-100'
    if (mos >= 0) return 'bg-amber-300/15 text-amber-100'
    return 'bg-rose-400/15 text-rose-100'
  }
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[34rem] border-separate border-spacing-1 text-center text-sm">
        <caption className="mb-3 text-left text-sm leading-6 text-slate-400">Fair value per share across WACC and terminal growth assumptions. Green cells imply at least 15% margin of safety versus current price.</caption>
        <thead>
          <tr>
            <th className="rounded-md bg-slate-950 p-2 text-left text-slate-400">WACC / terminal</th>
            {sensitivity.terminalRateValues.map((terminalRate) => <th key={terminalRate} className="rounded-md bg-slate-950 p-2 text-slate-300">{ratioPercent(terminalRate)}</th>)}
          </tr>
        </thead>
        <tbody>
          {sensitivity.waccValues.map((wacc) => (
            <tr key={wacc}>
              <th className="rounded-md bg-slate-950 p-2 text-left font-medium text-slate-300">{ratioPercent(wacc)}</th>
              {sensitivity.terminalRateValues.map((terminalRate) => {
                const cell = cellByKey.get(`${wacc}:${terminalRate}`)
                const isBase = wacc === sensitivity.baseWacc && terminalRate === sensitivity.baseTerminalRate
                return (
                  <td key={`${wacc}-${terminalRate}`} className={`rounded-md p-2 ${mosClass(cell?.fairValue)} ${isBase ? 'outline outline-2 outline-emerald-300' : ''}`}>
                    <span className="block font-semibold">{money(cell?.fairValue, currency)}</span>
                    <span className="text-xs opacity-80">TV {percentPoint(cell?.terminalValuePercentage)}</span>
                  </td>
                )
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function WaccPanel({ wacc }: { wacc: WaccDetail | null | undefined }): JSX.Element {
  if (!wacc) return <DataGap>WACC inputs are unavailable for this valuation. Re-run valuation after VM1 data is present.</DataGap>
  return (
    <dl className="grid gap-3 sm:grid-cols-2">
      <Metric label="Computed WACC" value={ratioPercent(wacc.wacc)} note={wacc.fallbackUsed ? `Fallback source: ${wacc.source || 'configured fallback'}` : `Source: ${wacc.source || 'stored inputs'}`} />
      <Metric label="Risk-free rate" value={ratioPercent(wacc.riskFreeRate)} />
      <Metric label="Equity risk premium" value={ratioPercent(wacc.equityRiskPremium)} />
      <Metric label="Beta" value={number(wacc.beta)} />
      <Metric label="Cost of equity" value={ratioPercent(wacc.costOfEquity)} />
      <Metric label="Cost of debt" value={ratioPercent(wacc.costOfDebt)} />
      <Metric label="Debt weight" value={ratioPercent(wacc.debtWeight)} />
      <Metric label="Equity weight" value={ratioPercent(wacc.equityWeight)} />
      <Metric label="Effective tax rate" value={ratioPercent(wacc.effectiveTaxRate)} />
    </dl>
  )
}

function GrahamChecklistPanel({ checklist }: { checklist: GrahamChecklist | null | undefined }): JSX.Element {
  if (!checklist) return <DataGap>Graham checklist results are unavailable for this valuation.</DataGap>
  const statusClass = (status: string) => status === 'PASS' ? 'text-emerald-200' : status === 'FAIL' ? 'text-rose-200' : 'text-slate-400'
  return (
    <div>
      <p className="mb-4 text-sm text-slate-300">{checklist.passed} of {checklist.criteria.length} criteria met. {checklist.insufficient} criteria lack enough data.</p>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-700 text-slate-400"><tr><th className="pb-3">Criterion</th><th>Status</th><th>Actual</th></tr></thead>
          <tbody>{checklist.criteria.map((item) => <tr key={item.code} className="border-b border-slate-800"><td className="py-3 text-slate-200">{item.label}</td><td className={statusClass(item.status)}>{item.status.replace(/_/g, ' ').toLowerCase()}</td><td>{number(item.actualValue)}</td></tr>)}</tbody>
        </table>
      </div>
    </div>
  )
}

function CompositeWeights({ valuation, currency }: { valuation: Valuation | null; currency: string }): JSX.Element {
  const [weights, setWeights] = useState({ dcf: 55, graham: 20, ddm: 10, epv: 15 })
  const entries: Array<[keyof typeof weights, string, number | null | undefined]> = [
    ['dcf', 'DCF', valuation?.dcf?.base],
    ['graham', 'Graham', valuation?.grahamNumber],
    ['ddm', 'DDM', valuation?.ddmValue],
    ['epv', 'EPV', valuation?.epv?.fairValue],
  ]
  const total = Object.values(weights).reduce((sum, value) => sum + value, 0)
  const adjustedTotal = total || 1
  const composite = entries.reduce((sum, [key, , value]) => value == null ? sum : sum + value * (weights[key] / adjustedTotal), 0)
  const update = (key: keyof typeof weights, value: number) => setWeights((current) => ({ ...current, [key]: value }))
  return (
    <div>
      <div className="grid gap-3">
        {entries.map(([key, label, value]) => (
          <label key={key} className="grid gap-2 text-sm text-slate-300">
            <span className="flex items-center justify-between gap-3"><span>{label}</span><span>{weights[key]}% · {money(value, currency)}</span></span>
            <input type="range" min="0" max="100" value={weights[key]} onChange={(event) => update(key, Number(event.target.value))} className="w-full accent-emerald-400" />
          </label>
        ))}
      </div>
      <dl className="mt-4 grid gap-3 sm:grid-cols-2">
        <Metric label="Weight total" value={`${total}%`} note={total === 100 ? 'Weights sum to 100.' : 'Preview normalizes the current weights.'} />
        <Metric label="Preview composite" value={money(composite || null, currency)} />
      </dl>
    </div>
  )
}

export function SecurityReviewPage(): JSX.Element {
  const { symbol: rawSymbol = '' } = useParams()
  const symbol = rawSymbol.trim().toUpperCase()
  const [scrollProgress, setScrollProgress] = useState(0)
  const review = useQuery({ queryKey: ['security-review', symbol], enabled: !!symbol, queryFn: () => json<Review>(`/api/v1/securities/${encodeURIComponent(symbol)}/review`), retry: false })
  const watchlist = useQuery({ queryKey: ['watchlist'], enabled: !!symbol, queryFn: watchlistApi.list })
  const confidence = useQuery({ queryKey: ['professional-confidence', symbol], enabled: !!symbol, queryFn: () => professionalApi.confidence(symbol), retry: false })
  const verification = useQuery({ queryKey: ['professional-verification', symbol], enabled: !!symbol, queryFn: () => professionalApi.verification(symbol), retry: false })
  const checklists = useQuery({ queryKey: ['checklists'], queryFn: professionalApi.checklists, retry: false })
  const competence = useQuery({ queryKey: ['competence-preferences'], queryFn: professionalApi.competence, retry: false })
  const availabilityDiagnostics = useQuery({ queryKey: ['availability-diagnostics'], queryFn: () => json<AvailabilityDiagnostic[]>('/api/v1/availability/diagnostics'), retry: false })
  const queryClient = useQueryClient()
  const addWatchlist = useMutation({
    mutationFn: () => watchlistApi.add(symbol, { mosAlertMin: null, mosAlertMax: null, fundamentalDegradeThreshold: null, monitoringReason: 'WAIT_FOR_BETTER_PRICE', rationaleNote: 'Added from the review page for continued monitoring.' }),
    onSuccess: (item) => {
      queryClient.setQueryData(['watchlist'], (current: Awaited<ReturnType<typeof watchlistApi.list>> | undefined) => {
        const items = current || []
        return items.some((existing) => existing.symbol.toUpperCase() === symbol) ? items : [...items, item]
      })
      void queryClient.invalidateQueries({ queryKey: ['watchlist'] })
    },
  })

  useEffect(() => {
    const updateProgress = () => {
      const scrollable = document.documentElement.scrollHeight - window.innerHeight
      setScrollProgress(scrollable <= 0 ? 0 : Math.min(100, Math.max(0, (window.scrollY / scrollable) * 100)))
    }
    updateProgress()
    window.addEventListener('scroll', updateProgress, { passive: true })
    window.addEventListener('resize', updateProgress)
    return () => {
      window.removeEventListener('scroll', updateProgress)
      window.removeEventListener('resize', updateProgress)
    }
  }, [])

  const currency = review.data?.detail.currency || 'USD'
  const annual = useMemo(() => [...(review.data?.financials.annuals || [])].sort((a, b) => a.fiscalYear - b.fiscalYear).map((item) => ({ label: String(item.fiscalYear), ...item })), [review.data])
  const ratioData = useMemo(() => [...(review.data?.ratios.ratios || [])].sort((a, b) => a.date.localeCompare(b.date)).map((item) => ({ label: item.date.slice(0, 4), ...item })), [review.data])
  const dividendData = useMemo(() => [...(review.data?.dividends.history || [])].reverse().map((item) => ({ label: (item.paymentDate || item.exDividendDate || '').slice(0, 7), amount: item.amount })), [review.data])

  if (!symbol) return <div className="mx-auto max-w-3xl"><DataGap>Choose a security from the screener to open an in-depth review.</DataGap></div>
  if (review.isLoading) return <div className="py-12 text-center text-slate-400">Loading the stock review packet...</div>
  if (!review.data) return <div className="mx-auto max-w-3xl"><DataGap>{review.error instanceof Error ? review.error.message : 'Security review data could not be loaded.'} Return to the <Link className="text-emerald-300 underline" to="/screener">screener</Link> and try again.</DataGap></div>

  const d = review.data.detail
  const financials = review.data.financials
  const valuation = review.data.valuation
  const dividends = review.data.dividends
  const growth = review.data.growth
  const peers = review.data.peers
  const score = review.data.score
  const health = review.data.financialHealth
  const watchlistItem = watchlist.data?.find((item) => item.symbol.toUpperCase() === symbol)
  const outsideCompetence = Boolean(d.sector && competence.data?.preferredSectors.length && !competence.data.preferredSectors.includes(d.sector))

  return (
    <div className="space-y-6">
      <section className="rounded-lg border border-slate-800 bg-slate-900/70 p-5 sm:p-7">
        <div className="flex flex-col justify-between gap-5 lg:flex-row lg:items-start">
          <div>
            <Link className="text-sm font-medium text-emerald-300 hover:text-emerald-200" to="/screener">Back to screener</Link>
            <p className="mt-5 text-xs font-semibold uppercase tracking-[.22em] text-emerald-300">In-depth stock review</p>
            <h1 className="mt-2 text-3xl font-semibold text-white sm:text-4xl">{d.companyName} <span className="text-slate-400">({d.symbol})</span></h1>
            <p className="mt-2 text-sm text-slate-400">{[d.sector, d.exchange, d.country, d.currency].filter(Boolean).join(' · ') || 'Profile context unavailable'}</p>
            {outsideCompetence && <p className="mt-3 inline-flex rounded-full bg-amber-300/15 px-3 py-1 text-xs font-semibold text-amber-100">Outside your marked competence sectors</p>}
            <p className="mt-4 max-w-3xl leading-7 text-slate-300">{d.description || 'Business description is unavailable from the current provider data.'}</p>
          </div>
          <div className="grid min-w-[16rem] gap-3 sm:grid-cols-2 lg:grid-cols-1">
            <Metric label="Current price" value={money(d.currentPrice, currency)} note={`Price date: ${date(d.priceDate)}`} />
            <Metric label="Fundamentals as of" value={date(d.dataAsOf)} />
          </div>
        </div>
        <div className="mt-5 flex flex-wrap gap-3">
          <button disabled={addWatchlist.isPending || Boolean(watchlistItem)} onClick={() => addWatchlist.mutate()} className="rounded-lg bg-emerald-400 px-4 py-2.5 text-sm font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-60">{watchlistItem ? 'Already on watchlist' : addWatchlist.isPending ? 'Adding...' : 'Add to watchlist'}</button>
          <Link to={`/securities/${symbol}`} className="rounded-lg border border-slate-700 px-4 py-2.5 text-sm font-semibold text-slate-200 hover:bg-slate-800">Open Security Detail</Link>
          <Link to="/admin/seed" className="rounded-lg border border-slate-700 px-4 py-2.5 text-sm font-semibold text-slate-200 hover:bg-slate-800">Refresh or seed</Link>
        </div>
        {watchlistItem && <p role="status" className="mt-3 text-sm text-slate-300">{symbol} is already on your watchlist.</p>}
        {addWatchlist.isSuccess && <p role="status" className="mt-3 text-sm text-emerald-200">Added to your watchlist.</p>}
        {addWatchlist.isError && <p role="alert" className="mt-3 text-sm text-rose-200">{addWatchlist.error instanceof Error ? addWatchlist.error.message : 'Could not add this security to your watchlist.'}</p>}
      </section>

      <nav aria-label="Review sections" className="sticky top-0 z-20 rounded-lg border border-slate-800 bg-slate-950/95 p-3 shadow-lg shadow-slate-950/30 backdrop-blur">
        <ReviewProgress progress={scrollProgress} />
        <div className="flex gap-2 overflow-x-auto">
          {[
            ['source', 'Sources'], ['valuation', 'Valuation'], ['cash', 'Cash generation'], ['earnings', 'Earnings'],
            ['professional', 'Workflow'], ['business-quality', 'Moat'], ['debt', 'Debt'], ['history', 'Graphs'], ['dividends', 'Dividends'], ['quality', 'Quality'], ['risk', 'Risk'],
          ].map(([id, label]) => <a key={id} href={`#${id}`} className="mt-3 whitespace-nowrap rounded-md px-3 py-2 text-sm font-medium text-slate-300 hover:bg-slate-800 hover:text-white">{label}</a>)}
        </div>
      </nav>

      <div className="rounded-lg border border-slate-800 bg-slate-900/40 p-5 sm:p-7">
        <Section id="source" title="Source Coverage And Freshness">
          <CoverageGrid coverage={review.data.sourceCoverage} freshness={review.data.freshness} />
          {review.data.availability.length > 0 && <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">{review.data.availability.map((item) => <div key={item.category} className="rounded-lg border border-slate-800 bg-slate-950/50 p-3"><span className="block text-xs uppercase tracking-wide text-slate-500">{item.category}</span><div className="mt-2"><AvailabilityBadge state={item.state} /></div><p className="mt-2 text-xs leading-5 text-slate-500">{item.state.reason}</p></div>)}</div>}
          <DataGap>Provider labels are shown where the backend has stored provider metadata. Otherwise the endpoint reports application data availability without inferring a provider.</DataGap>
          <AvailabilityDiagnosticsPanel diagnostics={availabilityDiagnostics.data} />
        </Section>

        <Section id="valuation" title="Valuation And Margin Of Safety" aside={<span className="rounded-full bg-amber-300/10 px-3 py-1 text-xs font-semibold text-amber-100">Decision support</span>}>
          <div className="grid gap-5 lg:grid-cols-2">
            <Panel title="Model outputs">
              <dl className="grid gap-3 sm:grid-cols-2">
                <Metric label="Market price" value={money(valuation?.currentPrice ?? d.currentPrice, currency)} />
                <Metric label="Composite fair value" value={money(valuation?.compositeFairValue, currency)} />
                <Metric label="Margin of safety" value={percentPoint(valuation?.marginOfSafety)} />
                <Metric label="Recommendation" value={valuation?.recommendation || 'Unavailable'} />
                <Metric label="DCF base" value={money(valuation?.dcf?.base, currency)} />
                <Metric label="DCF low / high" value={`${money(valuation?.dcf?.low, currency)} / ${money(valuation?.dcf?.high, currency)}`} />
                <Metric label="Terminal value share" value={percentPoint(valuation?.dcfTerminalValuePercentage)} />
                <Metric label="Graham number" value={money(valuation?.grahamNumber, currency)} />
                <Metric label="DDM" value={money(valuation?.ddmValue, currency)} />
                <Metric label="EPV floor" value={money(valuation?.epv?.fairValue, currency)} />
              </dl>
              {valuation?.dcfHighTerminalDependence && (
                <p role="status" className="mt-4 rounded-lg border border-amber-300/30 bg-amber-300/10 p-3 text-sm leading-6 text-amber-100">
                  This valuation depends heavily on long-term assumptions. Terminal value is {percentPoint(valuation.dcfTerminalValuePercentage)} of total DCF.
                </p>
              )}
              {valuation?.analystEstimates ? <p className="mt-4 text-sm leading-6 text-slate-400">Analyst target range: {money(valuation.analystEstimates.priceTargetLow, currency)} to {money(valuation.analystEstimates.priceTargetHigh, currency)}. Mean target {money(valuation.analystEstimates.priceTargetMean, currency)} from {valuation.analystEstimates.analystCount} estimates.</p> : <DataGap>Analyst target range is unavailable for this symbol.</DataGap>}
              <p className="mt-4 rounded-lg border border-amber-300/20 bg-amber-300/5 p-3 text-xs leading-5 text-amber-100">{valuation?.disclaimer || 'This is a decision-support tool, not investment advice (MiFID II).'}</p>
            </Panel>
            <Panel title="Custom DCF">
              <CustomDcf symbol={symbol} currency={currency} />
            </Panel>
            <Panel title="DCF sensitivity">
              <SensitivityTable valuation={valuation} currentPrice={valuation?.currentPrice ?? d.currentPrice} currency={currency} />
            </Panel>
            <Panel title="WACC transparency">
              <WaccPanel wacc={valuation?.wacc} />
            </Panel>
            <Panel title="Composite weight preview">
              <CompositeWeights valuation={valuation} currency={currency} />
            </Panel>
            <Panel title="Graham criteria checklist">
              <GrahamChecklistPanel checklist={valuation?.grahamChecklist} />
            </Panel>
          </div>
        </Section>

        <Section id="professional" title="Professional Workflow">
          <ProfessionalReviewPanel symbol={symbol} sector={d.sector} confidence={confidence.data} verification={verification.data} checklists={checklists.data} />
        </Section>

        <Section id="business-quality" title="Moat And Business Quality">
          <BusinessQuality review={review.data} annual={annual} ratioData={ratioData} />
        </Section>

        <Section id="cash" title="Cash Generation">
          <div className="grid gap-5 lg:grid-cols-2">
            <Panel title="Current cash profile">
              <dl className="grid gap-3 sm:grid-cols-2">
                <Metric label="TTM free cash flow" value={money(financials.ttm?.fcf ?? d.fcf, currency)} />
                <Metric label="TTM revenue" value={money(financials.ttm?.revenue ?? d.revenue, currency)} />
                <Metric label="FCF margin" value={financials.ttm?.fcf != null && financials.ttm.revenue ? ratioPercent(financials.ttm.fcf / financials.ttm.revenue) : 'Unavailable'} />
                <Metric label="Positive FCF years" value={annual.length ? `${annual.filter((item) => typeof item.fcf === 'number' && item.fcf > 0).length} of ${annual.length}` : 'Unavailable'} />
                <Metric label="Owner earnings" value={money(valuation?.ownerEarnings?.value, currency)} />
                <Metric label="Maintenance capex estimate" value={money(valuation?.ownerEarnings?.maintenanceCapexEstimate, currency)} />
                <Metric label="EPV normalized earnings" value={money(valuation?.epv?.normalizedEarnings, currency)} note={valuation?.epv?.yearsAveraged ? `${valuation.epv.yearsAveraged} years averaged` : undefined} />
              </dl>
            </Panel>
            <Panel title="FCF history">
              <Chart data={annual} lines={[['fcf', 'Free cash flow']]} bar summary="Annual free cash flow history from locally stored fundamentals." />
            </Panel>
          </div>
        </Section>

        <Section id="earnings" title="Earnings And Business Performance">
          <div className="grid gap-5 lg:grid-cols-2">
            <Panel title="Latest earnings evidence">
              <dl className="grid gap-3 sm:grid-cols-2">
                <Metric label="Revenue" value={money(d.revenue, currency)} />
                <Metric label="Net income" value={money(d.netIncome, currency)} />
                <Metric label="EPS" value={number(d.eps)} />
                <Metric label="Book value per share" value={number(d.bvps)} />
              </dl>
            </Panel>
            <Panel title="Earnings history">
              <Chart data={annual} lines={[['revenue', 'Revenue'], ['netIncome', 'Net income'], ['fcf', 'Free cash flow']]} bar summary="Annual revenue, net income, and free cash flow history." />
            </Panel>
          </div>
        </Section>

        <Section id="debt" title="Balance Sheet And Debt">
          <div className="grid gap-5 lg:grid-cols-2">
            <Panel title="Available resilience indicators">
              <dl className="grid gap-3 sm:grid-cols-2">
                <Metric label="Total debt" value={money(health.totalDebt, currency)} />
                <Metric label="Cash" value={money(health.cash, currency)} />
                <Metric label="Net debt" value={money(health.netDebt, currency)} />
                <Metric label="Debt / equity" value={ratioPercent(health.debtToEquity)} />
                <Metric label="Current ratio" value={number(health.currentRatio)} />
                <Metric label="Quick ratio" value={number(health.quickRatio)} note={health.quickRatio == null ? 'Quick ratio is not stored for the latest local ratio snapshot.' : undefined} />
                <Metric label="Interest coverage" value={number(health.interestCoverage)} note={health.interestCoverage == null ? 'Interest coverage is not stored for the latest local ratio snapshot.' : undefined} />
              </dl>
              <InfoNote>Short-term debt and long-term debt breakdowns are not tracked separately in the current local model. Total debt, cash, net debt, liquidity, and coverage metrics are shown when stored locally.</InfoNote>
            </Panel>
            <Panel title="Debt trend">
              <Chart data={ratioData} lines={[['debtToEquity', 'Debt / equity'], ['currentRatio', 'Current ratio'], ['quickRatio', 'Quick ratio'], ['interestCoverage', 'Interest coverage']]} summary="Debt and resilience trend from the stored ratios endpoint." />
            </Panel>
          </div>
        </Section>

        <Section id="history" title="Historical Graphs">
          <div className="grid gap-5 xl:grid-cols-2">
            <Panel title="Earnings history"><Chart data={annual} lines={[['revenue', 'Revenue'], ['netIncome', 'Net income'], ['eps', 'EPS'], ['fcf', 'FCF']]} summary="Revenue, net income, EPS, and free cash flow over annual periods." /></Panel>
            <Panel title="Debt history"><Chart data={ratioData} lines={[['debtToEquity', 'Debt / equity'], ['currentRatio', 'Current ratio'], ['quickRatio', 'Quick ratio'], ['interestCoverage', 'Interest coverage']]} summary="Debt, liquidity, and interest coverage over time from stored ratio snapshots." /></Panel>
            <Panel title="ROIC history"><Chart data={ratioData} lines={[['roic', 'ROIC']]} summary="Return on invested capital over time, labelled as ROIC from the ratios endpoint." /></Panel>
            <Panel title="ROE history"><Chart data={ratioData} lines={[['roe', 'ROE']]} summary="Return on equity over time from the ratios endpoint." /></Panel>
          </div>
        </Section>

        <Section id="dividends" title="Dividends And Income Resilience">
          <div className="grid gap-5 lg:grid-cols-2">
            <Panel title="Dividend profile">
              <dl className="grid gap-3 sm:grid-cols-2">
                <Metric label="Dividend yield" value={ratioPercent(d.dividendYield)} />
                <Metric label="Dividend streak" value={`${dividends.streak} years`} />
                <Metric label="3-year CAGR" value={percentPoint(dividends.cagr3y)} />
                <Metric label="5-year CAGR" value={percentPoint(dividends.cagr5y)} />
                <Metric label="10-year CAGR" value={percentPoint(dividends.cagr10y)} />
                <Metric label="Payout ratio" value={ratioPercent(health.payoutRatio)} />
              </dl>
            </Panel>
            <Panel title="Dividend history">
              <Chart data={dividendData} lines={[['amount', 'Dividend per share']]} bar summary="Dividend-per-share history by payment or ex-dividend date." />
            </Panel>
          </div>
        </Section>

        <Section id="quality" title="Quality, Growth, And Peer Context">
          <div className="grid gap-5 lg:grid-cols-2">
            <Panel title="Quality and growth">
              <dl className="mb-5 grid gap-3 sm:grid-cols-2">
                <Metric label="ROIC" value={ratioPercent(d.roic)} />
                <Metric label="ROE" value={ratioPercent(ratioData.at(-1)?.roe as number | null | undefined)} />
                <Metric label="Gross margin" value={ratioPercent(health.grossMargin ?? (ratioData.at(-1)?.grossMargin as number | null | undefined))} />
                <Metric label="Operating margin" value={ratioPercent(health.operatingMargin)} />
                <Metric label="Net margin" value={ratioPercent(health.netMargin)} />
                <Metric label="Value score" value={number(score?.totalScore)} note={score?.availability ? score.availability.reason : score?.scoreDate ? `Score date: ${date(score.scoreDate)}` : undefined} />
                <Metric label="Raw score" value={number(score?.rawTotalScore)} note={score?.mosGateApplied ? 'Raw score before the MoS gate cap.' : undefined} />
                <Metric label="Weight profile" value={score?.weightProfile?.replace(/_/g, ' ') || 'Unavailable'} />
              </dl>
              {score?.mosGateApplied && <p className="mb-5 rounded-lg border border-amber-300/30 bg-amber-300/10 p-3 text-sm leading-6 text-amber-100">Score capped at 40 because this stock appears overvalued relative to composite fair value. Raw score: {number(score.rawTotalScore)}; capped score: {number(score.totalScore)}.</p>}
              <CagrTable growth={growth} />
            </Panel>
            <Panel title="Peer context">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead className="border-b border-slate-700 text-slate-400"><tr><th className="pb-3">Company</th><th>MoS</th><th>Score</th><th>ROIC</th></tr></thead>
                  <tbody>{peers.peers.length ? peers.peers.map((peer) => <tr key={peer.symbol} className="border-b border-slate-800"><td className="py-3"><Link className="font-medium text-emerald-300" to={`/securities/${peer.symbol}/review`}>{peer.symbol}</Link><span className="ml-2 text-slate-400">{peer.companyName}</span></td><td>{percentPoint(peer.marginOfSafety)}</td><td>{number(peer.totalScore)}</td><td>{ratioPercent(peer.roic)}</td></tr>) : <tr><td colSpan={4} className="py-5 text-slate-400">Peer context is unavailable.</td></tr>}</tbody>
                </table>
              </div>
            </Panel>
          </div>
        </Section>

        <Section id="risk" title="Risk And Data Quality Caveats">
          <div className="space-y-5">
            <RiskIntelligence review={review.data} currency={currency} />
            <div className="space-y-3">
              {review.data.dataQualityNotes.map((note) => {
                const Note = note.severity === 'INFO' ? InfoNote : DataGap
                return <Note key={`${note.category}-${note.message}`}>{note.category}: {note.message}</Note>
              })}
              <DataGap>Valuation outputs are model estimates based on available local data. They are not personalised investment advice, order recommendations, or a guarantee of intrinsic value.</DataGap>
            </div>
          </div>
        </Section>

        <Section id="actions" title="Next Actions">
          <div className="grid gap-5 lg:grid-cols-2">
            <Panel title="Add to portfolio"><AddToPortfolio symbol={symbol} currentPrice={d.currentPrice} sector={d.sector} /></Panel>
            <Panel title="Continue research">
              <div className="flex flex-wrap gap-3">
                <Link to={`/securities/${symbol}`} className="rounded-lg bg-slate-800 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700">Security Detail</Link>
                <Link to="/screener" className="rounded-lg bg-slate-800 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700">Screener</Link>
                <Link to="/watchlist" className="rounded-lg bg-slate-800 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700">Watchlist</Link>
              </div>
            </Panel>
          </div>
        </Section>
      </div>
    </div>
  )
}
