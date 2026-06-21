package it.mazzoni.vis.portfolio.dto;

import java.math.BigDecimal;

public record SimulationProposalItem(
        String symbol, BigDecimal valueScore, BigDecimal currentPrice, long proposedShares,
        BigDecimal targetAmount, BigDecimal actualAmount, BigDecimal actualWeightPercent,
        String sector, String country, BigDecimal marginOfSafety, BigDecimal dividendYield
) {}
