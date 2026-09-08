import { useRef, useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'

const navigation = [
  { label: 'Dashboard', to: '/' },
  { label: 'Screener', to: '/screener' },
  { label: 'Seed Universe', to: '/seed' },
  { label: 'Portfolio', to: '/portfolio' },
  { label: 'Watchlist', to: '/watchlist' },
  { label: 'Decision History', to: '/audit' },
  { label: 'Checklists', to: '/checklists' },
  { label: 'Account', to: '/account' },
]

const adminNavigation = [
  { label: 'Universe Curation', to: '/universe-curation' },
  { label: 'Jobs', to: '/admin/jobs' },
  { label: 'AI Thesis Review', to: '/admin/thesis-review' },
  { label: 'Data Fallbacks', to: '/admin/fallbacks' },
  { label: 'Users', to: '/admin/users' },
]

export function AppShell(): JSX.Element {
  const { session, logout } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)
  const menuButton = useRef<HTMLButtonElement>(null)
  function closeMenu(): void {
    setMenuOpen(false)
    if (window.matchMedia('(max-width: 1023px)').matches) menuButton.current?.focus()
  }
  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 lg:grid lg:grid-cols-[17rem_1fr]">
      <aside onKeyDown={(event) => {
        if (event.key === 'Escape' && menuOpen) {
          event.preventDefault()
          closeMenu()
        }
      }} className="border-b border-slate-800 bg-slate-950 px-5 py-5 lg:min-h-screen lg:border-b-0 lg:border-r">
        <div className="flex items-center justify-between gap-3">
        <NavLink onClick={() => setMenuOpen(false)} className="flex items-center gap-3" to="/">
          <span className="grid h-9 w-9 place-items-center rounded-lg bg-emerald-400 font-bold text-slate-950">V</span>
          <span>
            <span className="block text-sm font-semibold tracking-wide text-white">Value Investing</span>
            <span className="block text-xs text-slate-400">Decision support</span>
          </span>
        </NavLink>
        <button
          ref={menuButton}
          type="button"
          aria-expanded={menuOpen}
          aria-controls="workspace-navigation"
          aria-label={menuOpen ? 'Close navigation menu' : 'Open navigation menu'}
          onClick={() => setMenuOpen((open) => !open)}
          className="flex min-h-11 min-w-11 items-center justify-center gap-2 rounded-lg border border-slate-700 px-3 text-sm font-medium text-slate-100 hover:bg-slate-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-400 lg:hidden"
        >
          <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
            <path d={menuOpen ? 'M6 6l12 12M6 18L18 6' : 'M4 6h16M4 12h16M4 18h16'} />
          </svg>
          <span>{menuOpen ? 'Close' : 'Menu'}</span>
        </button>
        </div>
        <div id="workspace-navigation" className={`${menuOpen ? 'block' : 'hidden'} lg:block`}>
        <nav aria-label="Primary navigation" className="mt-5 flex flex-col gap-1 lg:mt-7">
          {navigation.map((item) => (
            <NavLink
              className={({ isActive }) =>
                `flex min-h-11 items-center rounded-lg px-3 py-2 text-sm font-medium transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-emerald-400 ${
                  isActive ? 'bg-slate-800 text-emerald-300' : 'text-slate-300 hover:bg-slate-900 hover:text-white'
                }`
              }
              key={item.to}
              onClick={closeMenu}
              to={item.to}
              end={item.to === '/'}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        {session?.role === 'ADMIN' && (
          <nav aria-label="Admin navigation" className="mt-6 flex flex-col gap-1 border-t border-slate-800 pt-5">
            {adminNavigation.map((item) => (
              <NavLink
                className={({ isActive }) =>
                  `flex min-h-11 items-center rounded-lg px-3 py-2 text-sm font-medium transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-emerald-400 ${
                    isActive ? 'bg-slate-800 text-emerald-300' : 'text-slate-300 hover:bg-slate-900 hover:text-white'
                  }`
                }
                key={item.to}
                onClick={closeMenu}
                to={item.to}
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        )}
        <div className="mt-5 space-y-3 border-t border-slate-800 pt-5 lg:hidden">
          <div>
            <p className="text-xs font-medium uppercase tracking-[0.18em] text-emerald-400">Workspace</p>
            <p className="mt-1 text-sm text-slate-400">Research before conviction.</p>
          </div>
          <p className="text-xs text-slate-400"><span className="block break-words text-sm text-slate-200">{session?.email}</span>{session?.role}</p>
          <button type="button" onClick={() => void logout()} className="flex min-h-11 w-full items-center justify-center rounded-lg border border-slate-700 px-3 py-2 text-sm font-medium text-slate-200 hover:border-emerald-400 hover:text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-400">Sign out</button>
        </div>
        </div>
      </aside>
      <div className="min-w-0">
        <header className="hidden min-h-16 items-center justify-between border-b border-slate-800 bg-slate-950/80 px-5 backdrop-blur lg:flex lg:px-8">
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
