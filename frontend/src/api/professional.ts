import { apiFetch } from './client'

export type ResearchSnapshot = {
  id: string
  symbol: string
  actionType: string
  capturedAt: string
  currentPrice: number | null
  compositeFairValue: number | null
  marginOfSafety: number | null
  valueScore: number | null
  waccUsed: number | null
  dataSource: string | null
  piotroskiScore: number | null
  moatClassification: string | null
  rationale: string | null
}

export type ChecklistCriterion = {
  id?: string
  label: string
  criterionType: string
  metricKey: string | null
  operator: string | null
  threshold: number | null
  displayOrder?: number
}

export type Checklist = {
  id: string
  name: string
  description: string | null
  criteria: ChecklistCriterion[]
  createdAt: string
  updatedAt: string
}

export type ChecklistRequest = {
  name: string
  description: string | null
  criteria: Array<Omit<ChecklistCriterion, 'id' | 'displayOrder'>>
}

export type ChecklistEvaluation = {
  id: string
  checklistId: string
  symbol: string
  evaluatedAt: string
  items: Array<{ label: string; status: string; actualValue: number | null; message: string | null }>
}

export type Confidence = {
  symbol: string
  overallLevel: string
  factors: Array<{ name: string; level: string; message: string }>
}

export type Verification = {
  symbol: string
  flags: Array<{ field: string; severity: string; message: string }>
}

export type CompetencePreferences = {
  preferredSectors: string[]
  competenceIndustries: string[]
  updatedAt: string | null
}

export type AdvisorAcknowledgement = {
  acknowledged: boolean
  acknowledgedAt: string | null
  sessionKey: string | null
  disclaimer: string
}

async function json<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init)
  if (!response.ok) {
    const body = await response.json().catch(() => null) as { message?: string; detail?: string; error?: string } | null
    throw new Error(body?.message || body?.detail || body?.error || `Request failed (${response.status}).`)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

const body = (value: unknown, method = 'POST'): RequestInit => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(value),
})

export const professionalApi = {
  decisions: (filters: { symbol?: string; from?: string; to?: string } = {}) => {
    const params = new URLSearchParams()
    if (filters.symbol) params.set('symbol', filters.symbol)
    if (filters.from) params.set('from', filters.from)
    if (filters.to) params.set('to', filters.to)
    const suffix = params.toString()
    return json<ResearchSnapshot[]>(`/api/v1/audit/decisions${suffix ? `?${suffix}` : ''}`)
  },
  checklists: () => json<Checklist[]>('/api/v1/checklists'),
  createChecklist: (request: ChecklistRequest) => json<Checklist>('/api/v1/checklists', body(request)),
  updateChecklist: (id: string, request: ChecklistRequest) => json<Checklist>(`/api/v1/checklists/${id}`, body(request, 'PUT')),
  deleteChecklist: async (id: string) => {
    const response = await apiFetch(`/api/v1/checklists/${id}`, { method: 'DELETE' })
    if (!response.ok) throw new Error(`Request failed (${response.status}).`)
  },
  evaluateChecklist: (id: string, symbol: string) =>
    json<ChecklistEvaluation>(`/api/v1/checklists/${id}/evaluate/${encodeURIComponent(symbol)}`, { method: 'POST' }),
  confidence: (symbol: string) => json<Confidence>(`/api/v1/professional/valuation-confidence/${encodeURIComponent(symbol)}`),
  verification: (symbol: string) => json<Verification>(`/api/v1/professional/data-verification/${encodeURIComponent(symbol)}`),
  competence: () => json<CompetencePreferences>('/api/v1/preferences/competence'),
  updateCompetence: (request: { preferredSectors: string[]; competenceIndustries: string[] }) =>
    json<CompetencePreferences>('/api/v1/preferences/competence', body(request, 'PUT')),
  advisorAcknowledgement: () => json<AdvisorAcknowledgement>('/api/v1/advisor/acknowledgement'),
  acknowledgeAdvisor: () => json<AdvisorAcknowledgement>('/api/v1/advisor/acknowledgement', body({ acknowledged: true }, 'PUT')),
}
