import { getAccessToken, refreshAfterUnauthorized } from '../auth/token'

const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()
export const apiBaseUrl = (configuredBaseUrl || 'http://localhost:8080').replace(/\/$/, '')

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const request = async (): Promise<Response> => {
    const headers = new Headers(init.headers)
    const token = getAccessToken()

    if (token) headers.set('Authorization', `Bearer ${token}`)

    return fetch(`${apiBaseUrl}${path.startsWith('/') ? path : `/${path}`}`, {
      ...init,
      headers,
      credentials: 'include',
    })
  }

  const response = await request()
  if (response.status !== 401 || !getAccessToken() || !(await refreshAfterUnauthorized())) return response
  return request()
}

export async function authFetch(path: string, init: RequestInit = {}): Promise<Response> {
  return fetch(`${apiBaseUrl}${path}`, { ...init, credentials: 'include' })
}
