import { useQuery } from '@tanstack/react-query'
import { fetchScreenerPresets } from '../api/screener'
import type { ScreenerRequest } from '../api/screener'

interface ScreenerPresetsBarProps {
  onSelectPreset: (presetName: string, preset: ScreenerRequest) => void
  activePresetName: string | null
}

export function ScreenerPresetsBar({ onSelectPreset, activePresetName }: ScreenerPresetsBarProps): JSX.Element {
  const { data: presets, isLoading, error } = useQuery<Record<string, ScreenerRequest>>({
    queryKey: ['screenerPresets'],
    queryFn: fetchScreenerPresets,
  })

  if (isLoading) {
    return <div className="animate-pulse h-10 w-full bg-slate-900 rounded-lg"></div>
  }

  if (error || !presets) {
    return <div className="text-xs text-red-400">Failed to load presets</div>
  }

  const presetList = [
    { key: 'graham', label: 'Graham Number' },
    { key: 'dividend', label: 'Dividend' },
    { key: 'quality', label: 'Quality' },
  ]

  return (
    <div className="flex flex-wrap items-center gap-3">
      <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">Presets:</span>
      {presetList.map((preset) => {
        const isActive = activePresetName === preset.key
        return (
          <button
            key={preset.key}
            onClick={() => {
              const presetData = presets[preset.key]
              if (presetData) {
                onSelectPreset(preset.key, presetData)
              }
            }}
            className={`rounded-lg px-4 py-2 text-xs font-semibold transition border focus:outline-none focus:ring-2 focus:ring-emerald-500 ${
              isActive
                ? 'bg-emerald-500 border-emerald-500 text-slate-950 font-bold'
                : 'bg-slate-900 border-slate-800 text-slate-300 hover:bg-slate-800 hover:text-white'
            }`}
          >
            {preset.label}
          </button>
        )
      })}
    </div>
  )
}
