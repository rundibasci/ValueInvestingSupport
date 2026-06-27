import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Pie, PieChart, ResponsiveContainer, Cell, Tooltip } from "recharts";
import {
  portfolioApi,
  type Portfolio,
  type Simulation,
  type SimulationInput,
} from "../api/portfolio";

const inputClass =
  "mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white outline-none focus:border-emerald-400";
const buttonClass =
  "rounded-lg bg-emerald-400 px-4 py-2.5 text-sm font-semibold text-slate-950 transition hover:bg-emerald-300 disabled:cursor-not-allowed disabled:opacity-50";
const palette = [
  "#34d399",
  "#60a5fa",
  "#fbbf24",
  "#f472b6",
  "#a78bfa",
  "#fb7185",
];
const defaults: SimulationInput = {
  budget: 100000,
  maxStockPercent: 10,
  maxSectorPercent: 25,
  maxCountryPercent: 60,
  minimumMarginOfSafety: 15,
  minimumDividendYield: 0,
};
const maxHoldingQuantity = 1_000_000_000;

function money(value: number | null | undefined): string {
  return value == null
    ? "—"
    : new Intl.NumberFormat("en-US", {
        style: "currency",
        currency: "USD",
        maximumFractionDigits: 0,
      }).format(value);
}
function percent(value: number | null | undefined): string {
  return value == null ? "—" : `${Number(value).toFixed(1)}%`;
}
function ErrorNotice({ error }: { error: unknown }): JSX.Element | null {
  return error instanceof Error ? (
    <p
      role="alert"
      className="rounded-lg border border-rose-300/30 bg-rose-400/10 p-3 text-sm text-rose-100"
    >
      {error.message}
    </p>
  ) : null;
}

export function PortfolioPage(): JSX.Element {
  const client = useQueryClient();
  const [selected, setSelected] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [form, setForm] = useState<SimulationInput>(defaults);
  const [simulation, setSimulation] = useState<Simulation | null>(null);
  const [rebalance, setRebalance] = useState<Awaited<
    ReturnType<typeof portfolioApi.rebalance>
  > | null>(null);
  const [newSymbol, setNewSymbol] = useState("");
  const [newQuantity, setNewQuantity] = useState("");
  const [holdingValidation, setHoldingValidation] = useState<string | null>(
    null,
  );
  const portfolios = useQuery({
    queryKey: ["portfolios"],
    queryFn: portfolioApi.list,
  });
  const activeId = selected ?? portfolios.data?.[0]?.id ?? null;
  const detail = useQuery({
    queryKey: ["portfolio", activeId],
    queryFn: () => portfolioApi.detail(activeId!),
    enabled: Boolean(activeId),
  });
  const create = useMutation({
    mutationFn: () => portfolioApi.create(name, description),
    onSuccess: (created) => {
      setSelected(created.id);
      setName("");
      setDescription("");
      void client.invalidateQueries({ queryKey: ["portfolios"] });
    },
  });
  const runSimulation = useMutation({
    mutationFn: () => portfolioApi.simulate(activeId!, form),
    onSuccess: setSimulation,
  });
  const runRebalance = useMutation({
    mutationFn: () => portfolioApi.rebalance(activeId!, form),
    onSuccess: setRebalance,
  });
  const refreshDetail = (): void => {
    void client.invalidateQueries({ queryKey: ["portfolio", activeId] });
    void client.invalidateQueries({ queryKey: ["portfolios"] });
  };
  const addHolding = useMutation({
    mutationFn: () =>
      portfolioApi.addHolding(activeId!, {
        symbol: newSymbol.trim().toUpperCase(),
        quantity: Number(newQuantity),
      }),
    onSuccess: () => {
      setNewSymbol("");
      setNewQuantity("");
      refreshDetail();
    },
  });
  const saveProposal = useMutation({
    mutationFn: async () => {
      if (!simulation) return;
      for (const item of simulation.proposals)
        await portfolioApi.addHolding(activeId!, {
          symbol: item.symbol,
          quantity: item.proposedShares,
          averageCostBasis: item.currentPrice ?? undefined,
          currency: "USD",
        });
    },
    onSuccess: refreshDetail,
  });
  const removeHolding = useMutation({
    mutationFn: (holdingId: string) =>
      portfolioApi.removeHolding(activeId!, holdingId),
    onSuccess: refreshDetail,
  });
  const totalWeight = useMemo(
    () =>
      simulation?.proposals.reduce(
        (sum, item) => sum + item.actualWeightPercent,
        0,
      ) ?? 0,
    [simulation],
  );
  const canRun =
    Boolean(activeId) &&
    form.budget > 0 &&
    (form.maxStockPercent ?? 0) > 0 &&
    (form.maxSectorPercent ?? 0) > 0 &&
    (form.maxCountryPercent ?? 0) > 0;
  const update = (field: keyof SimulationInput, raw: string): void =>
    setForm((current) => ({
      ...current,
      [field]: raw === "" ? undefined : Number(raw),
    }));
  const quantityIsValid =
    Number.isInteger(Number(newQuantity)) &&
    Number(newQuantity) > 0 &&
    Number(newQuantity) <= maxHoldingQuantity;

  return (
    <div className="space-y-6">
      <section className="rounded-2xl border border-slate-800 bg-slate-900/60 p-6 sm:p-8">
        <p className="text-xs font-semibold uppercase tracking-[.22em] text-emerald-300">
          Construct
        </p>
        <h1 className="mt-3 text-3xl font-semibold text-white sm:text-4xl">
          Build with constraints, not hunches.
        </h1>
        <p className="mt-3 max-w-3xl leading-7 text-slate-300">
          Create a model portfolio, test its diversification limits, then review
          the practical trades required to rebalance it.
        </p>
      </section>

      <section className="grid gap-6 xl:grid-cols-[20rem_1fr]">
        <aside className="rounded-2xl border border-slate-800 bg-slate-900/50 p-5">
          <h2 className="text-lg font-semibold text-white">Your portfolios</h2>
          <div className="mt-4 space-y-2">
            {portfolios.isLoading && (
              <p className="text-sm text-slate-400">Loading portfolios…</p>
            )}
            {portfolios.data?.map((portfolio: Portfolio) => (
              <button
                key={portfolio.id}
                onClick={() => {
                  setSelected(portfolio.id);
                  setSimulation(null);
                  setRebalance(null);
                }}
                className={`w-full rounded-xl border p-3 text-left transition ${activeId === portfolio.id ? "border-emerald-400/60 bg-emerald-400/10" : "border-slate-800 hover:bg-slate-800/70"}`}
              >
                <span className="block font-medium text-white">
                  {portfolio.name}
                </span>
                <span className="text-xs text-slate-400">
                  {portfolio.holdingCount} holdings
                </span>
              </button>
            ))}
          </div>
          <form
            className="mt-5 border-t border-slate-800 pt-5"
            onSubmit={(event) => {
              event.preventDefault();
              if (name.trim()) create.mutate();
            }}
          >
            <label className="text-sm font-medium text-slate-200">
              New portfolio
              <input
                required
                value={name}
                onChange={(event) => setName(event.target.value)}
                className={inputClass}
                placeholder="Long-term value"
              />
            </label>
            <label className="mt-3 block text-sm font-medium text-slate-200">
              Description
              <input
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                className={inputClass}
                placeholder="Optional"
              />
            </label>
            <button
              disabled={create.isPending}
              className={`mt-4 w-full ${buttonClass}`}
            >
              {create.isPending ? "Creating…" : "Create portfolio"}
            </button>
          </form>
          <ErrorNotice error={portfolios.error ?? create.error} />
        </aside>

        <div className="space-y-6">
          <section className="rounded-2xl border border-slate-800 bg-slate-900/50 p-5 sm:p-6">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h2 className="text-xl font-semibold text-white">
                  {detail.data?.name ?? "Select or create a portfolio"}
                </h2>
                <p className="mt-1 text-sm text-slate-400">
                  {detail.data?.description ??
                    "A simulation needs a portfolio to receive its allocation."}
                </p>
              </div>
              {detail.data && (
                <div className="text-right text-sm">
                  <p className="text-slate-400">Current value</p>
                  <p className="font-semibold text-white">
                    {money(detail.data.totalValue)}
                  </p>
                </div>
              )}
            </div>
            {detail.data?.holdings?.length ? (
              <div className="mt-5 overflow-x-auto">
                <table className="w-full min-w-[38rem] text-left text-sm">
                  <thead className="text-xs uppercase text-slate-400">
                    <tr>
                      <th>Holding</th>
                      <th>Quantity</th>
                      <th>Value</th>
                      <th>Weight</th>
                      <th>MoS</th>
                      <th>
                        <span className="sr-only">Actions</span>
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800">
                    {detail.data.holdings.map((holding) => (
                      <tr key={holding.id}>
                        <td className="py-3 font-semibold text-emerald-300">
                          {holding.symbol}
                        </td>
                        <td>{holding.quantity}</td>
                        <td>{money(holding.currentValue)}</td>
                        <td>{percent(holding.weightPercent)}</td>
                        <td>{percent(holding.marginOfSafety)}</td>
                        <td>
                          <div className="flex flex-wrap gap-3">
                            <Link
                              to={`/securities/${encodeURIComponent(holding.symbol)}/review`}
                              className="text-xs font-semibold text-emerald-200 underline"
                            >
                              Review
                            </Link>
                            <button
                              type="button"
                              onClick={() => removeHolding.mutate(holding.id)}
                              className="text-xs font-semibold text-rose-200 underline"
                            >
                              Remove
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              activeId && (
                <p className="mt-5 rounded-lg bg-slate-950/60 p-4 text-sm text-slate-400">
                  No holdings yet. Run a simulation to assess a proposed
                  allocation; add holdings through the existing portfolio API
                  while portfolio editing is being expanded.
                </p>
              )
            )}
            {activeId && (
              <form
                className="mt-5 flex flex-wrap items-end gap-3 border-t border-slate-800 pt-5"
                onSubmit={(event) => {
                  event.preventDefault();
                  if (!quantityIsValid) {
                    setHoldingValidation(
                      `Quantity must be a whole number from 1 to ${maxHoldingQuantity.toLocaleString()}.`,
                    );
                    return;
                  }
                  setHoldingValidation(null);
                  if (newSymbol) addHolding.mutate();
                }}
              >
                <label className="text-sm font-medium text-slate-200">
                  Ticker
                  <input
                    required
                    value={newSymbol}
                    onChange={(event) => setNewSymbol(event.target.value)}
                    className={inputClass}
                    placeholder="AAPL"
                  />
                </label>
                <label className="text-sm font-medium text-slate-200">
                  Quantity
                  <input
                    required
                    type="number"
                    min="1"
                    step="1"
                    max={maxHoldingQuantity}
                    value={newQuantity}
                    onChange={(event) => {
                      setNewQuantity(event.target.value);
                      setHoldingValidation(null);
                    }}
                    className={inputClass}
                    placeholder="10"
                  />
                </label>
                <button
                  disabled={
                    addHolding.isPending || !newSymbol || !quantityIsValid
                  }
                  className={buttonClass}
                >
                  {addHolding.isPending ? "Adding…" : "Add holding"}
                </button>
              </form>
            )}
            {holdingValidation && (
              <p role="alert" className="mt-3 text-sm text-rose-200">
                {holdingValidation}
              </p>
            )}
            <ErrorNotice
              error={detail.error ?? addHolding.error ?? removeHolding.error}
            />
          </section>

          <section className="rounded-2xl border border-slate-800 bg-slate-900/50 p-5 sm:p-6">
            <h2 className="text-xl font-semibold text-white">
              Allocation constraints
            </h2>
            <p className="mt-1 text-sm text-slate-400">
              The risk profile establishes a conservative starting point; every
              limit remains explicit and editable.
            </p>
            <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <label className="text-sm font-medium text-slate-200">
                Risk profile
                <select
                  onChange={(event) => {
                    const profile = event.target.value;
                    setForm(
                      profile === "conservative"
                        ? {
                            ...form,
                            maxStockPercent: 7,
                            maxSectorPercent: 20,
                            maxCountryPercent: 50,
                          }
                        : profile === "growth"
                          ? {
                              ...form,
                              maxStockPercent: 15,
                              maxSectorPercent: 35,
                              maxCountryPercent: 75,
                            }
                          : {
                              ...form,
                              maxStockPercent: 10,
                              maxSectorPercent: 25,
                              maxCountryPercent: 60,
                            },
                    );
                  }}
                  className={inputClass}
                >
                  <option value="balanced">Balanced</option>
                  <option value="conservative">Conservative</option>
                  <option value="growth">Growth</option>
                </select>
              </label>
              <label className="text-sm font-medium text-slate-200">
                Budget (USD)
                <input
                  type="number"
                  min="1"
                  value={form.budget ?? ""}
                  onChange={(event) => update("budget", event.target.value)}
                  className={inputClass}
                />
              </label>
              <label className="text-sm font-medium text-slate-200">
                Minimum yield (%)
                <input
                  type="number"
                  min="0"
                  value={form.minimumDividendYield ?? ""}
                  onChange={(event) =>
                    update("minimumDividendYield", event.target.value)
                  }
                  className={inputClass}
                />
              </label>
              <label className="text-sm font-medium text-slate-200">
                Max. stock (%)
                <input
                  type="number"
                  min="1"
                  max="100"
                  value={form.maxStockPercent ?? ""}
                  onChange={(event) =>
                    update("maxStockPercent", event.target.value)
                  }
                  className={inputClass}
                />
              </label>
              <label className="text-sm font-medium text-slate-200">
                Max. sector (%)
                <input
                  type="number"
                  min="1"
                  max="100"
                  value={form.maxSectorPercent ?? ""}
                  onChange={(event) =>
                    update("maxSectorPercent", event.target.value)
                  }
                  className={inputClass}
                />
              </label>
              <label className="text-sm font-medium text-slate-200">
                Max. country (%)
                <input
                  type="number"
                  min="1"
                  max="100"
                  value={form.maxCountryPercent ?? ""}
                  onChange={(event) =>
                    update("maxCountryPercent", event.target.value)
                  }
                  className={inputClass}
                />
              </label>
              <label className="text-sm font-medium text-slate-200">
                Minimum MoS (%)
                <input
                  type="number"
                  min="0"
                  value={form.minimumMarginOfSafety ?? ""}
                  onChange={(event) =>
                    update("minimumMarginOfSafety", event.target.value)
                  }
                  className={inputClass}
                />
              </label>
            </div>
            <div className="mt-5 flex flex-wrap gap-3">
              <button
                disabled={!canRun || runSimulation.isPending}
                onClick={() => runSimulation.mutate()}
                className={buttonClass}
              >
                {runSimulation.isPending
                  ? "Simulating…"
                  : "Run allocation simulation"}
              </button>
              <button
                disabled={!canRun || runRebalance.isPending}
                onClick={() => runRebalance.mutate()}
                className="rounded-lg border border-slate-600 px-4 py-2.5 text-sm font-semibold text-slate-200 hover:bg-slate-800 disabled:opacity-50"
              >
                {runRebalance.isPending
                  ? "Preparing…"
                  : "Create rebalance proposal"}
              </button>
            </div>
            <ErrorNotice error={runSimulation.error ?? runRebalance.error} />
          </section>
        </div>
      </section>

      {simulation && (
        <section className="grid gap-6 xl:grid-cols-[1fr_20rem]">
          <div className="overflow-hidden rounded-2xl border border-slate-800 bg-slate-900/50">
            <div className="border-b border-slate-800 p-5 sm:p-6">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <h2 className="text-xl font-semibold text-white">
                  Proposed allocation
                </h2>
                <button
                  disabled={saveProposal.isPending}
                  onClick={() => saveProposal.mutate()}
                  className={buttonClass}
                >
                  {saveProposal.isPending
                    ? "Saving…"
                    : "Save proposed holdings"}
                </button>
              </div>
              <p className="mt-1 text-sm text-slate-400">
                {money(simulation.investedAmount)} invested ·{" "}
                {money(simulation.unallocatedCash)} unallocated ·{" "}
                {percent(totalWeight)} allocated
              </p>
            </div>
            <div className="overflow-x-auto">
              <table className="min-w-[50rem] w-full text-left text-sm">
                <thead className="bg-slate-950/40 text-xs uppercase text-slate-400">
                  <tr>
                    <th className="px-4 py-3">Security</th>
                    <th>Sector</th>
                    <th>Shares</th>
                    <th>Amount</th>
                    <th>Weight</th>
                    <th>MoS</th>
                    <th>Yield</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800">
                  {simulation.proposals.map((item) => (
                    <tr key={item.symbol}>
                      <td className="px-4 py-3 font-semibold text-emerald-300">
                        {item.symbol}
                      </td>
                      <td>{item.sector ?? "—"}</td>
                      <td>{item.proposedShares}</td>
                      <td>{money(item.actualAmount)}</td>
                      <td>{percent(item.actualWeightPercent)}</td>
                      <td>{percent(item.marginOfSafety)}</td>
                      <td>{percent(item.dividendYield)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {simulation.excludedSymbols.length > 0 && (
              <p className="m-5 text-sm text-amber-100">
                Excluded:{" "}
                {simulation.excludedSymbols
                  .map((item) => `${item.symbol} (${item.reason})`)
                  .join(", ")}
              </p>
            )}
            <p className="m-5 text-xs leading-5 text-slate-400">
              {simulation.disclaimer}
            </p>
          </div>
          <aside className="rounded-2xl border border-slate-800 bg-slate-900/50 p-5">
            <h2 className="text-lg font-semibold text-white">
              Sector allocation
            </h2>
            <div className="mt-4 h-56" aria-label="Sector allocation chart">
              <ResponsiveContainer>
                <PieChart>
                  <Pie
                    data={simulation.sectorWeights}
                    dataKey="weightPercent"
                    nameKey="key"
                    innerRadius="55%"
                    outerRadius="80%"
                  >
                    {simulation.sectorWeights.map((entry, index) => (
                      <Cell
                        key={entry.key}
                        fill={palette[index % palette.length]}
                      />
                    ))}
                  </Pie>
                  <Tooltip formatter={(value) => percent(Number(value))} />
                </PieChart>
              </ResponsiveContainer>
            </div>
            <ul className="space-y-2 text-sm">
              {simulation.sectorWeights.map((entry, index) => (
                <li key={entry.key} className="flex justify-between">
                  <span>
                    <span
                      className="mr-2 inline-block h-2.5 w-2.5 rounded-full"
                      style={{ background: palette[index % palette.length] }}
                    />
                    {entry.key}
                  </span>
                  <span>{percent(entry.weightPercent)}</span>
                </li>
              ))}
            </ul>
          </aside>
        </section>
      )}
      {rebalance && (
        <section className="overflow-hidden rounded-2xl border border-slate-800 bg-slate-900/50">
          <div className="flex flex-wrap justify-between gap-4 border-b border-slate-800 p-5 sm:p-6">
            <div>
              <h2 className="text-xl font-semibold text-white">
                Rebalance proposal
              </h2>
              <p className="mt-1 text-sm text-slate-400">
                Buy {money(rebalance.estimatedBuyValue)} · Sell{" "}
                {money(rebalance.estimatedSellValue)}
              </p>
            </div>
            <span className="rounded-full bg-emerald-400/10 px-3 py-1 text-sm text-emerald-200">
              {rebalance.status}
            </span>
          </div>
          <div className="overflow-x-auto">
            <table className="min-w-[42rem] w-full text-left text-sm">
              <thead className="bg-slate-950/40 text-xs uppercase text-slate-400">
                <tr>
                  <th className="px-4 py-3">Security</th>
                  <th>Action</th>
                  <th>Current</th>
                  <th>Target</th>
                  <th>Change</th>
                  <th>Estimated value</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800">
                {rebalance.lines.map((line) => (
                  <tr key={line.symbol}>
                    <td className="px-4 py-3 font-semibold text-emerald-300">
                      {line.symbol}
                    </td>
                    <td>{line.side}</td>
                    <td>{line.currentQuantity}</td>
                    <td>{line.targetQuantity}</td>
                    <td>{line.deltaQuantity}</td>
                    <td>{money(line.estimatedTradeValue)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="m-5 text-xs leading-5 text-slate-400">
            {rebalance.disclaimer}
          </p>
        </section>
      )}
    </div>
  );
}
