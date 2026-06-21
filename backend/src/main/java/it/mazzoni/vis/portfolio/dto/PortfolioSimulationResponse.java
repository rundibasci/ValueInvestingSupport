package it.mazzoni.vis.portfolio.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PortfolioSimulationResponse(
        UUID portfolioId, BigDecimal budget, BigDecimal maxStockPercent, BigDecimal maxSectorPercent,
        BigDecimal maxCountryPercent, BigDecimal investedAmount, BigDecimal unallocatedCash,
        BigDecimal weightedMarginOfSafety, BigDecimal weightedDividendYield,
        List<SimulationProposalItem> proposals, List<SimulationExclusion> excludedSymbols,
        List<AllocationWeight> sectorWeights, List<AllocationWeight> countryWeights,
        String disclaimer
) {}
