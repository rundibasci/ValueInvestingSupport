package it.mazzoni.vis.portfolio.analysis;
import java.time.*;
import java.math.BigDecimal;
public record PortfolioAnalysisOutcomeResponse(int position, String symbol, String status, String source, LocalDate refreshedAt, BigDecimal sourceLastPrice, BigDecimal sourceBaseValue, BigDecimal refreshedPrice, BigDecimal priceVariancePercent, String reasonCode, String reason, String fallbackReason, String errorMessage, String reviewPath, String calculationVersion, LocalDateTime startedAt, LocalDateTime completedAt) {}
