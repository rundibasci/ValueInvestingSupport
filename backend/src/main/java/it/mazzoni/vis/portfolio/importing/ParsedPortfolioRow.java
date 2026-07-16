package it.mazzoni.vis.portfolio.importing;

import java.math.BigDecimal;

public record ParsedPortfolioRow(int rowNumber, String productName, String sourceCode, String isin,
                                 BigDecimal quantity, BigDecimal sourceLastPrice, String currency,
                                 BigDecimal nativeValue, BigDecimal baseValue, String classification,
                                 ImportRowStatus status, String warning, String error) { }
