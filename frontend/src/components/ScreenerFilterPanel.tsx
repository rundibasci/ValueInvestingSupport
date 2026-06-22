import { useQuery } from '@tanstack/react-query'
import { fetchSectors, fetchExchanges } from '../api/screener'
import type { ScreenerRequest } from '../api/screener'

interface ScreenerFilterPanelProps {
  filters: ScreenerRequest
  onChangeFilters: (filters: Partial<ScreenerRequest>) => void
  onResetFilters: () => void
}

export function ScreenerFilterPanel({
  filters,
  onChangeFilters,
  onResetFilters,
}: ScreenerFilterPanelProps): JSX.Element {
  const { data: sectors = [] } = useQuery<string[]>({
    queryKey: ['screenerSectors'],
    queryFn: fetchSectors,
  })

  const { data: exchanges = [] } = useQuery<string[]>({
    queryKey: ['screenerExchanges'],
    queryFn: fetchExchanges,
  })

  const handleSliderChange = (name: keyof ScreenerRequest, value: string) => {
    const numVal = value === '' ? null : parseFloat(value)
    onChangeFilters({ [name]: numVal })
  }

  const handleSelectChange = (name: keyof ScreenerRequest, value: string) => {
    onChangeFilters({ [name]: value === '' ? null : value })
  }

  return (
    <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-6">
      <div className="flex items-center justify-between border-b border-slate-800 pb-4 mb-6">
        <h2 className="text-sm font-semibold tracking-wide text-white uppercase">Filters</h2>
        <button
          onClick={onResetFilters}
          className="text-xs font-medium text-emerald-400 hover:text-emerald-300 transition focus:outline-none focus:underline"
        >
          Reset Filters
        </button>
      </div>

      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
        {/* Sector */}
        <div>
          <label htmlFor="sector" className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">
            Sector
          </label>
          <select
            id="sector"
            value={filters.sector || ''}
            onChange={(e) => handleSelectChange('sector', e.target.value)}
            className="w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
          >
            <option value="">All Sectors</option>
            {sectors.map((sector) => (
              <option key={sector} value={sector}>
                {sector}
              </option>
            ))}
          </select>
        </div>

        {/* Exchange */}
        <div>
          <label htmlFor="exchange" className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">
            Exchange
          </label>
          <select
            id="exchange"
            value={filters.exchange || ''}
            onChange={(e) => handleSelectChange('exchange', e.target.value)}
            className="w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-200 focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
          >
            <option value="">All Exchanges</option>
            {exchanges.map((exchange) => (
              <option key={exchange} value={exchange}>
                {exchange}
              </option>
            ))}
          </select>
        </div>

        {/* Value Score */}
        <div>
          <div className="flex justify-between text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">
            <span>Min Value Score</span>
            <span className="text-emerald-400 font-mono">{filters.minValueScore ?? 0}</span>
          </div>
          <input
            type="range"
            min="0"
            max="100"
            step="1"
            value={filters.minValueScore ?? 0}
            onChange={(e) => handleSliderChange('minValueScore', e.target.value)}
            className="w-full accent-emerald-500 h-1.5 bg-slate-800 rounded-lg cursor-pointer focus:outline-none focus:ring-2 focus:ring-emerald-500"
          />
        </div>

        {/* Min Margin of Safety */}
        <div>
          <div className="flex justify-between text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">
            <span>Min Margin of Safety</span>
            <span className="text-emerald-400 font-mono">{filters.minMarginOfSafety ?? -100}%</span>
          </div>
          <input
            type="range"
            min="-100"
            max="100"
            step="1"
            value={filters.minMarginOfSafety ?? -100}
            onChange={(e) => handleSliderChange('minMarginOfSafety', e.target.value)}
            className="w-full accent-emerald-500 h-1.5 bg-slate-800 rounded-lg cursor-pointer focus:outline-none focus:ring-2 focus:ring-emerald-500"
          />
        </div>

        {/* Max Margin of Safety */}
        <div>
          <div className="flex justify-between text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">
            <span>Max Margin of Safety</span>
            <span className="text-emerald-400 font-mono">{filters.maxMarginOfSafety ?? 100}%</span>
          </div>
          <input
            type="range"
            min="-100"
            max="100"
            step="1"
            value={filters.maxMarginOfSafety ?? 100}
            onChange={(e) => handleSliderChange('maxMarginOfSafety', e.target.value)}
            className="w-full accent-emerald-500 h-1.5 bg-slate-800 rounded-lg cursor-pointer focus:outline-none focus:ring-2 focus:ring-emerald-500"
          />
        </div>

        {/* Min ROIC */}
        <div>
          <div className="flex justify-between text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">
            <span>Min ROIC</span>
            <span className="text-emerald-400 font-mono">{filters.minRoic ?? 0}%</span>
          </div>
          <input
            type="range"
            min="0"
            max="100"
            step="1"
            value={filters.minRoic ?? 0}
            onChange={(e) => handleSliderChange('minRoic', e.target.value)}
            className="w-full accent-emerald-500 h-1.5 bg-slate-800 rounded-lg cursor-pointer focus:outline-none focus:ring-2 focus:ring-emerald-500"
          />
        </div>

        {/* Min Dividend Yield */}
        <div>
          <div className="flex justify-between text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">
            <span>Min Dividend Yield</span>
            <span className="text-emerald-400 font-mono">{filters.minDividendYield ?? 0}%</span>
          </div>
          <input
            type="range"
            min="0"
            max="50"
            step="0.5"
            value={filters.minDividendYield ?? 0}
            onChange={(e) => handleSliderChange('minDividendYield', e.target.value)}
            className="w-full accent-emerald-500 h-1.5 bg-slate-800 rounded-lg cursor-pointer focus:outline-none focus:ring-2 focus:ring-emerald-500"
          />
        </div>

        {/* Max Debt-to-Equity */}
        <div>
          <div className="flex justify-between text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">
            <span>Max Debt-to-Equity</span>
            <span className="text-emerald-400 font-mono">{filters.maxDebtToEquity ?? 10.0}</span>
          </div>
          <input
            type="range"
            min="0"
            max="10"
            step="0.1"
            value={filters.maxDebtToEquity ?? 10.0}
            onChange={(e) => handleSliderChange('maxDebtToEquity', e.target.value)}
            className="w-full accent-emerald-500 h-1.5 bg-slate-800 rounded-lg cursor-pointer focus:outline-none focus:ring-2 focus:ring-emerald-500"
          />
        </div>
      </div>
    </div>
  )
}
