package it.mazzoni.vis.portfolio.importing.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PortfolioImportRowResponse(UUID rowId, int rowNumber, String productName, String sourceCode,
        String isin, BigDecimal quantity, BigDecimal sourceLastPrice, String nativeCurrency,
        BigDecimal nativeValue, BigDecimal baseValue, UUID resolvedSecurityId, String resolvedSymbol,
        String classification, String status, String warning, String error, String committedOutcome) { }
