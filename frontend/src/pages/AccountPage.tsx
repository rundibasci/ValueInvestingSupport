import { useEffect, useState } from 'react'
import { apiFetch } from '../api/client'
import { professionalApi } from '../api/professional'

type AccountResponse = {
  email: string
  role: string
  googleLinked: boolean
  localPasswordAvailable: boolean
}

export function AccountPage(): JSX.Element {
  const [account, setAccount] = useState<AccountResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [unlinking, setUnlinking] = useState(false)
  const [preferredSectors, setPreferredSectors] = useState('')
  const [competenceIndustries, setCompetenceIndustries] = useState('')
  const [savingCompetence, setSavingCompetence] = useState(false)

  async function loadAccount(): Promise<void> {
    setLoading(true)
    setError(null)
    const response = await apiFetch('/api/v1/account')
    if (!response.ok) {
      setError('Account settings could not be loaded.')
      setLoading(false)
      return
    }
    setAccount((await response.json()) as AccountResponse)
    const competence = await professionalApi.competence().catch(() => null)
    if (competence) {
      setPreferredSectors(competence.preferredSectors.join(', '))
      setCompetenceIndustries(competence.competenceIndustries.join(', '))
    }
    setLoading(false)
  }

  useEffect(() => {
    void loadAccount()
  }, [])

  async function unlinkGoogle(): Promise<void> {
    setUnlinking(true)
    setMessage(null)
    setError(null)
    const response = await apiFetch('/api/v1/account/oauth/google', { method: 'DELETE' })
    if (!response.ok) {
      const body = await response.json().catch(() => null) as { detail?: string; message?: string } | null
      setError(body?.detail ?? body?.message ?? 'Google cannot be unlinked from this account.')
      setUnlinking(false)
      return
    }
    setAccount((await response.json()) as AccountResponse)
    setMessage('Google sign-in has been unlinked from this platform account.')
    setUnlinking(false)
  }

  async function saveCompetence(): Promise<void> {
    setSavingCompetence(true)
    setMessage(null)
    setError(null)
    try {
      await professionalApi.updateCompetence({
        preferredSectors: preferredSectors.split(',').map((item) => item.trim()).filter(Boolean),
        competenceIndustries: competenceIndustries.split(',').map((item) => item.trim()).filter(Boolean),
      })
      setMessage('Circle of competence preferences have been updated.')
    } catch {
      setError('Circle of competence preferences could not be saved.')
    } finally {
      setSavingCompetence(false)
    }
  }

  return (
    <section className="space-y-6">
      <div>
        <p className="text-sm font-medium text-emerald-300">Account</p>
        <h1 className="mt-2 text-3xl font-semibold text-white">Sign-in settings</h1>
        <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-400">
          Manage platform sign-in methods while keeping research data, roles, and ownership unchanged.
        </p>
      </div>

      {(message || error) && (
        <div
          role="alert"
          className={`rounded-xl border px-4 py-3 text-sm ${
            error ? 'border-rose-300/30 bg-rose-300/10 text-rose-100' : 'border-emerald-300/30 bg-emerald-300/10 text-emerald-100'
          }`}
        >
          {error ?? message}
        </div>
      )}

      <div className="rounded-xl border border-slate-800 bg-slate-900 p-6">
        {loading && <p className="text-sm text-slate-400">Loading account settings...</p>}
        {!loading && account && (
          <div className="space-y-6">
            <dl className="grid gap-4 sm:grid-cols-2">
              <div>
                <dt className="text-xs font-semibold uppercase text-slate-500">Email</dt>
                <dd className="mt-1 text-sm text-slate-100">{account.email}</dd>
              </div>
              <div>
                <dt className="text-xs font-semibold uppercase text-slate-500">Role</dt>
                <dd className="mt-1 text-sm text-slate-100">{account.role}</dd>
              </div>
              <div>
                <dt className="text-xs font-semibold uppercase text-slate-500">Google sign-in</dt>
                <dd className="mt-1 text-sm text-slate-100">{account.googleLinked ? 'Linked' : 'Not linked'}</dd>
              </div>
              <div>
                <dt className="text-xs font-semibold uppercase text-slate-500">Password sign-in</dt>
                <dd className="mt-1 text-sm text-slate-100">{account.localPasswordAvailable ? 'Available' : 'Not configured'}</dd>
              </div>
            </dl>

            <div className="border-t border-slate-800 pt-5">
              <h2 className="text-lg font-semibold text-white">Google account link</h2>
              <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-400">
                Unlinking removes Google as a platform sign-in method. It does not sign you out of Google globally.
              </p>
              <button
                className="mt-4 rounded-lg border border-slate-700 px-4 py-2 text-sm font-semibold text-slate-200 transition hover:border-emerald-400 disabled:cursor-not-allowed disabled:opacity-50"
                disabled={!account.googleLinked || !account.localPasswordAvailable || unlinking}
                onClick={() => void unlinkGoogle()}
              >
                {unlinking ? 'Unlinking...' : 'Unlink Google'}
              </button>
              {account.googleLinked && !account.localPasswordAvailable && (
                <p className="mt-3 text-sm text-amber-200">
                  Google cannot be unlinked because no local password is available for this account.
                </p>
              )}
            </div>

            <div className="border-t border-slate-800 pt-5">
              <h2 className="text-lg font-semibold text-white">Circle of competence</h2>
              <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-400">
                Mark sectors and industries where you have enough business understanding to prioritize research.
              </p>
              <div className="mt-4 grid gap-4 sm:grid-cols-2">
                <label className="text-sm font-medium text-slate-200">
                  Preferred sectors
                  <input value={preferredSectors} onChange={(event) => setPreferredSectors(event.target.value)} className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-emerald-400" placeholder="Consumer Defensive, Healthcare" />
                </label>
                <label className="text-sm font-medium text-slate-200">
                  Competence industries
                  <input value={competenceIndustries} onChange={(event) => setCompetenceIndustries(event.target.value)} className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-emerald-400" placeholder="Beverages, Household Products" />
                </label>
              </div>
              <button
                className="mt-4 rounded-lg bg-emerald-400 px-4 py-2 text-sm font-semibold text-slate-950 disabled:opacity-50"
                disabled={savingCompetence}
                onClick={() => void saveCompetence()}
              >
                {savingCompetence ? 'Saving...' : 'Save competence'}
              </button>
            </div>
          </div>
        )}
      </div>
    </section>
  )
}
