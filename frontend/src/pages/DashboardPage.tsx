import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { portfolioApi, type Holding, type Portfolio } from "../api/portfolio";
import { watchlistApi, type Alert } from "../api/watchlist";

const disclaimer =
  "Decision-support only, not investment advice (MiFID II). Review the underlying data before making any investment decision.";

function money(value: number | null | undefined, currency = "USD"): string {
  return value == null
    ? "-"
    : new Intl.NumberFormat("en-US", {
        style: "currency",
        currency,
        maximumFractionDigits: 0,
        notation: Math.abs(value) >= 1_000_000 ? "compact" : "standard",
      }).format(value);
}

function number(value: number | null | undefined): string {
  return value == null
    ? "-"
    : new Intl.NumberFormat("en-US", { maximumFractionDigits: 1 }).format(
        value,
      );
}

function percent(value: number | null | undefined): string {
  return value == null ? "-" : `${number(value)}%`;
}

function date(value: string | null | undefined): string {
  return value
    ? new Intl.DateTimeFormat("en-US", { dateStyle: "medium" }).format(
        new Date(value),
      )
    : "-";
}

function alertType(type: string): string {
  return type
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function holdingReturn(holding: Holding): number | null {
  if (
    holding.currentPrice == null ||
    holding.averageCostBasis == null ||
    holding.averageCostBasis <= 0
  ) {
    return null;
  }
  return (
    ((holding.currentPrice - holding.averageCostBasis) /
      holding.averageCostBasis) *
    100
  );
}

function hasDecisionContext(holdings: Holding[], alerts: Alert[]): boolean {
  return (
    holdings.some(
      (holding) =>
        holding.marginOfSafety != null ||
        holding.compositeFairValue != null ||
        holding.recommendation != null,
    ) || alerts.length > 0
  );
}

export function DashboardPage(): JSX.Element {
  const [selectedPortfolioId, setSelectedPortfolioId] = useState<string | null>(
    null,
  );
  const portfolios = useQuery({
    queryKey: ["portfolios"],
    queryFn: portfolioApi.list,
  });
  const activePortfolioId =
    selectedPortfolioId ?? portfolios.data?.[0]?.id ?? null;
  const portfolio = useQuery({
    queryKey: ["portfolio", activePortfolioId],
    queryFn: () => portfolioApi.detail(activePortfolioId!),
    enabled: Boolean(activePortfolioId),
  });
  const alerts = useQuery({
    queryKey: ["watchlist", "alerts"],
    queryFn: watchlistApi.alerts,
  });

  const holdings = portfolio.data?.holdings ?? [];
  const pricedHoldings = holdings.filter(
    (holding) => holding.currentValue != null,
  );
  const movers = useMemo(
    () =>
      holdings
        .map((holding) => ({ holding, returnPercent: holdingReturn(holding) }))
        .filter(
          (
            item,
          ): item is { holding: Holding; returnPercent: number } =>
            item.returnPercent != null,
        )
        .sort(
          (left, right) =>
            Math.abs(right.returnPercent) - Math.abs(left.returnPercent),
        )
        .slice(0, 5),
    [holdings],
  );
  const highPriorityAlerts =
    alerts.data?.filter((alert) => alert.priority === "HIGH").length ?? 0;
  const activeAlerts = alerts.data ?? [];
  const positiveMosCount = holdings.filter(
    (holding) => (holding.marginOfSafety ?? -1) >= 15,
  ).length;
  const dataCoverage =
    holdings.length === 0
      ? null
      : (pricedHoldings.length / holdings.length) * 100;

  return (
    <div className="space-y-6">
      <section className="rounded-2xl border border-slate-800 bg-slate-900/60 p-6 sm:p-8">
        <div className="flex flex-col justify-between gap-5 lg:flex-row lg:items-end">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[.22em] text-emerald-300">
              Monitor
            </p>
            <h1 className="mt-3 text-3xl font-semibold text-white sm:text-4xl">
              Dashboard
            </h1>
            <p className="mt-3 max-w-3xl leading-7 text-slate-300">
              Track portfolio condition, active alerts, margin of safety, and
              the data gaps that need review before decisions are made.
            </p>
          </div>
          <label className="min-w-[16rem] text-sm font-medium text-slate-200">
            Portfolio scope
            <select
              value={activePortfolioId ?? ""}
              onChange={(event) => setSelectedPortfolioId(event.target.value)}
              className="mt-2 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-emerald-400"
              disabled={!portfolios.data?.length}
            >
              {portfolios.data?.length ? (
                portfolios.data.map((item: Portfolio) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))
              ) : (
                <option value="">No portfolios</option>
              )}
            </select>
          </label>
        </div>
      </section>

      {portfolios.isLoading ? (
        <State message="Loading dashboard data..." />
      ) : portfolios.isError ? (
        <State
          message="Dashboard data could not be loaded."
          retry={() => void portfolios.refetch()}
          error
        />
      ) : !portfolios.data?.length ? (
        <EmptyPortfolio />
      ) : (
        <>
          <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <MetricCard
              label="Portfolio value"
              value={money(portfolio.data?.totalValue)}
              detail={`${pricedHoldings.length}/${holdings.length} holdings priced`}
            />
            <MetricCard
              label="Weighted margin of safety"
              value={percent(portfolio.data?.weightedMoS)}
              detail={`${positiveMosCount} holdings at or above 15%`}
            />
            <MetricCard
              label="Yield"
              value="-"
              detail="Portfolio yield is not exposed by the current API."
            />
            <MetricCard
              label="Active alerts"
              value={String(activeAlerts.length)}
              detail={`${highPriorityAlerts} high priority`}
              tone={highPriorityAlerts > 0 ? "warning" : "default"}
            />
          </section>

          <section className="grid gap-6 xl:grid-cols-[minmax(0,1.2fr)_minmax(20rem,.8fr)]">
            <Panel
              title="Top movers"
              eyebrow="Cost basis return"
              action={<LinkButton to="/portfolio">Open portfolio</LinkButton>}
            >
              {portfolio.isLoading ? (
                <State message="Loading holdings..." />
              ) : portfolio.isError ? (
                <State
                  message="Portfolio detail could not be loaded."
                  retry={() => void portfolio.refetch()}
                  error
                />
              ) : movers.length ? (
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[38rem] text-left text-sm">
                    <thead className="border-b border-slate-800 text-xs uppercase tracking-wide text-slate-500">
                      <tr>
                        <th className="pb-3">Holding</th>
                        <th className="pb-3">Value</th>
                        <th className="pb-3">Current price</th>
                        <th className="pb-3">Return</th>
                        <th className="pb-3">MoS</th>
                        <th className="pb-3">Recommendation</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800">
                      {movers.map(({ holding, returnPercent }) => (
                        <tr key={holding.id}>
                          <td className="py-3">
                            <Link
                              className="font-semibold text-emerald-300 hover:text-emerald-200"
                              to={`/securities/${encodeURIComponent(holding.symbol)}`}
                            >
                              {holding.symbol}
                            </Link>
                          </td>
                          <td>
                            {money(
                              holding.currentValue,
                              holding.currency ?? "USD",
                            )}
                          </td>
                          <td>
                            {money(
                              holding.currentPrice,
                              holding.currency ?? "USD",
                            )}
                          </td>
                          <td>
                            <span
                              className={
                                returnPercent >= 0
                                  ? "font-semibold text-emerald-200"
                                  : "font-semibold text-rose-200"
                              }
                            >
                              {percent(returnPercent)}
                            </span>
                          </td>
                          <td>{percent(holding.marginOfSafety)}</td>
                          <td>{holding.recommendation ?? "-"}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <State message="Add cost basis and priced holdings to see movers." />
              )}
            </Panel>

            <Panel
              title="Alert pressure"
              eyebrow="Monitoring"
              action={<LinkButton to="/watchlist">Open watchlist</LinkButton>}
            >
              {alerts.isLoading ? (
                <State message="Loading alerts..." />
              ) : alerts.isError ? (
                <State
                  message="Active alerts could not be loaded."
                  retry={() => void alerts.refetch()}
                  error
                />
              ) : activeAlerts.length ? (
                <div className="space-y-3">
                  {activeAlerts.slice(0, 5).map((alert) => (
                    <article
                      key={alert.id}
                      className="rounded-xl border border-slate-800 bg-slate-950/50 p-4"
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <Link
                            className="font-semibold text-white hover:text-emerald-300"
                            to={`/securities/${encodeURIComponent(alert.symbol)}`}
                          >
                            {alert.symbol}
                          </Link>
                          <p className="mt-1 text-sm text-slate-400">
                            {alertType(alert.alertType)}
                          </p>
                        </div>
                        <span
                          className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
                            alert.priority === "HIGH"
                              ? "bg-rose-300/15 text-rose-100"
                              : "bg-amber-300/15 text-amber-100"
                          }`}
                        >
                          {alert.priority === "HIGH" ? "High" : "Active"}
                        </span>
                      </div>
                      <p className="mt-3 text-xs text-slate-500">
                        Triggered {date(alert.triggeredAt)}. Threshold{" "}
                        {percent(alert.threshold)}.
                      </p>
                    </article>
                  ))}
                </div>
              ) : (
                <State message="No active alerts. Monitoring rules are quiet." />
              )}
            </Panel>
          </section>

          <section className="grid gap-6 xl:grid-cols-2">
            <Panel title="Portfolio data quality" eyebrow="Coverage">
              <dl className="grid gap-3 sm:grid-cols-3">
                <SmallMetric
                  label="Priced holdings"
                  value={`${pricedHoldings.length}/${holdings.length}`}
                />
                <SmallMetric label="Coverage" value={percent(dataCoverage)} />
                <SmallMetric
                  label="Data as of"
                  value={date(portfolio.data?.updatedAt)}
                />
              </dl>
              <p className="mt-4 text-sm leading-6 text-slate-400">
                Missing prices, fair values, or margins of safety are shown as
                unavailable so stale or incomplete data does not look more
                certain than it is.
              </p>
            </Panel>

            <Panel title="Upcoming earnings and dividends" eyebrow="Next 30 days">
              <State message="Calendar data is not exposed by the current API. H7 keeps this panel visible as a tracked integration gap instead of calling market-data providers from the browser." />
            </Panel>
          </section>

          {hasDecisionContext(holdings, activeAlerts) && (
            <p className="rounded-xl border border-amber-300/20 bg-amber-300/5 p-4 text-xs leading-5 text-amber-100">
              {disclaimer}
            </p>
          )}
        </>
      )}
    </div>
  );
}

function EmptyPortfolio(): JSX.Element {
  return (
    <section className="rounded-2xl border border-slate-800 bg-slate-900/60 p-6">
      <h2 className="text-xl font-semibold text-white">No portfolios yet</h2>
      <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-400">
        Create a portfolio before the dashboard can calculate monitoring
        summaries, top movers, and alert context for holdings.
      </p>
      <LinkButton to="/portfolio">Create portfolio</LinkButton>
    </section>
  );
}

function MetricCard({
  label,
  value,
  detail,
  tone = "default",
}: {
  label: string;
  value: string;
  detail: string;
  tone?: "default" | "warning";
}): JSX.Element {
  return (
    <article
      className={`rounded-2xl border p-5 ${
        tone === "warning"
          ? "border-amber-300/30 bg-amber-300/5"
          : "border-slate-800 bg-slate-900/60"
      }`}
    >
      <p className="text-xs uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-3 text-2xl font-semibold text-white">{value}</p>
      <p className="mt-2 text-sm text-slate-400">{detail}</p>
    </article>
  );
}

function SmallMetric({
  label,
  value,
}: {
  label: string;
  value: string;
}): JSX.Element {
  return (
    <div className="rounded-lg bg-slate-950/55 p-3">
      <dt className="text-xs uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="mt-1 font-medium text-white">{value}</dd>
    </div>
  );
}

function Panel({
  title,
  eyebrow,
  action,
  children,
}: {
  title: string;
  eyebrow: string;
  action?: JSX.Element;
  children: JSX.Element | JSX.Element[];
}): JSX.Element {
  return (
    <section className="rounded-2xl border border-slate-800 bg-slate-900/60 p-5 sm:p-6">
      <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[.18em] text-emerald-300">
            {eyebrow}
          </p>
          <h2 className="mt-2 text-xl font-semibold text-white">{title}</h2>
        </div>
        {action}
      </div>
      {children}
    </section>
  );
}

function LinkButton({
  to,
  children,
}: {
  to: string;
  children: string;
}): JSX.Element {
  return (
    <Link
      to={to}
      className="mt-4 inline-flex rounded-lg border border-slate-600 px-3 py-2 text-sm font-medium text-slate-200 hover:border-emerald-300 hover:text-white"
    >
      {children}
    </Link>
  );
}

function State({
  message,
  retry,
  error,
}: {
  message: string;
  retry?: () => void;
  error?: boolean;
}): JSX.Element {
  return (
    <div
      className={`rounded-xl border p-5 text-sm ${
        error
          ? "border-rose-300/30 bg-rose-300/5 text-rose-100"
          : "border-slate-800 bg-slate-950/40 text-slate-400"
      }`}
    >
      <p>{message}</p>
      {retry && (
        <button
          onClick={retry}
          className="mt-3 rounded-lg border border-current px-3 py-1.5 font-medium"
        >
          Try again
        </button>
      )}
    </div>
  );
}
