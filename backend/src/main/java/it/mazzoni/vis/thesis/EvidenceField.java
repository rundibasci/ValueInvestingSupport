package it.mazzoni.vis.thesis;

/** Mirrors vis-model-training/prompts/system-prompt-v2.txt's (and v3's) enumerated
 * allowed `evidenceFields` values — the only fields a bull/bear claim may cite. */
public enum EvidenceField {
    marketPrice, intrinsicValue, marginOfSafetyPercent, valueScore, dividendYieldPercent,
    payoutRatioPercent, netDebtToEbitda, revenueTrend, earningsTrend, freeCashFlowTrend,
    dataQuality, deterministicWarnings
}
