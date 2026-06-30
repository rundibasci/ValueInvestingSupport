import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { authFetch } from '../api/client'
import { setAccessToken, setUnauthorizedHandler } from './token'

type Role = 'ADMIN' | 'ADVISOR' | 'INVESTOR'
type Session = { email: string; role: Role }
type AuthContextValue = {
  session: Session | null
  ready: boolean
  message: string | null
  login: (email: string, password: string) => Promise<void>
  completeOAuthLogin: (accessToken: string) => void
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

function readSession(token: string): Session | null {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))) as { sub?: string; role?: Role }
    return payload.sub && payload.role ? { email: payload.sub, role: payload.role } : null
  } catch {
    return null
  }
}

async function refreshToken(): Promise<string | null> {
  const response = await authFetch('/auth/refresh', { method: 'POST' })
  if (!response.ok) return null
  const data = (await response.json()) as { accessToken: string }
  return data.accessToken
}

export function AuthProvider({ children }: { children: React.ReactNode }): JSX.Element {
  const [session, setSession] = useState<Session | null>(null)
  const [ready, setReady] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const refreshInFlight = useRef<Promise<boolean> | null>(null)

  const clear = useCallback((notice: string | null = null) => {
    setAccessToken(null)
    setSession(null)
    setMessage(notice)
  }, [])

  const refresh = useCallback(async (): Promise<boolean> => {
    if (refreshInFlight.current) return refreshInFlight.current
    refreshInFlight.current = refreshToken().then((token) => {
      if (!token) { clear('Your session has expired. Please sign in again.'); return false }
      const nextSession = readSession(token)
      if (!nextSession) { clear('Your session could not be restored. Please sign in again.'); return false }
      setAccessToken(token)
      setSession(nextSession)
      return true
    }).finally(() => { refreshInFlight.current = null })
    return refreshInFlight.current
  }, [clear])

  useEffect(() => {
    setUnauthorizedHandler(refresh)
    void refresh().finally(() => setReady(true))
    return () => setUnauthorizedHandler(null)
  }, [refresh])

  const login = useCallback(async (email: string, password: string) => {
    const response = await authFetch('/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }),
    })
    if (!response.ok) throw new Error(response.status === 401 ? 'Invalid email or password.' : 'Unable to sign in right now.')
    const data = (await response.json()) as { accessToken: string }
    const nextSession = readSession(data.accessToken)
    if (!nextSession) throw new Error('The server returned an invalid session.')
    setAccessToken(data.accessToken)
    setSession(nextSession)
    setMessage(null)
  }, [])

  const completeOAuthLogin = useCallback((token: string) => {
    const nextSession = readSession(token)
    if (!nextSession) throw new Error('The server returned an invalid session.')
    setAccessToken(token)
    setSession(nextSession)
    setMessage(null)
  }, [])

  const logout = useCallback(async () => {
    await authFetch('/auth/logout', { method: 'POST' })
    clear()
  }, [clear])

  const value = useMemo(
    () => ({ session, ready, message, login, completeOAuthLogin, logout }),
    [session, ready, message, login, completeOAuthLogin, logout],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider')
  return context
}
