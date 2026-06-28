import { apiFetch } from "./client";

export type Portfolio = {
  id: string;
  name: string;
  description: string | null;
  holdingCount: number;
  createdAt: string;
  updatedAt: string;
};
export type Holding = {
  id: string;
  symbol: string;
  sector: string | null;
  quantity: number;
  averageCostBasis: number | null;
  currency: string | null;
  currentPrice: number | null;
  currentValue: number | null;
  weightPercent: number | null;
  compositeFairValue: number | null;
  marginOfSafety: number | null;
  recommendation: string | null;
  valueStatus: string;
  addedAt: string;
};
export type ConcentrationWarning = {
  type: string;
  key: string;
  weightPercent: number | null;
  thresholdPercent: number | null;
  message: string;
};
export type PortfolioDetail = Portfolio & {
  totalValue: number | null;
  weightedMoS: number | null;
  holdings: Holding[];
  concentrationWarnings: ConcentrationWarning[];
};
export type SimulationInput = {
  budget: number;
  maxStockPercent?: number;
  maxSectorPercent?: number;
  maxCountryPercent?: number;
  minimumMarginOfSafety?: number;
  minimumDividendYield?: number;
};
export type Proposal = {
  symbol: string;
  valueScore: number | null;
  currentPrice: number | null;
  proposedShares: number;
  targetAmount: number;
  actualAmount: number;
  actualWeightPercent: number;
  sector: string | null;
  country: string | null;
  marginOfSafety: number | null;
  dividendYield: number | null;
};
export type Weight = { key: string; weightPercent: number };
export type Simulation = {
  portfolioId: string;
  budget: number;
  investedAmount: number;
  unallocatedCash: number;
  weightedMarginOfSafety: number | null;
  weightedDividendYield: number | null;
  proposals: Proposal[];
  excludedSymbols: { symbol: string; reason: string }[];
  sectorWeights: Weight[];
  countryWeights: Weight[];
  disclaimer: string;
};
export type Rebalance = {
  id: string;
  status: string;
  estimatedBuyValue: number;
  estimatedSellValue: number;
  disclaimer: string;
  lines: {
    symbol: string;
    capturedPrice: number;
    currentQuantity: number;
    targetQuantity: number;
    deltaQuantity: number;
    estimatedTradeValue: number;
    side: string;
  }[];
};

async function json<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init);
  if (!response.ok) {
    const payload = (await response.json().catch(() => null)) as
      | { message?: string; detail?: string; error?: string }
      | null;
    throw new Error(
      payload?.message ||
        payload?.detail ||
        payload?.error ||
        `Request failed (${response.status}).`,
    );
  }
  return response.json() as Promise<T>;
}
const body = (value: unknown): RequestInit => ({
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(value),
});

export const portfolioApi = {
  list: () => json<Portfolio[]>("/api/v1/portfolios"),
  detail: (id: string) => json<PortfolioDetail>(`/api/v1/portfolios/${id}`),
  create: (name: string, description: string) =>
    json<Portfolio>(
      "/api/v1/portfolios",
      body({ name, description: description || null }),
    ),
  simulate: (id: string, input: SimulationInput) =>
    json<Simulation>(`/api/v1/portfolios/${id}/simulate`, body(input)),
  rebalance: (id: string, simulation: SimulationInput) =>
    json<Rebalance>(
      `/api/v1/portfolios/${id}/rebalance`,
      body({ simulation, minimumTradeValue: 0 }),
    ),
  addHolding: (
    id: string,
    holding: {
      symbol: string;
      quantity: number;
      averageCostBasis?: number;
      currency?: string;
    },
  ) => json<Holding>(`/api/v1/portfolios/${id}/holdings`, body(holding)),
  removeHolding: async (id: string, holdingId: string): Promise<void> => {
    const response = await apiFetch(
      `/api/v1/portfolios/${id}/holdings/${holdingId}`,
      { method: "DELETE" },
    );
    if (!response.ok) throw new Error(`Request failed (${response.status}).`);
  },
};
