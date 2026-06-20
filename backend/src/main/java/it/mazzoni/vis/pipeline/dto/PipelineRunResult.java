package it.mazzoni.vis.pipeline.dto;

import it.mazzoni.vis.domain.entity.Recommendation;

import java.math.BigDecimal;

public record PipelineRunResult(
        String symbol,
        String companyName,
        BigDecimal compositeFairValue,
        BigDecimal marginOfSafety,
        BigDecimal totalScore,
        String recommendation,
        String error
) {
    public static PipelineRunResult success(String symbol, String companyName,
            BigDecimal compositeFairValue, BigDecimal marginOfSafety,
            BigDecimal totalScore, Recommendation recommendation) {
        return new PipelineRunResult(symbol, companyName, compositeFairValue, marginOfSafety,
                totalScore, recommendation != null ? recommendation.name() : null, null);
    }

    public static PipelineRunResult failed(String symbol, String error) {
        return new PipelineRunResult(symbol, null, null, null, null, null, error);
    }
}
