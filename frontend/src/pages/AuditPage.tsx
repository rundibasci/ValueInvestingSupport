import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { professionalApi, type ResearchSnapshot } from '../api/professional'

const inputClass = 'rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-emerald-400'

function money(value: number | null | undefined): string {
  return value == null ? '-' : new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 2 }).format(value)
}

function number(value: number | null | undefined): string {
  return value == null ? '-' : new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(value)
}

function dateTime(value: string): string {
  return new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function csvValue(value: unknown): string {
  const text = value == null ? '' : String(value)
  return `"${text.replace(/"/g, '""')}"`
}

function exportCsv(rows: ResearchSnapshot[]): void {
  const headers = ['capturedAt', 'symbol', 'actionType', 'currentPrice', 'compositeFairValue', 'marginOfSafety', 'valueScore', 'waccUsed', 'dataSource', 'piotroskiScore', 'moatClassification', 'rationale']
  const csv = [headers.join(','), ...rows.map((row) => headers.map((key) => csvValue(row[key as keyof ResearchSnapshot])).join(','))].join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = 'research-decision-history.csv'
  anchor.click()
  URL.revokeObjectURL(url)
}

export function AuditPage(): JSX.Element {
  const [symbol, setSymbol] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [query, setQuery] = useState({ symbol: '', from: '', to: '' })
  const decisions = useQuery({
    queryKey: ['audit-decisions', query],
    queryFn: () => professionalApi.decisions(query),
  })
  const sorted = useMemo(() => [...(decisions.data ?? [])].sort((a, b) => b.capturedAt.localeCompare(a.capturedAt)), [decisions.data])

  return (
    <section className="space-y-6">
      <div>
        <p className="text-sm font-medium text-emerald-300">Professional workflow</p>
        <h1 className="mt-2 text-3xl font-semibold text-white">Decision history</h1>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-400">
          Timestamped research snapshots captured when portfolio and watchlist actions are taken.
        </p>
      </div>
      <form
        className="grid gap-3 rounded-xl border border-slate-800 bg-slate-900/60 p-5 sm:grid-cols-[1fr_1fr_1fr_auto]"
        onSubmit={(event) => {
          event.preventDefault()
          setQuery({ symbol: symbol.trim().toUpperCase(), from, to })
        }}
      >
        <label className="text-sm font-medium text-slate-200">Symbol<input value={symbol} onChange={(event) => setSymbol(event.target.value)} className={`mt-1 ${inputClass}`} placeholder="AAPL" /></label>
        <label className="text-sm font-medium text-slate-200">From<input type="date" value={from} onChange={(event) => setFrom(event.target.value)} className={`mt-1 ${inputClass}`} /></label>
        <label className="text-sm font-medium text-slate-200">To<input type="date" value={to} onChange={(event) => setTo(event.target.value)} className={`mt-1 ${inputClass}`} /></label>
        <div className="flex items-end gap-2">
          <button className="rounded-lg bg-emerald-400 px-4 py-2 text-sm font-semibold text-slate-950">Filter</button>
          <button type="button" disabled={!sorted.length} onClick={() => exportCsv(sorted)} className="rounded-lg border border-slate-700 px-4 py-2 text-sm font-semibold text-slate-200 disabled:opacity-50">CSV</button>
        </div>
      </form>
      <div className="overflow-hidden rounded-xl border border-slate-800 bg-slate-900/50">
        {decisions.isLoading ? (
          <p className="p-6 text-sm text-slate-400">Loading decision history...</p>
        ) : decisions.isError ? (
          <p role="alert" className="m-5 rounded-lg border border-rose-300/30 bg-rose-400/10 p-3 text-sm text-rose-100">Decision history could not be loaded.</p>
        ) : sorted.length === 0 ? (
          <p className="p-6 text-sm text-slate-400">No captured decisions match the current filters.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-[72rem] w-full text-left text-sm">
              <thead className="bg-slate-950/50 text-xs uppercase text-slate-400">
                <tr><th className="px-4 py-3">Captured</th><th>Action</th><th>Price</th><th>Fair value</th><th>MoS</th><th>Score</th><th>WACC</th><th>Source</th><th>Quality</th><th>Rationale</th></tr>
              </thead>
              <tbody className="divide-y divide-slate-800">
                {sorted.map((row) => (
                  <tr key={row.id} className="text-slate-200">
                    <td className="px-4 py-3"><span className="block font-medium text-white">{row.symbol}</span><span className="text-xs text-slate-400">{dateTime(row.capturedAt)}</span></td>
                    <td>{row.actionType.replace(/_/g, ' ').toLowerCase()}</td>
                    <td>{money(row.currentPrice)}</td>
                    <td>{money(row.compositeFairValue)}</td>
                    <td>{number(row.marginOfSafety)}%</td>
                    <td>{number(row.valueScore)}</td>
                    <td>{number(row.waccUsed)}</td>
                    <td>{row.dataSource ?? '-'}</td>
                    <td>{row.piotroskiScore == null ? '-' : `${row.piotroskiScore}/9`} {row.moatClassification ? `- ${row.moatClassification}` : ''}</td>
                    <td className="max-w-[22rem] text-slate-400">{row.rationale || '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  )
}
