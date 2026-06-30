import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { apiBaseUrl, authFetch } from '../api/client'
import { useAuth } from '../auth/AuthProvider'

const OAUTH_RETURN_PATH_KEY = 'vis.oauth.returnPath'

function consumeReturnPath(): string {
  const fallback = '/'
  const value = window.sessionStorage.getItem(OAUTH_RETURN_PATH_KEY)
  window.sessionStorage.removeItem(OAUTH_RETURN_PATH_KEY)
  if (!value || !value.startsWith('/') || value.startsWith('//')) return fallback
  return value
}

export function OAuthCallbackPage(): JSX.Element {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { completeOAuthLogin } = useAuth()
  const [error, setError] = useState<string | null>(null)

  const providerError = useMemo(() => {
    const code = searchParams.get('error')
    if (!code) return null
    const description = searchParams.get('error_description')
    if (code === 'access_denied') return 'Google sign-in was cancelled or consent was denied.'
    return description || 'Google sign-in could not be completed.'
  }, [searchParams])

  useEffect(() => {
    if (providerError) {
      setError(providerError)
      return
    }

    const code = searchParams.get('code')
    if (!code) {
      setError('Google sign-in did not return a valid session handoff code.')
      return
    }
    const handoffCode = code

    let cancelled = false
    async function exchangeCode(): Promise<void> {
      try {
        const response = await authFetch(`/auth/oauth2/token?code=${encodeURIComponent(handoffCode)}`)
        if (!response.ok) {
          throw new Error(response.status === 401
            ? 'Google sign-in expired. Please try again.'
            : 'Google sign-in is unavailable right now.')
        }
        const data = (await response.json()) as { accessToken: string }
        if (cancelled) return
        completeOAuthLogin(data.accessToken)
        navigate(consumeReturnPath(), { replace: true })
      } catch (reason) {
        if (!cancelled) setError(reason instanceof Error ? reason.message : 'Google sign-in failed.')
      }
    }

    void exchangeCode()
    return () => {
      cancelled = true
    }
  }, [completeOAuthLogin, navigate, providerError, searchParams])

  return (
    <main className="grid min-h-screen place-items-center bg-slate-950 px-5 text-slate-100">
      <section className="w-full max-w-md rounded-2xl border border-slate-800 bg-slate-900 p-8 shadow-2xl">
        <p className="text-sm font-medium text-emerald-300">Google sign-in</p>
        <h1 className="mt-2 text-2xl font-semibold text-white">{error ? 'Sign-in needs attention' : 'Completing sign-in'}</h1>
        <p className="mt-3 text-sm leading-6 text-slate-300">
          {error ?? 'Exchanging the secure handoff with the Value Investing session service.'}
        </p>
        {error && (
          <div className="mt-6 flex gap-3">
            <Link
              className="rounded-lg bg-emerald-400 px-4 py-2 text-sm font-semibold text-slate-950 hover:bg-emerald-300"
              to="/login"
              replace
            >
              Back to sign in
            </Link>
            <a
              className="rounded-lg border border-slate-700 px-4 py-2 text-sm font-semibold text-slate-200 hover:border-emerald-400"
              href={`${apiBaseUrl}/oauth2/authorization/google`}
            >
              Try Google again
            </a>
          </div>
        )}
      </section>
    </main>
  )
}

export function rememberOAuthReturnPath(path: string): void {
  window.sessionStorage.setItem(OAUTH_RETURN_PATH_KEY, path)
}
