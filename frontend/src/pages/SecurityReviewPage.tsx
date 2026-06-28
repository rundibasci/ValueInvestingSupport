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
import { watchlistApi } from '../api/watchlist'

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
type Annual = { fiscalYear: number; revenue: number | null; netIncome: number | null; fcf: number | null; eps: number | null; bvps: number | null }
type Financials = { annuals: Annual[]; quarters: Array<{ period: string; revenue: number | null; netIncome: number | null; fcf: number | null; eps: number | null }>; ttm: { revenue: number | null; netIncome: number | null; fcf: number | null; eps: number | null } | null }
type RatioItem = { date: string; pe: number | null; roic: number | null; roe: number | null; debtToEquity: number | null; grossMargin: number | null; dividendYield: number | null }
type Ratios = { ratios: RatioItem[] }
type Valuation = { currentPrice: number | null; dcf: { base: number | null; low: number | null; high: number | null } | null; grahamNumber: number | null; ddmValue: number | null; compositeFairValue: number | null; marginOfSafety: number | null; mosLow: number | null; mosHigh: number | null; recommendation: string | null; analystEstimates: { priceTargetMean: number | null; priceTargetLow: number | null; priceTargetHigh: number | null; analystCount: number; consensus: string | null } | null; dataAsOf: string | null; disclaimer: string }
type Dividends = { history: Array<{ exDividendDate: string | null; paymentDate: string | null; amount: number | null; currency: string | null }>; streak: number; cagr3y: number | null; cagr5y: number | null; cagr10y: number | null }
type Metrics = { cagr3y: number | null; cagr5y: number | null; cagr10y: number | null }
type Growth = { revenue: Metrics; fcf: Metrics; eps: Metrics }
type Peers = { peers: Array<{ symbol: string; companyName: string; currentPrice: number | null; compositeFairValue: number | null; marginOfSafety: number | null; totalScore: number | null; pe: number | null; roic: number | null }> }
type Score = { totalScore: number | null; mosScore: number | null; qualityScore: number | null; safetyScore: number | null; growthScore: number | null; dividendScore: number | null; scoreDate: string | null }
type FinancialHealth = { totalDebt: number | null; cash: number | null; netDebt: number | null; debtToEquity: number | null; currentRatio: number | null; quickRatio: number | null; interestCoverage: number | null; payoutRatio: number | null; dividendYield: number | null; grossMargin: number | null; operatingMargin: number | null; netMargin: number | null; dataAsOf: string | null }
type SourceCoverageItem = { category: string; provider: string | null; status: string; message: string | null }
type FreshnessItem = { category: string; dataAsOf: string | null; status: string; message: string | null }
type DataQualityNote = { category: string; severity: string; message: string }
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
  financialHealth: FinancialHealth
  sourceCoverage: SourceCoverageItem[]
  freshness: FreshnessItem[]
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

function Chart({ data, lines, bar = false, summary }: { data: Array<Record<string, number | string | null>>; lines: Array<[string, string]>; bar?: boolean; summary: string }): JSX.Element {
  if (!data.length) return <DataGap>No historical series is available for this chart.</DataGap>
  const Component = bar ? BarChart : LineChart
  return (
    <div>
      <p className="mb-3 text-sm leading-6 text-slate-400">{summary}</p>
      <div className="min-h-72 h-72 min-w-0 w-full" role="img" aria-label={summary}>
        <ResponsiveContainer width="100%" height="100%">
          <Component data={data}>
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

function AddToPortfolio({ symbol }: { symbol: string }): JSX.Element {
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

export function SecurityReviewPage(): JSX.Element {
  const { symbol: rawSymbol = '' } = useParams()
  const symbol = rawSymbol.trim().toUpperCase()
  const [scrollProgress, setScrollProgress] = useState(0)
  const review = useQuery({ queryKey: ['security-review', symbol], enabled: !!symbol, queryFn: () => json<Review>(`/api/v1/securities/${encodeURIComponent(symbol)}/review`), retry: false })
  const watchlist = useQuery({ queryKey: ['watchlist'], enabled: !!symbol, queryFn: watchlistApi.list })
  const queryClient = useQueryClient()
  const addWatchlist = useMutation({
    mutationFn: () => watchlistApi.add(symbol, { mosAlertMin: null, mosAlertMax: null, fundamentalDegradeThreshold: null }),
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

  return (
    <div className="space-y-6">
      <section className="rounded-lg border border-slate-800 bg-slate-900/70 p-5 sm:p-7">
        <div className="flex flex-col justify-between gap-5 lg:flex-row lg:items-start">
          <div>
            <Link className="text-sm font-medium text-emerald-300 hover:text-emerald-200" to="/screener">Back to screener</Link>
            <p className="mt-5 text-xs font-semibold uppercase tracking-[.22em] text-emerald-300">In-depth stock review</p>
            <h1 className="mt-2 text-3xl font-semibold text-white sm:text-4xl">{d.companyName} <span className="text-slate-400">({d.symbol})</span></h1>
            <p className="mt-2 text-sm text-slate-400">{[d.sector, d.exchange, d.country, d.currency].filter(Boolean).join(' · ') || 'Profile context unavailable'}</p>
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
            ['debt', 'Debt'], ['history', 'Graphs'], ['dividends', 'Dividends'], ['quality', 'Quality'], ['risk', 'Risk'],
          ].map(([id, label]) => <a key={id} href={`#${id}`} className="mt-3 whitespace-nowrap rounded-md px-3 py-2 text-sm font-medium text-slate-300 hover:bg-slate-800 hover:text-white">{label}</a>)}
        </div>
      </nav>

      <div className="rounded-lg border border-slate-800 bg-slate-900/40 p-5 sm:p-7">
        <Section id="source" title="Source Coverage And Freshness">
          <CoverageGrid coverage={review.data.sourceCoverage} freshness={review.data.freshness} />
          <DataGap>Provider labels are shown where the backend has stored provider metadata. Otherwise the endpoint reports application data availability without inferring a provider.</DataGap>
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
                <Metric label="Graham number" value={money(valuation?.grahamNumber, currency)} />
                <Metric label="DDM" value={money(valuation?.ddmValue, currency)} />
              </dl>
              {valuation?.analystEstimates ? <p className="mt-4 text-sm leading-6 text-slate-400">Analyst target range: {money(valuation.analystEstimates.priceTargetLow, currency)} to {money(valuation.analystEstimates.priceTargetHigh, currency)}. Mean target {money(valuation.analystEstimates.priceTargetMean, currency)} from {valuation.analystEstimates.analystCount} estimates.</p> : <DataGap>Analyst target range is unavailable for this symbol.</DataGap>}
              <p className="mt-4 rounded-lg border border-amber-300/20 bg-amber-300/5 p-3 text-xs leading-5 text-amber-100">{valuation?.disclaimer || 'This is a decision-support tool, not investment advice (MiFID II).'}</p>
            </Panel>
            <Panel title="Custom DCF">
              <CustomDcf symbol={symbol} currency={currency} />
            </Panel>
          </div>
        </Section>

        <Section id="cash" title="Cash Generation">
          <div className="grid gap-5 lg:grid-cols-2">
            <Panel title="Current cash profile">
              <dl className="grid gap-3 sm:grid-cols-2">
                <Metric label="TTM free cash flow" value={money(financials.ttm?.fcf ?? d.fcf, currency)} />
                <Metric label="TTM revenue" value={money(financials.ttm?.revenue ?? d.revenue, currency)} />
                <Metric label="FCF margin" value={financials.ttm?.fcf != null && financials.ttm.revenue ? ratioPercent(financials.ttm.fcf / financials.ttm.revenue) : 'Unavailable'} />
                <Metric label="Positive FCF years" value={annual.length ? `${annual.filter((item) => typeof item.fcf === 'number' && item.fcf > 0).length} of ${annual.length}` : 'Unavailable'} />
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
                <Metric label="Quick ratio" value={number(health.quickRatio)} note={health.quickRatio == null ? 'Provider data did not supply quick ratio.' : undefined} />
                <Metric label="Interest coverage" value={number(health.interestCoverage)} note={health.interestCoverage == null ? 'Provider data did not supply interest coverage.' : undefined} />
              </dl>
              <DataGap>Short-term debt and long-term debt breakdowns are unavailable in the current data model. The endpoint reports total debt, cash, and net debt when stored locally.</DataGap>
            </Panel>
            <Panel title="Debt trend">
              <Chart data={ratioData} lines={[['debtToEquity', 'Debt / equity']]} summary="Debt trend currently uses debt-to-equity because total debt and cash history are unavailable from the active endpoint." />
            </Panel>
          </div>
        </Section>

        <Section id="history" title="Historical Graphs">
          <div className="grid gap-5 xl:grid-cols-2">
            <Panel title="Earnings history"><Chart data={annual} lines={[['revenue', 'Revenue'], ['netIncome', 'Net income'], ['eps', 'EPS'], ['fcf', 'FCF']]} summary="Revenue, net income, EPS, and free cash flow over annual periods." /></Panel>
            <Panel title="Debt history"><Chart data={ratioData} lines={[['debtToEquity', 'Debt / equity']]} summary="Debt-to-equity over time; total debt, cash, and net debt are unavailable in the current API." /></Panel>
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
                <Metric label="Value score" value={number(score?.totalScore)} note={score?.scoreDate ? `Score date: ${date(score.scoreDate)}` : undefined} />
              </dl>
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
          <div className="space-y-3">
            {review.data.dataQualityNotes.map((note) => <DataGap key={`${note.category}-${note.message}`}>{note.category}: {note.message}</DataGap>)}
            <DataGap>Valuation outputs are model estimates based on available local data. They are not personalised investment advice, order recommendations, or a guarantee of intrinsic value.</DataGap>
          </div>
        </Section>

        <Section id="actions" title="Next Actions">
          <div className="grid gap-5 lg:grid-cols-2">
            <Panel title="Add to portfolio"><AddToPortfolio symbol={symbol} /></Panel>
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
