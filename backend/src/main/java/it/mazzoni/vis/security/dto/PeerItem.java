package it.mazzoni.vis.security.dto;

import java.math.BigDecimal;

public record PeerItem(
        String symbol,
        String companyName,
        BigDecimal currentPrice,
        BigDecimal compositeFairValue,
        BigDecimal marginOfSafety,
        BigDecimal totalScore,
        BigDecimal pe,
        BigDecimal roic
) {}
