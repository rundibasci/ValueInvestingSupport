import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { apiFetch } from '../api/client'
import { useAuth } from '../auth/AuthProvider'

type SeedResult = {
  symbol: string
  companyName: string | null
  compositeFairValue: number | null
  marginOfSafety: number | null
  recommendation: string | null
  source: string | null
  error: string | null
}

function money(value: number | null): string {
  return value == null ? '-' : new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value)
}

function percent(value: number | null): string {
  return value == null ? '-' : `${Number(value).toFixed(2)}%`
}

export function AdminSeedPage(): JSX.Element {
  const { session } = useAuth()
  const [tickers, setTickers] = useState('AAPL, MSFT, KO, JNJ')
  const [results, setResults] = useState<SeedResult[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (session?.role !== 'ADMIN') return <Navigate to="/" replace />

  async function submit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault()
    setError(null)
    setResults(null)
    const value = tickers.split(',').map((ticker) => ticker.trim()).filter(Boolean).join(',')
    if (!value) {
      setError('Enter at least one ticker.')
      return
    }
    setSubmitting(true)
    try {
      const response = await apiFetch(`/api/v1/admin/seed?${new URLSearchParams({ tickers: value }).toString()}`, { method: 'POST' })
      if (!response.ok) throw new Error(`Request failed (${response.status}).`)
      setResults((await response.json()) as SeedResult[])
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Unable to seed tickers right now.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="space-y-6">
      <div className="flex flex-col justify-between gap-4 md:flex-row md:items-end">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[.18em] text-emerald-400">Administration</p>
          <h1 className="mt-2 text-3xl font-semibold text-white">Seed tickers</h1>
          <p className="mt-3 max-w-2xl text-slate-400">Load profiles, fundamentals, ratios, quotes, and valuations for market symbols.</p>
        </div>
        <Link to="/screener" className="rounded-lg border border-slate-700 px-4 py-2 text-sm font-medium text-slate-200 hover:border-emerald-300 hover:text-white">
          Open screener
        </Link>
      </div>

      <form onSubmit={submit} className="rounded-2xl border border-slate-800 bg-slate-900/50 p-6">
        <label className="block text-sm font-medium text-slate-200">
          Tickers
          <input
            value={tickers}
            onChange={(event) => setTickers(event.target.value)}
            className="mt-2 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white outline-none transition focus:border-emerald-400 focus:ring-2 focus:ring-emerald-400/30"
            placeholder="AAPL, MSFT, KO"
          />
        </label>
        <div className="mt-5 flex flex-wrap items-center gap-3">
          <button disabled={submitting} className="rounded-lg bg-emerald-400 px-4 py-2 font-semibold text-slate-950 hover:bg-emerald-300 disabled:cursor-not-allowed disabled:opacity-60">
            {submitting ? 'Seeding...' : 'Seed tickers'}
          </button>
          {error && <p role="alert" className="text-sm text-rose-200">{error}</p>}
        </div>
      </form>

      {results && (
        <section className="overflow-hidden rounded-2xl border border-slate-800 bg-slate-900/50">
          <div className="border-b border-slate-800 px-5 py-4">
            <h2 className="text-lg font-semibold text-white">Seed results</h2>
          </div>
          <div className="overflow-x-auto">
            <table className="min-w-[860px] w-full text-left text-sm">
              <thead className="bg-slate-950/50 text-xs uppercase tracking-wide text-slate-400">
                <tr>
                  <th className="px-4 py-3">Ticker</th>
                  <th className="px-4 py-3">Company</th>
                  <th className="px-4 py-3">Fair value</th>
                  <th className="px-4 py-3">MoS</th>
                  <th className="px-4 py-3">Recommendation</th>
                  <th className="px-4 py-3">Source</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">
                    <span className="sr-only">Review</span>
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800">
                {results.map((result) => (
                  <tr key={result.symbol} className="text-slate-200">
                    <td className="px-4 py-4 font-semibold text-white">
                      {result.error ? result.symbol : <Link className="text-emerald-300 hover:text-emerald-200" to={`/securities/${encodeURIComponent(result.symbol)}`}>{result.symbol}</Link>}
                    </td>
                    <td className="px-4 py-4">{result.companyName ?? '-'}</td>
                    <td className="px-4 py-4">{money(result.compositeFairValue)}</td>
                    <td className="px-4 py-4">{percent(result.marginOfSafety)}</td>
                    <td className="px-4 py-4">{result.recommendation ?? '-'}</td>
                    <td className="px-4 py-4 text-slate-400">{result.source ?? '-'}</td>
                    <td className={`px-4 py-4 ${result.error ? 'text-rose-200' : 'text-emerald-200'}`}>{result.error ?? 'Seeded'}</td>
                    <td className="px-4 py-4">
                      {!result.error && <Link className="font-semibold text-emerald-300 hover:text-emerald-200" to={`/securities/${encodeURIComponent(result.symbol)}/review`}>Review</Link>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </section>
  )
}
