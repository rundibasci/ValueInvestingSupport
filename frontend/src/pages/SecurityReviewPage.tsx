import { useMutation, useQuery } from '@tanstack/react-query'
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
type Valuation = { currentPrice: number | null; dcf: { base: number | null; low: number | null; high: number | null } | null; grahamNumber: number | null; ddmValue: number | null; compositeFairValue: number | null; marginOfSafety: number | null; mosLow: number | null; mosHigh: number | null; recommendation: string | null; analystEstimates: { meanTarget: number | null; lowTarget: number | null; highTarget: number | null; analystCount: number; consensus: string | null } | null; dataAsOf: string | null; disclaimer: string }
type Dividends = { history: Array<{ exDividendDate: string | null; paymentDate: string | null; amount: number | null; currency: string | null }>; streak: number; cagr3y: number | null; cagr5y: number | null; cagr10y: number | null }
type Metrics = { cagr3y: number | null; cagr5y: number | null; cagr10y: number | null }
type Growth = { revenue: Metrics; fcf: Metrics; eps: Metrics }
type Peers = { peers: Array<{ symbol: string; companyName: string; currentPrice: number | null; compositeFairValue: number | null; marginOfSafety: number | null; totalScore: number | null; pe: number | null; roic: number | null }> }
type Score = { totalScore: number | null; mosScore: number | null; qualityScore: number | null; safetyScore: number | null; growthScore: number | null; dividendScore: number | null; scoreDate: string | null }

const money = (value: number | null | undefined, currency = 'USD') =>
  value == null ? 'Unavailable' : new Intl.NumberFormat('en-US', { style: 'currency', currency, maximumFractionDigits: 2, notation: Math.abs(value) >= 1000000 ? 'compact' : 'standard' }).format(value)
const number = (value: number | null | undefined) => value == null ? 'Unavailable' : new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(value)
const compact = (value: number | null | undefined) => value == null ? 'Unavailable' : new Intl.NumberFormat('en-US', { maximumFractionDigits: 2, notation: 'compact' }).format(value)
const percent = (value: number | null | undefined) => value == null ? 'Unavailable' : `${new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(value)}%`
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
      <div className="h-72 w-full" role="img" aria-label={summary}>
        <ResponsiveContainer>
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
  return <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead className="border-b border-slate-700 text-slate-400"><tr><th className="pb-3">Metric</th><th>3 years</th><th>5 years</th><th>10 years</th></tr></thead><tbody>{rows.map(([label, values]) => <tr key={label} className="border-b border-slate-800"><th className="py-3 font-medium text-white">{label}</th><td>{percent(values?.cagr3y)}</td><td>{percent(values?.cagr5y)}</td><td>{percent(values?.cagr10y)}</td></tr>)}</tbody></table></div>
}

function CoverageGrid({ queries }: { queries: Array<[string, boolean, unknown]> }): JSX.Element {
  return <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">{queries.map(([label, ok, error]) => <div key={label} className="rounded-lg border border-slate-800 bg-slate-950/50 p-3"><span className="block text-xs uppercase tracking-wide text-slate-500">{label}</span><span className={`mt-1 inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${ok ? 'bg-amber-300/15 text-amber-100' : 'bg-slate-700 text-slate-200'}`}>{ok ? 'Provider metadata unavailable' : 'Unavailable'}</span>{error instanceof Error && <p className="mt-2 text-xs leading-5 text-slate-500">{error.message}</p>}</div>)}</div>
}

function DisabledAddToPortfolio(): JSX.Element {
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-950/50 p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm leading-6 text-slate-300">Add this symbol to a portfolio from the review page.</p>
        <span className="rounded-full bg-slate-700 px-3 py-1 text-xs font-semibold text-slate-200">Coming soon</span>
      </div>
      <button disabled className="mt-4 rounded-lg bg-slate-700 px-4 py-2 text-sm font-semibold text-slate-300 opacity-60">Add to portfolio</button>
      <p className="mt-3 text-xs leading-5 text-slate-500">H4A keeps this action visible but disabled. The functional portfolio-add flow is reserved for H4B.</p>
    </div>
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
      {custom.data && <p className="sm:col-span-2 text-sm text-emerald-200">Custom valuation calculated. Fair value: {money(custom.data.dcfFairValue, currency)}.</p>}
    </form>
  )
}

export function SecurityReviewPage(): JSX.Element {
  const { symbol: rawSymbol = '' } = useParams()
  const symbol = rawSymbol.trim().toUpperCase()
  const [scrollProgress, setScrollProgress] = useState(0)
  const detail = useQuery({ queryKey: ['security-review', symbol, 'detail'], enabled: !!symbol, queryFn: () => json<Detail>(`/api/v1/securities/${encodeURIComponent(symbol)}`), retry: false })
  const financials = useQuery({ queryKey: ['security-review', symbol, 'financials'], enabled: !!symbol, queryFn: () => json<Financials>(`/api/v1/securities/${encodeURIComponent(symbol)}/financials`), retry: false })
  const ratios = useQuery({ queryKey: ['security-review', symbol, 'ratios'], enabled: !!symbol, queryFn: () => json<Ratios>(`/api/v1/securities/${encodeURIComponent(symbol)}/ratios`), retry: false })
  const valuation = useQuery({ queryKey: ['security-review', symbol, 'valuation'], enabled: !!symbol, queryFn: () => json<Valuation>(`/api/v1/securities/${encodeURIComponent(symbol)}/valuation`), retry: false })
  const dividends = useQuery({ queryKey: ['security-review', symbol, 'dividends'], enabled: !!symbol, queryFn: () => json<Dividends>(`/api/v1/securities/${encodeURIComponent(symbol)}/dividends`), retry: false })
  const growth = useQuery({ queryKey: ['security-review', symbol, 'growth'], enabled: !!symbol, queryFn: () => json<Growth>(`/api/v1/securities/${encodeURIComponent(symbol)}/growth`), retry: false })
  const peers = useQuery({ queryKey: ['security-review', symbol, 'peers'], enabled: !!symbol, queryFn: () => json<Peers>(`/api/v1/securities/${encodeURIComponent(symbol)}/peers`), retry: false })
  const score = useQuery({ queryKey: ['security-review', symbol, 'score'], enabled: !!symbol, queryFn: () => json<Score>(`/api/v1/securities/${encodeURIComponent(symbol)}/score`), retry: false })
  const addWatchlist = useMutation({ mutationFn: () => watchlistApi.add(symbol, { mosAlertMin: null, mosAlertMax: null, fundamentalDegradeThreshold: null }) })

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

  const currency = detail.data?.currency || 'USD'
  const annual = useMemo(() => [...(financials.data?.annuals || [])].sort((a, b) => a.fiscalYear - b.fiscalYear).map((item) => ({ label: String(item.fiscalYear), ...item })), [financials.data])
  const ratioData = useMemo(() => [...(ratios.data?.ratios || [])].sort((a, b) => a.date.localeCompare(b.date)).map((item) => ({ label: item.date.slice(0, 4), ...item })), [ratios.data])
  const dividendData = useMemo(() => [...(dividends.data?.history || [])].reverse().map((item) => ({ label: (item.paymentDate || item.exDividendDate || '').slice(0, 7), amount: item.amount })), [dividends.data])

  if (!symbol) return <div className="mx-auto max-w-3xl"><DataGap>Choose a security from the screener to open an in-depth review.</DataGap></div>
  if (detail.isLoading) return <div className="py-12 text-center text-slate-400">Loading the stock review packet...</div>
  if (!detail.data) return <div className="mx-auto max-w-3xl"><DataGap>Security review data could not be loaded. Return to the <Link className="text-emerald-300 underline" to="/screener">screener</Link> and try again.</DataGap></div>

  const d = detail.data
  const sourceQueries: Array<[string, boolean, unknown]> = [
    ['Profile', detail.isSuccess, detail.error],
    ['Fundamentals', financials.isSuccess, financials.error],
    ['Ratios', ratios.isSuccess, ratios.error],
    ['Quote', detail.isSuccess && d.currentPrice != null, null],
    ['Dividends', dividends.isSuccess, dividends.error],
    ['Valuation', valuation.isSuccess, valuation.error],
    ['Score', score.isSuccess, score.error],
    ['Analyst estimates', Boolean(valuation.data?.analystEstimates), null],
  ]

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
          <button disabled={addWatchlist.isPending} onClick={() => addWatchlist.mutate()} className="rounded-lg bg-emerald-400 px-4 py-2.5 text-sm font-semibold text-slate-950 disabled:opacity-60">{addWatchlist.isPending ? 'Adding...' : 'Add to watchlist'}</button>
          <Link to={`/securities/${symbol}`} className="rounded-lg border border-slate-700 px-4 py-2.5 text-sm font-semibold text-slate-200 hover:bg-slate-800">Open Security Detail</Link>
          <Link to="/admin/seed" className="rounded-lg border border-slate-700 px-4 py-2.5 text-sm font-semibold text-slate-200 hover:bg-slate-800">Refresh or seed</Link>
        </div>
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
          <CoverageGrid queries={sourceQueries} />
          <DataGap>Provider-level labels such as FMP, Yahoo Finance, or Mixed are not exposed by the current detail endpoints. The review labels provider-specific coverage as unavailable instead of inferring it from successful application API responses.</DataGap>
        </Section>

        <Section id="valuation" title="Valuation And Margin Of Safety" aside={<span className="rounded-full bg-amber-300/10 px-3 py-1 text-xs font-semibold text-amber-100">Decision support</span>}>
          <div className="grid gap-5 lg:grid-cols-2">
            <Panel title="Model outputs">
              <dl className="grid gap-3 sm:grid-cols-2">
                <Metric label="Market price" value={money(valuation.data?.currentPrice ?? d.currentPrice, currency)} />
                <Metric label="Composite fair value" value={money(valuation.data?.compositeFairValue, currency)} />
                <Metric label="Margin of safety" value={percent(valuation.data?.marginOfSafety)} />
                <Metric label="Recommendation" value={valuation.data?.recommendation || 'Unavailable'} />
                <Metric label="DCF base" value={money(valuation.data?.dcf?.base, currency)} />
                <Metric label="DCF low / high" value={`${money(valuation.data?.dcf?.low, currency)} / ${money(valuation.data?.dcf?.high, currency)}`} />
                <Metric label="Graham number" value={money(valuation.data?.grahamNumber, currency)} />
                <Metric label="DDM" value={money(valuation.data?.ddmValue, currency)} />
              </dl>
              {valuation.data?.analystEstimates ? <p className="mt-4 text-sm leading-6 text-slate-400">Analyst target range: {money(valuation.data.analystEstimates.lowTarget, currency)} to {money(valuation.data.analystEstimates.highTarget, currency)}. Mean target {money(valuation.data.analystEstimates.meanTarget, currency)} from {valuation.data.analystEstimates.analystCount} estimates.</p> : <DataGap>Analyst target range is unavailable for this symbol.</DataGap>}
              <p className="mt-4 rounded-lg border border-amber-300/20 bg-amber-300/5 p-3 text-xs leading-5 text-amber-100">{valuation.data?.disclaimer || 'This is a decision-support tool, not investment advice (MiFID II).'}</p>
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
                <Metric label="TTM free cash flow" value={money(financials.data?.ttm?.fcf ?? d.fcf, currency)} />
                <Metric label="TTM revenue" value={money(financials.data?.ttm?.revenue ?? d.revenue, currency)} />
                <Metric label="FCF margin" value={financials.data?.ttm?.fcf != null && financials.data.ttm.revenue ? percent((financials.data.ttm.fcf / financials.data.ttm.revenue) * 100) : 'Unavailable'} />
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
                <Metric label="Debt / equity" value={percent(ratioData.at(-1)?.debtToEquity as number | null | undefined)} />
                <Metric label="Current ratio" value="Unavailable" note="Not supplied by the current API response." />
                <Metric label="Quick ratio" value="Unavailable" note="Not supplied by the current API response." />
                <Metric label="Interest coverage" value="Unavailable" note="Not supplied by the current API response." />
              </dl>
              <DataGap>Total debt, cash, net debt, short-term debt, and long-term debt are not exposed through the current frontend contract. The review labels those fields as unavailable instead of inventing a balance-sheet trend.</DataGap>
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
                <Metric label="Dividend yield" value={percent(d.dividendYield)} />
                <Metric label="Dividend streak" value={dividends.data ? `${dividends.data.streak} years` : 'Unavailable'} />
                <Metric label="3-year CAGR" value={percent(dividends.data?.cagr3y)} />
                <Metric label="5-year CAGR" value={percent(dividends.data?.cagr5y)} />
                <Metric label="10-year CAGR" value={percent(dividends.data?.cagr10y)} />
                <Metric label="Payout / FCF coverage" value="Unavailable" note="Coverage is not supplied by the current dividend endpoint." />
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
                <Metric label="ROIC" value={percent(d.roic)} />
                <Metric label="ROE" value={percent(ratioData.at(-1)?.roe as number | null | undefined)} />
                <Metric label="Gross margin" value={percent(ratioData.at(-1)?.grossMargin as number | null | undefined)} />
                <Metric label="Value score" value={number(score.data?.totalScore)} note={score.data?.scoreDate ? `Score date: ${date(score.data.scoreDate)}` : undefined} />
              </dl>
              <CagrTable growth={growth.data} />
            </Panel>
            <Panel title="Peer context">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead className="border-b border-slate-700 text-slate-400"><tr><th className="pb-3">Company</th><th>MoS</th><th>Score</th><th>ROIC</th></tr></thead>
                  <tbody>{peers.data?.peers.length ? peers.data.peers.map((peer) => <tr key={peer.symbol} className="border-b border-slate-800"><td className="py-3"><Link className="font-medium text-emerald-300" to={`/securities/${peer.symbol}/review`}>{peer.symbol}</Link><span className="ml-2 text-slate-400">{peer.companyName}</span></td><td>{percent(peer.marginOfSafety)}</td><td>{number(peer.totalScore)}</td><td>{percent(peer.roic)}</td></tr>) : <tr><td colSpan={4} className="py-5 text-slate-400">Peer context is unavailable.</td></tr>}</tbody>
                </table>
              </div>
            </Panel>
          </div>
        </Section>

        <Section id="risk" title="Risk And Data Quality Caveats">
          <div className="space-y-3">
            <DataGap>Unavailable metrics are labelled explicitly. Current known gaps include provider-specific source coverage, current ratio, quick ratio, interest coverage, total debt, cash, net debt, payout ratio, and FCF dividend coverage unless future endpoints supply them.</DataGap>
            <DataGap>Provider fallbacks and plan restrictions are governed by the backend market-data client. The current frontend detail contracts do not expose FMP/Yahoo fallback reasons by category.</DataGap>
            <DataGap>Valuation outputs are model estimates based on available local data. They are not personalised investment advice, order recommendations, or a guarantee of intrinsic value.</DataGap>
          </div>
        </Section>

        <Section id="actions" title="Next Actions">
          <div className="grid gap-5 lg:grid-cols-2">
            <Panel title="Add to portfolio"><DisabledAddToPortfolio /></Panel>
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
