import { useNavigate } from 'react-router-dom'
import type { ScreenerResponse, ScreenerResultItem } from '../api/screener'

interface ScreenerTableProps {
  data: ScreenerResponse | undefined
  sortBy: string | null
  sortDirection: 'ASC' | 'DESC' | null
  onSort: (field: string) => void
  page: number
  pageSize: number
  onChangePage: (page: number) => void
  onChangePageSize: (pageSize: number) => void
}

export function ScreenerTable({
  data,
  sortBy,
  sortDirection,
  onSort,
  page,
  pageSize,
  onChangePage,
  onChangePageSize,
}: ScreenerTableProps): JSX.Element {
  const navigate = useNavigate()

  const handleRowClick = (symbol: string) => {
    navigate(`/securities/${symbol}`)
  }

  const formatCurrency = (val: number | null) => {
    if (val === null || val === undefined) return '-'
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(val)
  }

  const formatPercent = (val: number | null) => {
    if (val === null || val === undefined) return '-'
    return `${val > 0 ? '+' : ''}${val.toFixed(1)}%`
  }

  const formatScore = (val: number | null) => {
    if (val === null || val === undefined) return '-'
    return val.toFixed(0)
  }

  const getRecommendationBadge = (rec: string) => {
    const base = 'inline-flex items-center rounded-md px-2 py-1 text-xs font-semibold uppercase tracking-wider border '
    switch (rec) {
      case 'STRONG_BUY':
        return <span className={`${base} bg-emerald-950/80 text-emerald-300 border-emerald-800`}>Strong Buy</span>
      case 'QUALITY_VALUE':
        return <span className={`${base} bg-green-950/80 text-green-300 border-green-900`}>Quality Value</span>
      case 'FAIR_VALUE':
        return <span className={`${base} bg-amber-950/50 text-amber-300 border-amber-900/60`}>Fair Value</span>
      case 'OVERVALUED':
        return <span className={`${base} bg-rose-950/80 text-rose-300 border-rose-900`}>Overvalued</span>
      default:
        return <span className={`${base} bg-slate-800 text-slate-400 border-slate-700`}>{rec || 'Unknown'}</span>
    }
  }

  const columns = [
    { field: 'symbol', label: 'Ticker' },
    { field: 'companyName', label: 'Name' },
    { field: 'sector', label: 'Sector' },
    { field: 'exchange', label: 'Exchange' },
    { field: 'currentPrice', label: 'Price' },
    { field: 'compositeFairValue', label: 'Fair Value' },
    { field: 'marginOfSafety', label: 'MoS' },
    { field: 'totalScore', label: 'Total Score' },
    { field: 'recommendation', label: 'Recommendation' },
  ]

  const totalPages = data?.totalPages ?? 0
  const totalElements = data?.totalElements ?? 0

  const renderSortIndicator = (field: string) => {
    if (sortBy !== field) return null
    return sortDirection === 'ASC' ? ' ▲' : ' ▼'
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="overflow-x-auto rounded-xl border border-slate-800 bg-slate-900/20">
        <table className="w-full border-collapse text-left text-sm text-slate-300">
          <thead className="bg-slate-900/70 text-xs font-semibold uppercase tracking-wider text-slate-400 border-b border-slate-800">
            <tr>
              {columns.map((col) => (
                <th
                  key={col.field}
                  onClick={() => onSort(col.field)}
                  className="cursor-pointer select-none px-6 py-4 hover:bg-slate-800 hover:text-white transition"
                >
                  {col.label}
                  {renderSortIndicator(col.field)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800">
            {data?.results && data.results.length > 0 ? (
              data.results.map((item: ScreenerResultItem) => (
                <tr
                  key={item.symbol}
                  onClick={() => handleRowClick(item.symbol)}
                  className="cursor-pointer hover:bg-slate-900/60 transition"
                >
                  <td className="px-6 py-4 font-mono font-bold text-emerald-400">{item.symbol}</td>
                  <td className="px-6 py-4 font-medium text-white max-w-xs truncate">{item.companyName}</td>
                  <td className="px-6 py-4 text-slate-400">{item.sector}</td>
                  <td className="px-6 py-4 text-slate-400">{item.exchange}</td>
                  <td className="px-6 py-4 font-mono">{formatCurrency(item.currentPrice)}</td>
                  <td className="px-6 py-4 font-mono">{formatCurrency(item.compositeFairValue)}</td>
                  <td
                    className={`px-6 py-4 font-mono font-semibold ${
                      (item.marginOfSafety ?? 0) >= 15
                        ? 'text-emerald-400'
                        : (item.marginOfSafety ?? 0) >= 5
                        ? 'text-amber-400'
                        : 'text-rose-400'
                    }`}
                  >
                    {formatPercent(item.marginOfSafety)}
                  </td>
                  <td className="px-6 py-4 font-mono text-center font-semibold text-white">
                    {formatScore(item.totalScore)}
                  </td>
                  <td className="px-6 py-4">{getRecommendationBadge(item.recommendation)}</td>
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan={columns.length} className="px-6 py-12 text-center text-slate-500">
                  {data === undefined ? 'Loading...' : 'No companies found matching the current filters.'}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination Controls */}
      <div className="flex flex-wrap items-center justify-between gap-4 px-2">
        <div className="text-xs text-slate-400">
          Showing <span className="font-semibold text-slate-200">{data?.results.length ?? 0}</span> of{' '}
          <span className="font-semibold text-slate-200">{totalElements}</span> results
        </div>

        <div className="flex items-center gap-6">
          {/* Page Size Select */}
          <div className="flex items-center gap-2">
            <span className="text-xs text-slate-400">Page size:</span>
            <select
              value={pageSize}
              onChange={(e) => onChangePageSize(parseInt(e.target.value))}
              className="rounded-lg border border-slate-800 bg-slate-950 px-2 py-1 text-xs text-slate-200 focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
            >
              <option value={20}>20</option>
              <option value={50}>50</option>
              <option value={100}>100</option>
            </select>
          </div>

          {/* Prev/Next buttons */}
          <div className="flex items-center gap-2">
            <button
              onClick={() => onChangePage(page - 1)}
              disabled={page <= 0}
              className="rounded-lg border border-slate-800 bg-slate-900 px-3 py-1.5 text-xs font-medium text-slate-300 transition hover:bg-slate-800 hover:text-white disabled:pointer-events-none disabled:opacity-40"
            >
              Previous
            </button>
            <span className="text-xs text-slate-400">
              Page <span className="font-semibold text-slate-200">{totalPages > 0 ? page + 1 : 0}</span> of{' '}
              <span className="font-semibold text-slate-200">{totalPages}</span>
            </span>
            <button
              onClick={() => onChangePage(page + 1)}
              disabled={page >= totalPages - 1}
              className="rounded-lg border border-slate-800 bg-slate-900 px-3 py-1.5 text-xs font-medium text-slate-300 transition hover:bg-slate-800 hover:text-white disabled:pointer-events-none disabled:opacity-40"
            >
              Next
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
