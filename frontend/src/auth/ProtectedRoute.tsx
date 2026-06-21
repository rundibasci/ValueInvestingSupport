import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthProvider'

export function ProtectedRoute(): JSX.Element {
  const { session, ready } = useAuth()
  const location = useLocation()
  if (!ready) return <div className="grid min-h-screen place-items-center bg-slate-950 text-sm text-slate-300">Restoring your research workspace…</div>
  if (!session) return <Navigate to="/login" replace state={{ from: location }} />
  return <Outlet />
}
