import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { watchlistApi } from '../api/watchlist'
import type { Alert, WatchlistItem, WatchlistThresholds } from '../api/watchlist'

type FormValues = { symbol: string; mosAlertMin: string; mosAlertMax: string; fundamentalDegradeThreshold: string }
const emptyForm: FormValues = { symbol: '', mosAlertMin: '', mosAlertMax: '', fundamentalDegradeThreshold: '' }

function numberOrNull(value: string): number | null { return value.trim() === '' ? null : Number(value) }
function thresholdsFrom(form: FormValues): WatchlistThresholds {
  return {
    mosAlertMin: numberOrNull(form.mosAlertMin),
    mosAlertMax: numberOrNull(form.mosAlertMax),
    fundamentalDegradeThreshold: numberOrNull(form.fundamentalDegradeThreshold),
  }
}
function percent(value: number | null): string { return value == null ? 'Not set' : `${value.toFixed(1)}%` }
function date(value: string | null): string { return value ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : 'Time unavailable' }
function friendlyAlertType(type: string): string { return type.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase()) }
function alertDescription(alert: Alert): string {
  const threshold = alert.threshold == null ? '' : ` at ${alert.threshold}%`
  return `${friendlyAlertType(alert.alertType)} condition detected${threshold}. Review the underlying data before making any decision.`
}

export function WatchlistPage(): JSX.Element {
  const client = useQueryClient()
  const [form, setForm] = useState<FormValues>(emptyForm)
  const [editing, setEditing] = useState<WatchlistItem | null>(null)
  const [filter, setFilter] = useState<'all' | 'alerts' | 'quiet'>('all')
  const watchlist = useQuery({ queryKey: ['watchlist'], queryFn: watchlistApi.list })
  const alerts = useQuery({ queryKey: ['watchlist', 'alerts'], queryFn: watchlistApi.alerts })
  const invalidate = () => Promise.all([client.invalidateQueries({ queryKey: ['watchlist'] }), client.invalidateQueries({ queryKey: ['watchlist', 'alerts'] })])
  const add = useMutation({ mutationFn: () => watchlistApi.add(form.symbol.trim().toUpperCase(), thresholdsFrom(form)), onSuccess: () => { setForm(emptyForm); void invalidate() } })
  const update = useMutation({ mutationFn: () => watchlistApi.update(editing!.id, thresholdsFrom(form)), onSuccess: () => { setEditing(null); setForm(emptyForm); void invalidate() } })
  const remove = useMutation({ mutationFn: watchlistApi.remove, onSuccess: () => void invalidate() })
  const acknowledge = useMutation({ mutationFn: watchlistApi.acknowledge, onSuccess: () => void invalidate() })
  const alertSymbols = useMemo(() => new Set((alerts.data || []).map((alert) => alert.symbol)), [alerts.data])
  const items = (watchlist.data || []).filter((item) => filter === 'all' || (filter === 'alerts' ? alertSymbols.has(item.symbol) : !alertSymbols.has(item.symbol)))
  const submit = (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); if (editing) update.mutate(); else add.mutate() }
  const beginEdit = (item: WatchlistItem) => { setEditing(item); setForm({ symbol: item.symbol, mosAlertMin: item.mosAlertMin?.toString() || '', mosAlertMax: item.mosAlertMax?.toString() || '', fundamentalDegradeThreshold: item.fundamentalDegradeThreshold?.toString() || '' }) }
  const pending = add.isPending || update.isPending
  const formError = [add.error, update.error].find(Boolean)

  return <div className="space-y-8">
    <section className="rounded-2xl border border-slate-800 bg-slate-900/60 p-6 sm:p-8">
      <p className="text-xs font-semibold uppercase tracking-[.22em] text-emerald-300">Monitor</p>
      <div className="mt-3 flex flex-col justify-between gap-4 md:flex-row md:items-end"><div><h1 className="text-3xl font-semibold text-white sm:text-4xl">Watchlist & alerts</h1><p className="mt-2 max-w-2xl text-slate-400">Keep promising businesses in view and surface changes worth researching.</p></div><div className="rounded-xl border border-amber-300/20 bg-amber-300/5 px-4 py-3 text-xs leading-5 text-amber-100">Decision-support only, not investment advice (MiFID II). Alerts identify a condition; they do not prescribe an action.</div></div>
    </section>

    <section className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_23rem]">
      <div className="space-y-5">
        <div className="flex flex-wrap items-center justify-between gap-3"><div><h2 className="text-xl font-semibold text-white">Your watchlist</h2><p className="mt-1 text-sm text-slate-400">Each card shows its configured monitoring thresholds and current alert state.</p></div><div className="flex rounded-lg border border-slate-700 p-1" aria-label="Watchlist filters">{(['all', 'alerts', 'quiet'] as const).map((value) => <button key={value} onClick={() => setFilter(value)} className={`rounded-md px-3 py-1.5 text-sm capitalize ${filter === value ? 'bg-emerald-400 font-semibold text-slate-950' : 'text-slate-300 hover:text-white'}`}>{value}</button>)}</div></div>
        {watchlist.isLoading ? <State message="Loading your watchlist…" /> : watchlist.isError ? <State message="Your watchlist could not be loaded." retry={() => void watchlist.refetch()} error /> : items.length === 0 ? <State message={filter === 'all' ? 'Your watchlist is empty. Add a company to begin monitoring it.' : 'No securities match this filter.'} /> : <div className="grid gap-4 md:grid-cols-2">{items.map((item) => <WatchlistCard key={item.id} item={item} active={alertSymbols.has(item.symbol)} editing={editing?.id === item.id} removePending={remove.isPending} onEdit={() => beginEdit(item)} onRemove={() => { if (window.confirm(`Remove ${item.symbol} from your watchlist?`)) remove.mutate(item.id) }} />)}</div>}
      </div>
      <ThresholdForm form={form} setForm={setForm} editing={editing} pending={pending} error={formError} onCancel={() => { setEditing(null); setForm(emptyForm) }} onSubmit={submit} />
    </section>

    <section className="rounded-2xl border border-slate-800 bg-slate-900/60 p-6 sm:p-8"><div className="flex flex-wrap items-end justify-between gap-3"><div><p className="text-xs font-semibold uppercase tracking-[.22em] text-emerald-300">Active conditions</p><h2 className="mt-2 text-2xl font-semibold text-white">Alerts requiring review</h2></div><span className="rounded-full bg-slate-800 px-3 py-1 text-sm text-slate-300">{alerts.data?.length || 0} active</span></div>{alerts.isLoading ? <div className="mt-5"><State message="Loading active alerts…" /></div> : alerts.isError ? <div className="mt-5"><State message="Active alerts could not be loaded." retry={() => void alerts.refetch()} error /></div> : alerts.data?.length ? <div className="mt-5 grid gap-4 lg:grid-cols-2">{alerts.data.map((alert) => <AlertCard key={alert.id} alert={alert} pending={acknowledge.isPending} onAcknowledge={() => acknowledge.mutate(alert.id)} />)}</div> : <div className="mt-5"><State message="No active alerts. Your monitoring rules are currently quiet." /></div>}</section>
  </div>
}

function WatchlistCard({ item, active, editing, removePending, onEdit, onRemove }: { item: WatchlistItem; active: boolean; editing: boolean; removePending: boolean; onEdit: () => void; onRemove: () => void }): JSX.Element { return <article className={`rounded-2xl border bg-slate-900/60 p-5 ${editing ? 'border-emerald-400/70' : 'border-slate-800'}`}><div className="flex items-start justify-between gap-3"><div><Link to={`/securities/${encodeURIComponent(item.symbol)}`} className="text-xl font-semibold text-white hover:text-emerald-300">{item.symbol}</Link><p className="mt-1 text-xs text-slate-500">Added {date(item.addedAt)}</p></div><span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${active ? 'bg-amber-300/15 text-amber-100' : 'bg-emerald-300/10 text-emerald-200'}`}>{active ? 'Active alert' : 'Monitoring'}</span></div><dl className="mt-5 grid grid-cols-3 gap-3 text-sm"><Metric label="MoS floor" value={percent(item.mosAlertMin)} /><Metric label="MoS ceiling" value={percent(item.mosAlertMax)} /><Metric label="Fundamental threshold" value={percent(item.fundamentalDegradeThreshold)} /></dl><div className="mt-5 flex flex-wrap gap-3"><Link to={`/securities/${encodeURIComponent(item.symbol)}/review`} className="rounded-lg border border-emerald-400/30 px-3 py-2 text-sm font-medium text-emerald-200 hover:bg-emerald-400/10">Review</Link><button onClick={onEdit} className="rounded-lg border border-slate-600 px-3 py-2 text-sm font-medium text-slate-200 hover:border-emerald-300 hover:text-white">Edit alerts</button><button disabled={removePending} onClick={onRemove} className="rounded-lg px-3 py-2 text-sm font-medium text-rose-200 hover:bg-rose-300/10 disabled:opacity-60">Remove</button></div></article> }
function ThresholdForm({ form, setForm, editing, pending, error, onCancel, onSubmit }: { form: FormValues; setForm: (form: FormValues) => void; editing: WatchlistItem | null; pending: boolean; error: unknown; onCancel: () => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void }): JSX.Element { const set = (key: keyof FormValues, value: string) => setForm({ ...form, [key]: value }); return <aside className="h-fit rounded-2xl border border-slate-800 bg-slate-900/60 p-5"><h2 className="text-lg font-semibold text-white">{editing ? `Configure ${editing.symbol}` : 'Add to watchlist'}</h2><p className="mt-1 text-sm text-slate-400">Leave any threshold blank to disable that condition.</p><form onSubmit={onSubmit} className="mt-5 space-y-4"><label className="block text-sm font-medium text-slate-200">Ticker symbol<input required={!editing} disabled={Boolean(editing)} value={form.symbol} onChange={(event) => set('symbol', event.target.value.toUpperCase())} placeholder="AAPL" maxLength={12} className="mt-1.5 block w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white disabled:opacity-60" /></label><NumberField label="Margin of safety floor (%)" value={form.mosAlertMin} onChange={(value) => set('mosAlertMin', value)} /><NumberField label="Margin of safety ceiling (%)" value={form.mosAlertMax} onChange={(value) => set('mosAlertMax', value)} /><NumberField label="Fundamental degradation threshold (%)" value={form.fundamentalDegradeThreshold} onChange={(value) => set('fundamentalDegradeThreshold', value)} />{Boolean(error) && <p role="alert" className="text-sm text-rose-200">{error instanceof Error ? error.message : 'The change could not be saved.'}</p>}<button disabled={pending} className="w-full rounded-lg bg-emerald-400 px-4 py-2.5 text-sm font-semibold text-slate-950 disabled:opacity-60">{pending ? 'Saving…' : editing ? 'Save alert settings' : 'Add to watchlist'}</button>{editing && <button type="button" onClick={onCancel} className="w-full rounded-lg px-4 py-2 text-sm text-slate-300 hover:text-white">Cancel</button>}</form></aside> }
function NumberField({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }): JSX.Element { return <label className="block text-sm font-medium text-slate-200">{label}<input type="number" min="0" max="100" step="0.1" value={value} onChange={(event) => onChange(event.target.value)} className="mt-1.5 block w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" /></label> }
function AlertCard({ alert, pending, onAcknowledge }: { alert: Alert; pending: boolean; onAcknowledge: () => void }): JSX.Element { const high = alert.priority === 'HIGH'; return <article className="rounded-xl border border-slate-800 bg-slate-950/50 p-5"><div className="flex items-start justify-between gap-3"><div><Link to={`/securities/${encodeURIComponent(alert.symbol)}`} className="font-semibold text-white hover:text-emerald-300">{alert.symbol}</Link><p className="mt-1 text-sm text-slate-300">{friendlyAlertType(alert.alertType)}</p></div><span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${high ? 'bg-rose-300/15 text-rose-100' : 'bg-amber-300/15 text-amber-100'}`}>{high ? 'High priority' : 'Active alert'}</span></div><p className="mt-4 text-sm leading-6 text-slate-400">{alertDescription(alert)}</p><p className="mt-3 text-xs text-slate-500">Triggered {date(alert.triggeredAt)} · Delivery: {alert.deliveryStatus || 'not recorded'}</p><div className="mt-4 flex flex-wrap gap-3"><Link to={`/securities/${encodeURIComponent(alert.symbol)}/review`} className="rounded-lg border border-emerald-400/30 px-3 py-2 text-sm font-medium text-emerald-200 hover:bg-emerald-400/10">Review</Link><button disabled={pending} onClick={onAcknowledge} className="rounded-lg border border-slate-600 px-3 py-2 text-sm font-medium text-slate-200 hover:border-emerald-300 hover:text-white disabled:opacity-60">Acknowledge</button></div></article> }
function Metric({ label, value }: { label: string; value: string }): JSX.Element { return <div><dt className="text-xs text-slate-500">{label}</dt><dd className="mt-1 font-medium text-slate-200">{value}</dd></div> }
function State({ message, retry, error }: { message: string; retry?: () => void; error?: boolean }): JSX.Element { return <div className={`rounded-xl border p-5 text-sm ${error ? 'border-rose-300/30 bg-rose-300/5 text-rose-100' : 'border-slate-800 bg-slate-900/40 text-slate-400'}`}><p>{message}</p>{retry && <button onClick={retry} className="mt-3 rounded-lg border border-current px-3 py-1.5 font-medium">Try again</button>}</div> }
