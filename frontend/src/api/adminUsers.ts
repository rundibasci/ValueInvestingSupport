import { apiFetch } from './client'

export type AdminUser = { id: string; email: string; role: 'ADMIN' | 'ADVISOR' | 'INVESTOR'; active: boolean; createdAt: string }
export type UserPage = { content: AdminUser[]; number: number; size: number; totalElements: number; totalPages: number; first: boolean; last: boolean }
export type CreateAdminUser = { email: string; password: string; role: AdminUser['role'] }

export class AdminUsersError extends Error {
  constructor(readonly status: number, readonly code: string | undefined, message: string) { super(message) }
}

async function json<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init)
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { code?: string; message?: string; error?: string }
    throw new AdminUsersError(response.status, body.code, body.message || body.error || `Request failed (${response.status}).`)
  }
  return response.json() as Promise<T>
}

export const adminUsersApi = {
  list: (page: number, size = 20) => json<UserPage>(`/api/v1/admin/users?page=${page}&size=${size}`),
  create: (request: CreateAdminUser) => json<AdminUser>('/api/v1/admin/users', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request),
  }),
  setActive: (id: string, active: boolean) => json<AdminUser>(`/api/v1/admin/users/${id}/active`, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ active }),
  }),
}
