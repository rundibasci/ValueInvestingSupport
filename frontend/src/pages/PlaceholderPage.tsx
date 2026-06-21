type PlaceholderPageProps = {
  eyebrow: string
  title: string
  description: string
  nextPhase: string
}

export function PlaceholderPage({ eyebrow, title, description, nextPhase }: PlaceholderPageProps): JSX.Element {
  return (
    <section className="max-w-3xl">
      <p className="text-sm font-semibold uppercase tracking-[0.2em] text-emerald-400">{eyebrow}</p>
      <h1 className="mt-3 text-3xl font-semibold tracking-tight text-white sm:text-4xl">{title}</h1>
      <p className="mt-4 max-w-2xl text-base leading-7 text-slate-300">{description}</p>
      <div className="mt-8 rounded-xl border border-slate-800 bg-slate-900/70 p-6">
        <p className="text-sm font-medium text-slate-200">Ready for the next increment</p>
        <p className="mt-2 text-sm leading-6 text-slate-400">{nextPhase}</p>
      </div>
    </section>
  )
}
