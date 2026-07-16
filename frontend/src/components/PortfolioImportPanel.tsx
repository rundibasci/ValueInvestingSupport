import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import type { Portfolio } from "../api/portfolio";
import { portfolioImportApi, type ImportMode, type PortfolioImportPreview, type SecuritySearchResult } from "../api/portfolioImport";

const fieldClass = "mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-emerald-400";
const buttonClass = "rounded-lg bg-emerald-400 px-4 py-2 text-sm font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-50";
const formatNumber = (value: number | null | undefined, currency?: string | null): string => value == null ? "—" : new Intl.NumberFormat("en-US", currency ? { style: "currency", currency } : { maximumFractionDigits: 6 }).format(value);
const statusTone = (status: string): string => status === "INVALID" ? "border-rose-300/30 bg-rose-400/10 text-rose-100" : status === "NEEDS_MAPPING" || status === "WARNING" ? "border-amber-300/30 bg-amber-300/10 text-amber-100" : "border-emerald-300/30 bg-emerald-400/10 text-emerald-100";

function MappingControl({ rowId, isin, onMap }: { rowId: string; isin: string | null; onMap: (rowId: string, result: SecuritySearchResult) => void }): JSX.Element {
  const [query, setQuery] = useState("");
  const search = useQuery({
    queryKey: ["security-search", query],
    queryFn: () => portfolioImportApi.searchSecurities(query.trim()),
    enabled: query.trim().length >= 2,
    staleTime: 30_000,
  });
  return <div className="mt-2 min-w-64">
    <label className="text-xs text-slate-300">Find an existing security for ISIN {isin}
      <input value={query} onChange={(event) => setQuery(event.target.value)} className={fieldClass} placeholder="Symbol or company" />
    </label>
    {search.isFetching && <p role="status" className="mt-2 text-xs text-slate-400">Searching…</p>}
    {search.isError && <p role="alert" className="mt-2 text-xs text-rose-200">Security search failed.</p>}
    <div className="mt-2 space-y-1">
      {search.data?.map((result) => <button key={result.id} type="button" onClick={() => onMap(rowId, result)} className="block w-full rounded border border-slate-700 p-2 text-left text-xs text-slate-200 hover:border-emerald-400">
        <strong className="text-emerald-200">{result.symbol}</strong> · {result.companyName}<span className="block text-slate-500">{[result.exchange, result.sector].filter(Boolean).join(" · ") || "Profile context unavailable"}</span>
      </button>)}
    </div>
  </div>;
}

function PreviewTable({ preview, skipped, mappings, setSkipped, setMapping }: {
  preview: PortfolioImportPreview;
  skipped: Set<string>;
  mappings: Map<string, SecuritySearchResult>;
  setSkipped: (rowId: string) => void;
  setMapping: (rowId: string, result: SecuritySearchResult) => void;
}): JSX.Element {
  return <div className="mt-5 overflow-x-auto">
    <table className="w-full min-w-[76rem] text-left text-xs">
      <thead className="uppercase text-slate-400"><tr><th className="p-2">Row</th><th>Product / ISIN</th><th>Quantity</th><th>Source last</th><th>Native value</th><th>{preview.baseCurrency} value</th><th>Resolution</th><th>Status</th><th>Action</th></tr></thead>
      <tbody className="divide-y divide-slate-800">{preview.rows.map((row) => {
        const mapped = mappings.get(row.rowId);
        const canSkip = row.status === "INVALID" || row.status === "NEEDS_MAPPING";
        return <tr key={row.rowId} className={skipped.has(row.rowId) ? "opacity-60" : ""}>
          <td className="p-2 text-slate-500">{row.rowNumber}</td>
          <td className="max-w-xs py-2"><span className="block font-medium text-white">{row.productName}</span><span className="text-slate-400">{row.isin || "Cash balance"}</span>{row.warning && <span className="block text-amber-200">{row.warning}</span>}{row.error && <span role="alert" className="block text-rose-200">{row.error}</span>}</td>
          <td>{formatNumber(row.quantity)}</td><td>{formatNumber(row.sourceLastPrice, row.nativeCurrency)}</td><td>{formatNumber(row.nativeValue, row.nativeCurrency)}</td><td>{formatNumber(row.baseValue, preview.baseCurrency)}</td>
          <td>{mapped ? <span><strong className="text-emerald-200">{mapped.symbol}</strong><button type="button" onClick={() => setMapping(row.rowId, mapped)} className="ml-2 text-rose-200 underline">clear</button></span> : row.resolvedSymbol || (row.classification === "CASH" ? `${row.nativeCurrency} cash` : "Unresolved")}{row.status === "NEEDS_MAPPING" && !mapped && !skipped.has(row.rowId) && <MappingControl rowId={row.rowId} isin={row.isin} onMap={setMapping} />}</td>
          <td><span className={`inline-flex rounded-full border px-2 py-1 font-semibold ${statusTone(row.status)}`}>{skipped.has(row.rowId) ? "SKIPPED" : mapped ? "MAPPED" : row.status.replaceAll("_", " ")}</span></td>
          <td>{canSkip && <button type="button" onClick={() => setSkipped(row.rowId)} className="font-semibold text-amber-200 underline">{skipped.has(row.rowId) ? "Include" : "Skip"}</button>}</td>
        </tr>;
      })}</tbody>
    </table>
  </div>;
}

export function PortfolioImportPanel({ portfolios, activeId, onCommitted }: { portfolios: Portfolio[]; activeId: string | null; onCommitted: (portfolioId: string) => void }): JSX.Element {
  const client = useQueryClient();
  const [open, setOpen] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [target, setTarget] = useState(activeId || "new");
  const [newName, setNewName] = useState("");
  const [baseCurrency, setBaseCurrency] = useState("EUR");
  const [mode, setMode] = useState<ImportMode>("MERGE");
  const [preview, setPreview] = useState<PortfolioImportPreview | null>(null);
  const [skipped, setSkipped] = useState<Set<string>>(new Set());
  const [mappings, setMappings] = useState<Map<string, SecuritySearchResult>>(new Map());
  const [replaceConfirmed, setReplaceConfirmed] = useState(false);
  const [historyPage, setHistoryPage] = useState(0);
  const history = useQuery({ queryKey: ["portfolio-imports", activeId, historyPage], queryFn: () => portfolioImportApi.history(activeId || undefined, historyPage), enabled: open });
  const previewMutation = useMutation({
    mutationFn: () => portfolioImportApi.preview({ file: file!, portfolioId: target === "new" ? undefined : target, baseCurrency, mode }),
    onSuccess: (value) => { setPreview(value); setSkipped(new Set()); setMappings(new Map()); setReplaceConfirmed(false); void client.invalidateQueries({ queryKey: ["portfolio-imports"] }); },
  });
  const commit = useMutation({
    mutationFn: () => portfolioImportApi.commit(preview!.importId, { newPortfolioName: target === "new" ? newName.trim() : undefined, replaceConfirmed, skippedRowIds: [...skipped], mappings: [...mappings].map(([rowId, security]) => ({ rowId, securityId: security.id })) }),
    onSuccess: async (result) => { await client.invalidateQueries({ queryKey: ["portfolios"] }); await client.invalidateQueries({ queryKey: ["portfolio"] }); await client.invalidateQueries({ queryKey: ["portfolio-imports"] }); onCommitted(result.portfolioId); },
  });
  const mappedOrReady = useMemo(() => preview?.rows.every((row) => skipped.has(row.rowId) || row.status === "READY" || row.status === "CASH" || (row.status === "WARNING" && Boolean(row.resolvedSecurityId)) || mappings.has(row.rowId)) ?? false, [preview, skipped, mappings]);
  const expired = preview ? new Date(preview.expiresAt).getTime() <= Date.now() : false;
  const alreadyCommitted = preview?.status === "COMMITTED";
  const toggleSkip = (rowId: string): void => setSkipped((current) => { const next = new Set(current); next.has(rowId) ? next.delete(rowId) : next.add(rowId); return next; });
  const mapRow = (rowId: string, result: SecuritySearchResult): void => setMappings((current) => { const next = new Map(current); if (next.get(rowId)?.id === result.id) next.delete(rowId); else next.set(rowId, result); return next; });
  const download = async (importId: string): Promise<void> => { const blob = await portfolioImportApi.report(importId); const url = URL.createObjectURL(blob); const anchor = document.createElement("a"); anchor.href = url; anchor.download = `portfolio-import-${importId}.csv`; anchor.click(); URL.revokeObjectURL(url); };

  if (!open) return <section className="rounded-2xl border border-slate-800 bg-slate-900/50 p-5"><div className="flex flex-wrap items-center justify-between gap-3"><div><h2 className="text-xl font-semibold text-white">Import an existing portfolio</h2><p className="mt-1 text-sm text-slate-400">Preview and reconcile broker CSV positions before anything changes.</p></div><button type="button" onClick={() => { setOpen(true); setTarget(activeId || "new"); }} className={buttonClass}>Import CSV</button></div></section>;

  return <section className="rounded-2xl border border-emerald-400/30 bg-slate-900/70 p-5 sm:p-6" aria-labelledby="portfolio-import-title">
    <div className="flex justify-between gap-4"><div><p className="text-xs font-semibold uppercase tracking-widest text-emerald-300">Portfolio import</p><h2 id="portfolio-import-title" className="mt-2 text-xl font-semibold text-white">{preview ? "Review normalized rows" : "Upload and preview"}</h2></div><button type="button" onClick={() => setOpen(false)} className="text-sm text-slate-300 underline">Close</button></div>
    {!preview && <form className="mt-5 grid gap-4 md:grid-cols-2" onSubmit={(event) => { event.preventDefault(); if (file && (target !== "new" || newName.trim())) previewMutation.mutate(); }}>
      <div className="md:col-span-2 rounded-xl border border-dashed border-slate-600 p-4" onDragOver={(event) => event.preventDefault()} onDrop={(event) => { event.preventDefault(); setFile(event.dataTransfer.files[0] || null); }}>
        <label className="text-sm font-medium text-slate-200">Broker CSV file<input type="file" accept=".csv,text/csv" required onChange={(event) => setFile(event.target.files?.[0] || null)} className="mt-2 block w-full text-sm text-slate-300" /></label><p className="mt-2 text-xs text-slate-500">Drop a file here or use the picker. Maximum 1 MB and 1,000 rows.</p>{file && <p role="status" className="mt-2 text-sm text-emerald-200">Selected: {file.name} ({Math.ceil(file.size / 1024)} KB)</p>}
      </div>
      <label className="text-sm text-slate-200">Target portfolio<select value={target} onChange={(event) => setTarget(event.target.value)} className={fieldClass}><option value="new">Create a new portfolio</option>{portfolios.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
      {target === "new" && <label className="text-sm text-slate-200">New portfolio name<input required value={newName} onChange={(event) => setNewName(event.target.value)} className={fieldClass} /></label>}
      <label className="text-sm text-slate-200">Base currency<input value={baseCurrency} maxLength={3} onChange={(event) => setBaseCurrency(event.target.value.toUpperCase())} className={fieldClass} /></label>
      <label className="text-sm text-slate-200">Import mode<select value={mode} onChange={(event) => setMode(event.target.value as ImportMode)} className={fieldClass}><option value="MERGE">MERGE — synchronize imported positions</option><option value="REPLACE">REPLACE — remove current positions first</option></select></label>
      <div className="md:col-span-2 rounded-lg bg-slate-950/60 p-4 text-xs leading-5 text-slate-400"><code className="text-slate-200">Prodotto,Codice,Quantità,Ultimo,Valore,,Valore in EUR</code><p className="mt-2">For this export, column 5 contains currency and the unnamed column 6 contains native value. Quoted decimal commas are supported. Preview does not modify a portfolio.</p></div>
      {mode === "REPLACE" && <p role="alert" className="md:col-span-2 rounded-lg border border-rose-300/30 bg-rose-400/10 p-3 text-sm text-rose-100">REPLACE is destructive and requires another confirmation after preview.</p>}
      <button disabled={!file || previewMutation.isPending || (target === "new" && !newName.trim())} className={`${buttonClass} md:col-span-2`}>{previewMutation.isPending ? "Parsing and validating…" : "Preview import"}</button>
      {previewMutation.error instanceof Error && <p role="alert" className="md:col-span-2 text-sm text-rose-200">{previewMutation.error.message}</p>}
    </form>}
    {preview && <div className="mt-5">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4"><div className="rounded-lg bg-slate-950/60 p-3"><span className="text-xs text-slate-500">Rows</span><strong className="block text-white">{preview.sourceRowCount}</strong></div><div className="rounded-lg bg-slate-950/60 p-3"><span className="text-xs text-slate-500">Base total</span><strong className="block text-white">{formatNumber(preview.baseValueTotal, preview.baseCurrency)}</strong></div><div className="rounded-lg bg-slate-950/60 p-3"><span className="text-xs text-slate-500">Warnings / errors</span><strong className="block text-white">{preview.warningCount} / {preview.errorCount}</strong></div><div className="rounded-lg bg-slate-950/60 p-3"><span className="text-xs text-slate-500">Expires</span><strong className="block text-white">{new Date(preview.expiresAt).toLocaleString()}</strong></div></div>
      <p className="mt-3 text-xs text-slate-400">Native totals: {Object.entries(preview.nativeValueTotals).map(([currency, value]) => `${formatNumber(value, currency)}`).join(" · ")} · Checksum {preview.checksum.slice(0, 12)}…</p>
      <PreviewTable preview={preview} skipped={skipped} mappings={mappings} setSkipped={toggleSkip} setMapping={mapRow} />
      <div className="mt-5 rounded-xl border border-slate-700 bg-slate-950/60 p-4"><h3 className="font-semibold text-white">Confirm {preview.mode}</h3><p className="mt-2 text-sm text-slate-300">{preview.mode === "MERGE" ? "Imported positions will be synchronized. Replaying this file will not add quantities again." : "Current holdings and cash in the target portfolio will be atomically replaced."}</p><p className="mt-2 text-xs text-slate-400">{mappings.size} explicit mappings · {skipped.size} skipped rows. Source prices and values are reconciliation evidence, not cost basis or live market data.</p>
        {!preview.portfolioId && !alreadyCommitted && <label className="mt-3 block text-sm text-slate-200">New portfolio name<input required value={newName} onChange={(event) => setNewName(event.target.value)} className={fieldClass} /></label>}
        {preview.mode === "REPLACE" && <label className="mt-3 flex gap-2 text-sm text-rose-100"><input type="checkbox" checked={replaceConfirmed} onChange={(event) => setReplaceConfirmed(event.target.checked)} />I confirm replacement of {portfolios.find((item) => item.id === target)?.name || newName || "the target portfolio"}.</label>}
        <div className="mt-4 flex flex-wrap gap-3">{!alreadyCommitted && <button type="button" onClick={() => commit.mutate()} disabled={!mappedOrReady || expired || commit.isPending || (!preview.portfolioId && !newName.trim()) || (preview.mode === "REPLACE" && !replaceConfirmed)} className={buttonClass}>{commit.isPending ? "Committing…" : `Commit ${preview.mode}`}</button>}<button type="button" onClick={() => { setPreview(null); commit.reset(); }} className="rounded-lg border border-slate-600 px-4 py-2 text-sm text-slate-200">Start again</button>{alreadyCommitted && <button type="button" onClick={() => void download(preview.importId)} className={buttonClass}>Download reconciliation</button>}</div>
        {!alreadyCommitted && !mappedOrReady && <p role="alert" className="mt-3 text-sm text-amber-200">Map or explicitly skip every unresolved/invalid row before commit.</p>}{!alreadyCommitted && expired && <p role="alert" className="mt-3 text-sm text-rose-200">This preview expired. Upload the file again.</p>}{commit.error instanceof Error && <p role="alert" className="mt-3 text-sm text-rose-200">{commit.error.message}</p>}{commit.data && <div role="status" className="mt-3 rounded-lg border border-emerald-300/30 bg-emerald-400/10 p-3 text-sm text-emerald-100">Committed {commit.data.committedHoldingRows} security rows and {commit.data.committedCashRows} cash rows. {commit.data.skippedRows} skipped. <button type="button" onClick={() => void download(commit.data.importId)} className="ml-2 font-semibold underline">Download reconciliation</button><span className="ml-2">Full analysis becomes available in FI3.</span></div>}
      </div>
    </div>}
    <div className="mt-6 border-t border-slate-800 pt-5"><h3 className="font-semibold text-white">Recent imports</h3>{history.isLoading && <p role="status" className="mt-2 text-sm text-slate-400">Loading import history…</p>}{history.isError && <p role="alert" className="mt-2 text-sm text-rose-200">Import history could not be loaded.</p>}<div className="mt-3 space-y-2">{history.data?.content.map((item) => <div key={item.importId} className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-800 p-3 text-sm"><div><strong className="text-white">{item.filename}</strong><span className="ml-2 text-slate-400">{item.mode} · {item.status} · {item.sourceRowCount} rows</span><span className="block text-xs text-slate-500">{item.portfolioName || "New portfolio preview"} · {new Date(item.createdAt).toLocaleString()} · {item.checksum.slice(0, 10)}…</span></div><div className="flex gap-3"><button type="button" onClick={() => void portfolioImportApi.detail(item.importId).then((value) => { setPreview(value); setTarget(value.portfolioId || "new"); setMappings(new Map()); setSkipped(new Set()); })} className="text-emerald-200 underline">Open</button><button type="button" onClick={() => void download(item.importId)} className="text-slate-200 underline">CSV report</button></div></div>)}</div>{(history.data?.totalPages || 0) > 1 && <div className="mt-3 flex gap-3"><button type="button" disabled={historyPage === 0} onClick={() => setHistoryPage((page) => page - 1)} className="text-sm text-slate-200 underline">Previous</button><button type="button" disabled={historyPage + 1 >= (history.data?.totalPages || 0)} onClick={() => setHistoryPage((page) => page + 1)} className="text-sm text-slate-200 underline">Next</button></div>}</div>
    <p className="mt-5 text-xs leading-5 text-slate-500">This imports user-owned model-portfolio records only. It does not place trades or provide personalized investment advice.</p>
  </section>;
}
