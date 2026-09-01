package it.mazzoni.vis.screener.dto;

import it.mazzoni.vis.common.dto.AvailabilityResponse;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ScreenerResultItem(
        String symbol,
        String companyName,
        String sector,
        String exchange,
        BigDecimal currentPrice,
        BigDecimal compositeFairValue,
        BigDecimal marginOfSafety,
        BigDecimal totalScore,
        BigDecimal mosScore,
        BigDecimal qualityScore,
        BigDecimal safetyScore,
        BigDecimal growthScore,
        BigDecimal dividendScore,
        String recommendation,
        LocalDate scoreDate,
        AvailabilityResponse scoreAvailability,
        AvailabilityResponse valuationAvailability,
        Integer piotroskiScore,
        String piotroskiAvailabilityStatus,
        String altmanZone,
        String altmanAvailabilityStatus,
        String moatStrength,
        String sharesOutstandingTrend,
        String sectorMetricCaveat
) {}
