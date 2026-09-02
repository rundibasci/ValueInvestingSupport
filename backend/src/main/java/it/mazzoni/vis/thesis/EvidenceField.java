package it.mazzoni.vis.thesis;

/** Mirrors vis-model-training/prompts/system-prompt-v3.txt's enumerated allowed
 * `evidenceFields` values — the only fields a bull/bear claim may cite. RM4 added the five
 * REIT fields (ffoPerShare/affoPerShare/priceToFfo/priceToAffo/affoPayoutRatio) so a REIT
 * security's claims can be attributed to them, per mission.md Principle 15. */
public enum EvidenceField {
    marketPrice, intrinsicValue, marginOfSafetyPercent, valueScore, dividendYieldPercent,
    payoutRatioPercent, netDebtToEbitda, ffoPerShare, affoPerShare, priceToFfo, priceToAffo,
    affoPayoutRatio, revenueTrend, earningsTrend, freeCashFlowTrend, dataQuality,
    deterministicWarnings
}
