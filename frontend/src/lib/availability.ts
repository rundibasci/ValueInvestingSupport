export type AvailabilityStatus =
  | 'AVAILABLE'
  | 'STALE'
  | 'PENDING'
  | 'PROVIDER_LIMITED'
  | 'MISSING_SEEDED_HISTORY'
  | 'MISSING_INTERNAL_COMPUTATION'
  | 'GUARDRAIL_BLOCKED'

export type AvailabilitySeverity = 'ok' | 'warn' | 'blocked' | 'missing'

export const availabilityStatuses: AvailabilityStatus[] = [
  'AVAILABLE',
  'STALE',
  'PENDING',
  'PROVIDER_LIMITED',
  'MISSING_SEEDED_HISTORY',
  'MISSING_INTERNAL_COMPUTATION',
  'GUARDRAIL_BLOCKED',
]

export function availabilityLabel(status: string | null | undefined): string {
  return (status || 'MISSING_INTERNAL_COMPUTATION').replace(/_/g, ' ').toLowerCase()
}

export function availabilitySeverity(status: string | null | undefined): AvailabilitySeverity {
  if (status === 'AVAILABLE') return 'ok'
  if (status === 'STALE' || status === 'PENDING' || status === 'GUARDRAIL_BLOCKED') return 'warn'
  if (status === 'PROVIDER_LIMITED') return 'blocked'
  return 'missing'
}

export function availabilityClass(status: string | null | undefined): string {
  const severity = availabilitySeverity(status)
  if (severity === 'ok') return 'bg-emerald-300/15 text-emerald-100'
  if (severity === 'warn') return 'bg-amber-300/15 text-amber-100'
  if (severity === 'blocked') return 'bg-rose-400/15 text-rose-100'
  return 'bg-slate-700 text-slate-200'
}
