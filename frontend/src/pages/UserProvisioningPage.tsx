import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Navigate } from 'react-router-dom'
import { AdminUsersError, adminUsersApi } from '../api/adminUsers'
import type { CreateAdminUser } from '../api/adminUsers'
import { useAuth } from '../auth/AuthProvider'

function errorMessage(error: unknown): string {
  if (!(error instanceof AdminUsersError)) return 'Unable to complete the request. Please try again.'
  if (error.code === 'EMAIL_ALREADY_REGISTERED') return 'That email is already registered.'
  if (error.code === 'SELF_DISABLE_NOT_ALLOWED') return 'You cannot disable your own account.'
  if (error.code === 'LAST_ACTIVE_ADMIN') return 'The final active ADMIN cannot be disabled.'
  if (error.status === 404) return 'The user no longer exists. Refresh the list.'
  if (error.status === 403) return 'You are not authorized to manage users.'
  return 'Unable to complete the request. Please try again.'
}

export function UserProvisioningPage(): JSX.Element {
  const { session } = useAuth()
  const client = useQueryClient()
  const [page, setPage] = useState(0)
  const [notice, setNotice] = useState<string | null>(null)
  const users = useQuery({ queryKey: ['admin-users', page], queryFn: () => adminUsersApi.list(page), retry: false })
  const create = useMutation({
    mutationFn: adminUsersApi.create,
    onSuccess: async () => { setPage(0); setNotice('User created successfully.'); await client.invalidateQueries({ queryKey: ['admin-users'] }) },
    onError: (error) => setNotice(errorMessage(error)),
  })
  const lifecycle = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) => adminUsersApi.setActive(id, active),
    onSuccess: async (user) => { setNotice(`${user.email} is now ${user.active ? 'enabled' : 'disabled'}.`); await client.invalidateQueries({ queryKey: ['admin-users'] }) },
    onError: (error) => setNotice(errorMessage(error)),
  })

  if (session?.role !== 'ADMIN') return <Navigate to="/" replace />

  function submit(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault(); setNotice(null)
    const form = event.currentTarget
    const values = new FormData(form)
    const request: CreateAdminUser = { email: String(values.get('email')), password: String(values.get('password')), role: String(values.get('role')) as CreateAdminUser['role'] }
    create.mutate(request, { onSuccess: () => form.reset() })
  }

  function changeActive(id: string, email: string, active: boolean): void {
    const explanation = active
      ? `Enable ${email}? Their preserved data and access will be restored.`
      : `Disable ${email}? Their data will be preserved. Existing access may remain usable for up to 15 minutes.`
    if (window.confirm(explanation)) lifecycle.mutate({ id, active })
  }

  return <section className="max-w-6xl">
    <p className="text-xs font-semibold uppercase tracking-[.18em] text-emerald-400">Administration</p>
    <h1 className="mt-2 text-3xl font-semibold text-white">User lifecycle</h1>
    <p className="mt-3 text-slate-400">Provision accounts and reversibly control access without deleting owned data.</p>
    {notice && <p role="status" className="mt-6 rounded-xl border border-slate-700 bg-slate-900 px-4 py-3 text-sm text-slate-200">{notice}</p>}

    <form className="mt-7 grid gap-4 rounded-2xl border border-slate-800 bg-slate-900/50 p-6 md:grid-cols-4 md:items-end" onSubmit={submit}>
      <label className="block text-sm">Email<input required name="email" type="email" className="mt-2 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2" /></label>
      <label className="block text-sm">Temporary password<input required minLength={8} name="password" type="password" className="mt-2 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2" /></label>
      <label className="block text-sm">Role<select name="role" defaultValue="INVESTOR" className="mt-2 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2"><option>INVESTOR</option><option>ADVISOR</option><option>ADMIN</option></select></label>
      <button disabled={create.isPending} className="rounded-lg bg-emerald-400 px-4 py-2 font-semibold text-slate-950 disabled:opacity-50">{create.isPending ? 'Creating…' : 'Create user'}</button>
    </form>

    <div className="mt-8 overflow-x-auto rounded-2xl border border-slate-800 bg-slate-900/50">
      {users.isLoading && <p className="p-6 text-slate-400">Loading users…</p>}
      {users.isError && <div className="p-6"><p className="text-rose-300">Unable to load users.</p><button onClick={() => void users.refetch()} className="mt-3 rounded-lg border border-slate-600 px-3 py-2">Retry</button></div>}
      {users.data && users.data.content.length === 0 && <p className="p-6 text-slate-400">No users found.</p>}
      {users.data && users.data.content.length > 0 && <table className="w-full min-w-[760px] text-left text-sm">
        <thead className="border-b border-slate-800 text-slate-400"><tr><th className="p-4">Email</th><th className="p-4">Role</th><th className="p-4">Status</th><th className="p-4">Created</th><th className="p-4">Action</th></tr></thead>
        <tbody>{users.data.content.map(user => {
          const self = user.email.toLowerCase() === session.email.toLowerCase()
          const pending = lifecycle.isPending && lifecycle.variables?.id === user.id
          return <tr key={user.id} className="border-b border-slate-800/70 last:border-0">
            <td className="p-4 text-white">{user.email}</td><td className="p-4">{user.role}</td>
            <td className="p-4"><span className={user.active ? 'text-emerald-300' : 'text-amber-300'}>{user.active ? 'Active' : 'Disabled'}</span></td>
            <td className="p-4 text-slate-400">{new Date(user.createdAt).toLocaleString()}</td>
            <td className="p-4"><button title={self && user.active ? 'You cannot disable your own account.' : undefined} disabled={pending || (self && user.active)} onClick={() => changeActive(user.id, user.email, !user.active)} className="rounded-lg border border-slate-600 px-3 py-2 disabled:cursor-not-allowed disabled:opacity-40">{pending ? 'Updating…' : user.active ? 'Disable' : 'Enable'}</button></td>
          </tr>
        })}</tbody>
      </table>}
      {users.data && <div className="flex items-center justify-between border-t border-slate-800 p-4 text-sm text-slate-400">
        <span>Page {users.data.number + 1} of {Math.max(users.data.totalPages, 1)} · {users.data.totalElements} users</span>
        <div className="flex gap-2"><button disabled={users.data.first} onClick={() => setPage(value => Math.max(0, value - 1))} className="rounded border border-slate-700 px-3 py-1 disabled:opacity-40">Previous</button><button disabled={users.data.last} onClick={() => setPage(value => value + 1)} className="rounded border border-slate-700 px-3 py-1 disabled:opacity-40">Next</button></div>
      </div>}
    </div>
  </section>
}
