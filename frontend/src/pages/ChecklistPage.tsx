import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { professionalApi, type Checklist, type ChecklistEvaluation, type ChecklistRequest } from '../api/professional'

const inputClass = 'mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-emerald-400'
const metricOptions = ['marginOfSafety', 'valueScore', 'roic', 'debtToEquity', 'dividendYield', 'piotroskiScore', 'altmanScore']

function emptyRequest(): ChecklistRequest {
  return {
    name: '',
    description: '',
    criteria: [{ label: '', criterionType: 'QUANTITATIVE', metricKey: 'marginOfSafety', operator: '>=', threshold: 15 }],
  }
}

function statusClass(status: string): string {
  if (status === 'PASS') return 'bg-emerald-300/15 text-emerald-100'
  if (status === 'FAIL') return 'bg-rose-400/15 text-rose-100'
  return 'bg-amber-300/15 text-amber-100'
}

export function ChecklistPage(): JSX.Element {
  const client = useQueryClient()
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState<ChecklistRequest>(emptyRequest())
  const [symbol, setSymbol] = useState('')
  const [evaluation, setEvaluation] = useState<ChecklistEvaluation | null>(null)
  const checklists = useQuery({ queryKey: ['checklists'], queryFn: professionalApi.checklists })
  const active = useMemo(() => checklists.data?.find((item) => item.id === editingId) ?? checklists.data?.[0] ?? null, [checklists.data, editingId])
  const save = useMutation({
    mutationFn: () => editingId ? professionalApi.updateChecklist(editingId, form) : professionalApi.createChecklist(form),
    onSuccess: (saved) => {
      setEditingId(saved.id)
      setForm({ name: saved.name, description: saved.description, criteria: saved.criteria.map(({ label, criterionType, metricKey, operator, threshold }) => ({ label, criterionType, metricKey, operator, threshold })) })
      void client.invalidateQueries({ queryKey: ['checklists'] })
    },
  })
  const remove = useMutation({
    mutationFn: (id: string) => professionalApi.deleteChecklist(id),
    onSuccess: () => {
      setEditingId(null)
      setForm(emptyRequest())
      setEvaluation(null)
      void client.invalidateQueries({ queryKey: ['checklists'] })
    },
  })
  const evaluate = useMutation({
    mutationFn: () => professionalApi.evaluateChecklist(active!.id, symbol.trim().toUpperCase()),
    onSuccess: setEvaluation,
  })

  function load(checklist: Checklist): void {
    setEditingId(checklist.id)
    setForm({
      name: checklist.name,
      description: checklist.description,
      criteria: checklist.criteria.map(({ label, criterionType, metricKey, operator, threshold }) => ({ label, criterionType, metricKey, operator, threshold })),
    })
    setEvaluation(null)
  }

  function submit(event: FormEvent): void {
    event.preventDefault()
    save.mutate()
  }

  return (
    <section className="space-y-6">
      <div>
        <p className="text-sm font-medium text-emerald-300">Professional workflow</p>
        <h1 className="mt-2 text-3xl font-semibold text-white">Investment checklist</h1>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-400">Build repeatable research criteria and evaluate seeded companies against them.</p>
      </div>
      <div className="grid gap-6 lg:grid-cols-[18rem_1fr]">
        <aside className="rounded-xl border border-slate-800 bg-slate-900/60 p-4">
          <button type="button" onClick={() => { setEditingId(null); setForm(emptyRequest()); setEvaluation(null) }} className="w-full rounded-lg bg-emerald-400 px-4 py-2 text-sm font-semibold text-slate-950">New checklist</button>
          <div className="mt-4 space-y-2">
            {checklists.isLoading && <p className="text-sm text-slate-400">Loading checklists...</p>}
            {checklists.data?.map((checklist) => (
              <button key={checklist.id} type="button" onClick={() => load(checklist)} className={`w-full rounded-lg px-3 py-2 text-left text-sm ${editingId === checklist.id ? 'bg-slate-800 text-emerald-200' : 'text-slate-300 hover:bg-slate-800'}`}>
                <span className="block font-semibold">{checklist.name}</span>
                <span className="text-xs text-slate-500">{checklist.criteria.length} criteria</span>
              </button>
            ))}
          </div>
        </aside>
        <div className="space-y-6">
          <form onSubmit={submit} className="rounded-xl border border-slate-800 bg-slate-900/60 p-5">
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="text-sm font-medium text-slate-200">Name<input required value={form.name} onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))} className={inputClass} /></label>
              <label className="text-sm font-medium text-slate-200">Description<input value={form.description ?? ''} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value || null }))} className={inputClass} /></label>
            </div>
            <div className="mt-5 space-y-3">
              {form.criteria.map((criterion, index) => (
                <div key={index} className="grid gap-3 rounded-lg border border-slate-800 bg-slate-950/50 p-3 sm:grid-cols-[1fr_9rem_10rem_6rem_7rem_auto]">
                  <input required aria-label="Criterion label" value={criterion.label} onChange={(event) => setForm((current) => ({ ...current, criteria: current.criteria.map((item, i) => i === index ? { ...item, label: event.target.value } : item) }))} className={inputClass} placeholder="ROIC above hurdle" />
                  <select value={criterion.criterionType} onChange={(event) => setForm((current) => ({ ...current, criteria: current.criteria.map((item, i) => i === index ? { ...item, criterionType: event.target.value, metricKey: event.target.value === 'MANUAL' ? null : item.metricKey } : item) }))} className={inputClass}>
                    <option value="QUANTITATIVE">Auto</option>
                    <option value="MANUAL">Manual</option>
                  </select>
                  <select disabled={criterion.criterionType === 'MANUAL'} value={criterion.metricKey ?? ''} onChange={(event) => setForm((current) => ({ ...current, criteria: current.criteria.map((item, i) => i === index ? { ...item, metricKey: event.target.value || null } : item) }))} className={inputClass}>
                    {metricOptions.map((metric) => <option key={metric} value={metric}>{metric}</option>)}
                  </select>
                  <select disabled={criterion.criterionType === 'MANUAL'} value={criterion.operator ?? '>='} onChange={(event) => setForm((current) => ({ ...current, criteria: current.criteria.map((item, i) => i === index ? { ...item, operator: event.target.value } : item) }))} className={inputClass}>
                    <option value=">=">&gt;=</option><option value="<=">&lt;=</option><option value=">">&gt;</option><option value="<">&lt;</option><option value="=">=</option>
                  </select>
                  <input disabled={criterion.criterionType === 'MANUAL'} type="number" step="0.01" value={criterion.threshold ?? ''} onChange={(event) => setForm((current) => ({ ...current, criteria: current.criteria.map((item, i) => i === index ? { ...item, threshold: event.target.value === '' ? null : Number(event.target.value) } : item) }))} className={inputClass} placeholder="15" />
                  <button type="button" onClick={() => setForm((current) => ({ ...current, criteria: current.criteria.filter((_, i) => i !== index) }))} className="mt-1 rounded-lg border border-slate-700 px-3 py-2 text-sm text-slate-300 disabled:opacity-50" disabled={form.criteria.length === 1}>Remove</button>
                </div>
              ))}
              <button type="button" onClick={() => setForm((current) => ({ ...current, criteria: [...current.criteria, { label: '', criterionType: 'MANUAL', metricKey: null, operator: null, threshold: null }] }))} className="rounded-lg border border-emerald-400/30 px-4 py-2 text-sm font-semibold text-emerald-200">Add criterion</button>
            </div>
            <div className="mt-5 flex flex-wrap gap-3">
              <button disabled={save.isPending} className="rounded-lg bg-emerald-400 px-4 py-2 text-sm font-semibold text-slate-950">{save.isPending ? 'Saving...' : 'Save checklist'}</button>
              {editingId && <button type="button" disabled={remove.isPending} onClick={() => remove.mutate(editingId)} className="rounded-lg border border-rose-300/40 px-4 py-2 text-sm font-semibold text-rose-100">Delete</button>}
            </div>
            {(save.isError || remove.isError) && <p role="alert" className="mt-3 text-sm text-rose-200">Checklist changes could not be saved.</p>}
          </form>
          <section className="rounded-xl border border-slate-800 bg-slate-900/60 p-5">
            <h2 className="text-lg font-semibold text-white">Evaluate a symbol</h2>
            <div className="mt-4 flex flex-wrap items-end gap-3">
              <label className="text-sm font-medium text-slate-200">Symbol<input value={symbol} onChange={(event) => setSymbol(event.target.value)} className={inputClass} placeholder="KO" /></label>
              <button type="button" disabled={!active || !symbol.trim() || evaluate.isPending} onClick={() => evaluate.mutate()} className="rounded-lg bg-emerald-400 px-4 py-2 text-sm font-semibold text-slate-950 disabled:opacity-50">{evaluate.isPending ? 'Evaluating...' : 'Evaluate'}</button>
            </div>
            {evaluation && (
              <div className="mt-5 overflow-x-auto">
                <table className="w-full min-w-[40rem] text-left text-sm">
                  <thead className="text-xs uppercase text-slate-400"><tr><th className="py-2">Criterion</th><th>Status</th><th>Actual</th><th>Message</th></tr></thead>
                  <tbody className="divide-y divide-slate-800">{evaluation.items.map((item) => <tr key={item.label}><td className="py-3 text-white">{item.label}</td><td><span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${statusClass(item.status)}`}>{item.status.toLowerCase()}</span></td><td>{item.actualValue ?? '-'}</td><td className="text-slate-400">{item.message ?? '-'}</td></tr>)}</tbody>
                </table>
              </div>
            )}
            {evaluate.isError && <p role="alert" className="mt-3 text-sm text-rose-200">Checklist evaluation could not be completed.</p>}
          </section>
        </div>
      </div>
    </section>
  )
}
