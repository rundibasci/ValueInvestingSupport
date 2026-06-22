import { useEffect, useState, useRef } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchScreener } from '../api/screener'
import type { ScreenerRequest } from '../api/screener'
import { ScreenerPresetsBar } from '../components/ScreenerPresetsBar'
import { ScreenerFilterPanel } from '../components/ScreenerFilterPanel'
import { ScreenerTable } from '../components/ScreenerTable'

const DEFAULT_FILTERS: ScreenerRequest = {
  sector: null,
  exchange: null,
  minMarginOfSafety: null,
  maxMarginOfSafety: null,
  minValueScore: null,
  minRoic: null,
  maxDebtToEquity: null,
  minDividendYield: null,
  sortField: 'totalScore',
  sortDirection: 'DESC',
  page: 0,
  pageSize: 20,
}

export function ScreenerPage(): JSX.Element {
  const [searchParams, setSearchParams] = useSearchParams()

  const parseParams = (): ScreenerRequest => {
    const getNum = (key: string): number | null => {
      const val = searchParams.get(key)
      return val !== null && val !== '' ? parseFloat(val) : null
    }

    return {
      sector: searchParams.get('sector') || null,
      exchange: searchParams.get('exchange') || null,
      minMarginOfSafety: getNum('minMarginOfSafety'),
      maxMarginOfSafety: getNum('maxMarginOfSafety'),
      minValueScore: getNum('minValueScore'),
      minRoic: getNum('minRoic'),
      maxDebtToEquity: getNum('maxDebtToEquity'),
      minDividendYield: getNum('minDividendYield'),
      sortField: searchParams.get('sortBy') || 'totalScore',
      sortDirection: (searchParams.get('sortDirection') as 'ASC' | 'DESC') || 'DESC',
      page: parseInt(searchParams.get('page') || '0', 10),
      pageSize: parseInt(searchParams.get('pageSize') || '20', 10),
    }
  }

  const [localFilters, setLocalFilters] = useState<ScreenerRequest>(parseParams)
  const [activePresetName, setActivePresetName] = useState<string | null>(searchParams.get('preset') || null)

  useEffect(() => {
    setLocalFilters(parseParams())
    setActivePresetName(searchParams.get('preset') || null)
  }, [searchParams])

  const debounceTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const updateSearchParams = (newFilters: ScreenerRequest, presetName: string | null) => {
    const params = new URLSearchParams()

    if (newFilters.sector) params.set('sector', newFilters.sector)
    if (newFilters.exchange) params.set('exchange', newFilters.exchange)
    if (newFilters.minMarginOfSafety !== null && newFilters.minMarginOfSafety !== undefined) {
      params.set('minMarginOfSafety', newFilters.minMarginOfSafety.toString())
    }
    if (newFilters.maxMarginOfSafety !== null && newFilters.maxMarginOfSafety !== undefined) {
      params.set('maxMarginOfSafety', newFilters.maxMarginOfSafety.toString())
    }
    if (newFilters.minValueScore !== null && newFilters.minValueScore !== undefined) {
      params.set('minValueScore', newFilters.minValueScore.toString())
    }
    if (newFilters.minRoic !== null && newFilters.minRoic !== undefined) {
      params.set('minRoic', newFilters.minRoic.toString())
    }
    if (newFilters.maxDebtToEquity !== null && newFilters.maxDebtToEquity !== undefined) {
      params.set('maxDebtToEquity', newFilters.maxDebtToEquity.toString())
    }
    if (newFilters.minDividendYield !== null && newFilters.minDividendYield !== undefined) {
      params.set('minDividendYield', newFilters.minDividendYield.toString())
    }
    if (newFilters.sortField) params.set('sortBy', newFilters.sortField)
    if (newFilters.sortDirection) params.set('sortDirection', newFilters.sortDirection)
    if (newFilters.page !== undefined && newFilters.page !== null) params.set('page', newFilters.page.toString())
    if (newFilters.pageSize) params.set('pageSize', newFilters.pageSize.toString())
    if (presetName) params.set('preset', presetName)

    setSearchParams(params)
  }

  const handleFilterChange = (updatedFields: Partial<ScreenerRequest>) => {
    const nextFilters = {
      ...localFilters,
      ...updatedFields,
      page: 0,
    }

    setLocalFilters(nextFilters)
    setActivePresetName(null)

    if (debounceTimeoutRef.current) {
      clearTimeout(debounceTimeoutRef.current)
    }

    debounceTimeoutRef.current = setTimeout(() => {
      updateSearchParams(nextFilters, null)
    }, 300)
  }

  const handleSelectPreset = (presetName: string, presetFilters: ScreenerRequest) => {
    const nextFilters = {
      ...DEFAULT_FILTERS,
      ...presetFilters,
      page: 0,
    }
    setLocalFilters(nextFilters)
    setActivePresetName(presetName)
    updateSearchParams(nextFilters, presetName)
  }

  const handleResetFilters = () => {
    setLocalFilters(DEFAULT_FILTERS)
    setActivePresetName(null)
    updateSearchParams(DEFAULT_FILTERS, null)
  }

  const handleSort = (field: string) => {
    let direction: 'ASC' | 'DESC' = 'DESC'
    if (localFilters.sortField === field && localFilters.sortDirection === 'DESC') {
      direction = 'ASC'
    }
    const nextFilters = {
      ...localFilters,
      sortField: field,
      sortDirection: direction,
      page: 0,
    }
    setLocalFilters(nextFilters)
    updateSearchParams(nextFilters, activePresetName)
  }

  const handleChangePage = (newPage: number) => {
    const nextFilters = {
      ...localFilters,
      page: newPage,
    }
    setLocalFilters(nextFilters)
    updateSearchParams(nextFilters, activePresetName)
  }

  const handleChangePageSize = (newPageSize: number) => {
    const nextFilters = {
      ...localFilters,
      pageSize: newPageSize,
      page: 0,
    }
    setLocalFilters(nextFilters)
    updateSearchParams(nextFilters, activePresetName)
  }

  const queryFilters = parseParams()
  const { data, error, isLoading } = useQuery({
    queryKey: ['screenerResults', queryFilters],
    queryFn: () => fetchScreener(queryFilters),
  })

  useEffect(() => {
    return () => {
      if (debounceTimeoutRef.current) {
        clearTimeout(debounceTimeoutRef.current)
      }
    }
  }, [])

  return (
    <div className="space-y-6">
      <div>
        <p className="text-xs font-semibold uppercase tracking-[0.2em] text-emerald-400">Discover</p>
        <h1 className="mt-1 text-3xl font-bold tracking-tight text-white sm:text-4xl">Screener</h1>
        <p className="mt-2 text-sm text-slate-400 max-w-xl">
          Search for companies that combine financial quality with a meaningful margin of safety.
        </p>
      </div>

      <ScreenerPresetsBar onSelectPreset={handleSelectPreset} activePresetName={activePresetName} />

      <ScreenerFilterPanel
        filters={localFilters}
        onChangeFilters={handleFilterChange}
        onResetFilters={handleResetFilters}
      />

      {error ? (
        <div className="rounded-xl border border-rose-900 bg-rose-950/20 p-4 text-sm text-rose-400">
          <p className="font-semibold">Unable to fetch screener results</p>
          <p className="mt-1 text-xs text-rose-500">{(error as Error).message || 'Server error'}</p>
        </div>
      ) : isLoading ? (
        <div className="space-y-4">
          <div className="animate-pulse h-12 w-full bg-slate-900 rounded-xl" />
          <div className="animate-pulse h-64 w-full bg-slate-900/60 rounded-xl" />
        </div>
      ) : (
        <ScreenerTable
          data={data}
          sortBy={localFilters.sortField || null}
          sortDirection={localFilters.sortDirection || null}
          onSort={handleSort}
          page={localFilters.page ?? 0}
          pageSize={localFilters.pageSize ?? 20}
          onChangePage={handleChangePage}
          onChangePageSize={handleChangePageSize}
        />
      )}

      {/* MiFID II Disclaimer Footer */}
      <footer className="border-t border-slate-905 pt-6 mt-12 text-center text-xs text-slate-500 max-w-3xl mx-auto leading-relaxed">
        <p>
          <strong>Disclaimer:</strong> This tool is provided solely for decision-support and educational purposes and does not constitute investment advice under MiFID II or any other regulatory framework. All financial data is compiled from third-party sources and is subject to change. Investors should verify all data points and consult with a licensed professional before making investment decisions.
        </p>
      </footer>
    </div>
  )
}
