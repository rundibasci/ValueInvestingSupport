import { getAccessToken } from '../auth/token'

const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()
export const apiBaseUrl = (configuredBaseUrl || 'http://localhost:8080').replace(/\/$/, '')

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers)
  const token = getAccessToken()

  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  return fetch(`${apiBaseUrl}${path.startsWith('/') ? path : `/${path}`}`, {
    ...init,
    headers,
  })
}
