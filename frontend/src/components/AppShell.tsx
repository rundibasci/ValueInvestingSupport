import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'

const navigation = [
  { label: 'Dashboard', to: '/' },
  { label: 'Screener', to: '/screener' },
  { label: 'Seed Universe', to: '/seed' },
  { label: 'Portfolio', to: '/portfolio' },
  { label: 'Watchlist', to: '/watchlist' },
]

const adminNavigation = [
  { label: 'Users', to: '/admin/users' },
]

export function AppShell(): JSX.Element {
  const { session, logout } = useAuth()
  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 lg:grid lg:grid-cols-[17rem_1fr]">
      <aside className="border-b border-slate-800 bg-slate-950 px-5 py-5 lg:min-h-screen lg:border-b-0 lg:border-r">
        <NavLink className="flex items-center gap-3" to="/">
          <span className="grid h-9 w-9 place-items-center rounded-lg bg-emerald-400 font-bold text-slate-950">V</span>
          <span>
            <span className="block text-sm font-semibold tracking-wide text-white">Value Investing</span>
            <span className="block text-xs text-slate-400">Decision support</span>
          </span>
        </NavLink>
        <nav aria-label="Primary navigation" className="mt-7 flex gap-1 overflow-x-auto lg:flex-col">
          {navigation.map((item) => (
            <NavLink
              className={({ isActive }) =>
                `rounded-lg px-3 py-2 text-sm font-medium transition ${
                  isActive ? 'bg-slate-800 text-emerald-300' : 'text-slate-300 hover:bg-slate-900 hover:text-white'
                }`
              }
              key={item.to}
              to={item.to}
              end={item.to === '/'}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        {session?.role === 'ADMIN' && (
          <nav aria-label="Admin navigation" className="mt-6 flex gap-1 overflow-x-auto border-t border-slate-800 pt-5 lg:flex-col">
            {adminNavigation.map((item) => (
              <NavLink
                className={({ isActive }) =>
                  `rounded-lg px-3 py-2 text-sm font-medium transition ${
                    isActive ? 'bg-slate-800 text-emerald-300' : 'text-slate-300 hover:bg-slate-900 hover:text-white'
                  }`
                }
                key={item.to}
                to={item.to}
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        )}
      </aside>
      <div className="min-w-0">
        <header className="flex min-h-16 items-center justify-between border-b border-slate-800 bg-slate-950/80 px-5 backdrop-blur lg:px-8">
          <div>
            <p className="text-xs font-medium uppercase tracking-[0.18em] text-emerald-400">Workspace</p>
            <p className="text-sm text-slate-400">Research before conviction.</p>
          </div>
          <div className="flex items-center gap-3"><span className="hidden text-right text-xs text-slate-400 sm:block"><span className="block text-slate-200">{session?.email}</span>{session?.role}</span><button onClick={() => void logout()} className="rounded-full border border-slate-700 px-3 py-1 text-xs font-medium text-slate-300 hover:border-emerald-400 hover:text-white">Sign out</button></div>
        </header>
        <main className="mx-auto w-full max-w-7xl px-5 py-8 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
