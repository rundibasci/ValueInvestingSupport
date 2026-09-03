package it.mazzoni.vis.thesis;

import it.mazzoni.vis.domain.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import it.mazzoni.vis.domain.repository.ValueScoreRepository;
import it.mazzoni.vis.marketdata.MarketDataClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThesisInputBuilderTest {

    @Mock MarketDataClient marketDataClient;
    @Mock ValuationResultRepository valuationResultRepository;
    @Mock ValueScoreRepository valueScoreRepository;
    @Mock RatioSnapshotRepository ratioSnapshotRepository;

    private final ThesisInputBuilder builder = new ThesisInputBuilder(null, null, null, null);

    private static List<BigDecimal> series(String... values) {
        List<BigDecimal> list = new ArrayList<>();
        for (String v : values) list.add(v == null ? null : new BigDecimal(v));
        return list;
    }

    private static Security security(String symbol, String sector) {
        Security security = new Security();
        security.setSymbol(symbol);
        security.setCompanyName(symbol + " Inc.");
        security.setSector(sector);
        return security;
    }

    private static RatioSnapshot reitRatioSnapshot() {
        RatioSnapshot ratioSnapshot = new RatioSnapshot();
        ratioSnapshot.setFfoPerShare(new BigDecimal("3.9024"));
        ratioSnapshot.setAffoPerShare(new BigDecimal("1.9897"));
        ratioSnapshot.setPriceToFfo(new BigDecimal("15.6442"));
        ratioSnapshot.setPriceToAffo(new BigDecimal("30.6830"));
        ratioSnapshot.setAffoPayoutRatio(new BigDecimal("1.6225"));
        return ratioSnapshot;
    }

    @Test
    void classifyTrend_returnsNotAvailable_whenFewerThanTwoPoints() {
        assertThat(builder.classifyTrend(series("100"), "revenue", new ArrayList<>())).isEqualTo(Trend.NOT_AVAILABLE);
        assertThat(builder.classifyTrend(null, "revenue", new ArrayList<>())).isEqualTo(Trend.NOT_AVAILABLE);
    }

    @Test
    void classifyTrend_stronglyGrowing_onLargeIncrease() {
        // Newest-first, matching production data (FmpAdapter/DemoAnalysisService/
        // deriveNetDebtToEbitda convention) — latest listed first: 120 (latest) vs 100 (prior)
        // = +20%.
        assertThat(builder.classifyTrend(series("120", "100"), "revenue", new ArrayList<>()))
                .isEqualTo(Trend.STRONGLY_GROWING);
    }

    @Test
    void classifyTrend_growing_onModerateIncrease() {
        // Newest-first: 105 (latest) vs 100 (prior) = +5%.
        assertThat(builder.classifyTrend(series("105", "100"), "revenue", new ArrayList<>()))
                .isEqualTo(Trend.GROWING);
    }

    @Test
    void classifyTrend_stable_onSmallChange() {
        // Newest-first: 101 (latest) vs 100 (prior) = +1%.
        assertThat(builder.classifyTrend(series("101", "100"), "revenue", new ArrayList<>()))
                .isEqualTo(Trend.STABLE);
    }

    @Test
    void classifyTrend_declining_onModerateDecrease() {
        // Newest-first: 95 (latest) vs 100 (prior) = -5%.
        assertThat(builder.classifyTrend(series("95", "100"), "revenue", new ArrayList<>()))
                .isEqualTo(Trend.DECLINING);
    }

    @Test
    void classifyTrend_stronglyDeclining_onLargeDecrease() {
        // Newest-first: 60 (latest) vs 100 (prior) = -40%.
        assertThat(builder.classifyTrend(series("60", "100"), "revenue", new ArrayList<>()))
                .isEqualTo(Trend.STRONGLY_DECLINING);
    }

    @Test
    void classifyTrend_volatile_whenAnyPeriodSwingExceedsThreshold() {
        // Newest-first: 210 (latest) <- 200 (+5% period-over-period) <- 100 (prior, +100%
        // swing) — the huge older swing marks this volatile even though the latest period
        // alone would read as merely "growing".
        assertThat(builder.classifyTrend(series("210", "200", "100"), "revenue", new ArrayList<>()))
                .isEqualTo(Trend.VOLATILE);
    }

    @Test
    void classifyTrend_readsNewestFirst_notOldestFirst() {
        // Latest (index 0) = 70, prior (index 1) = 100: a genuine -30% decline ->
        // STRONGLY_DECLINING. Reading the array in the opposite (pre-TA6) direction would
        // instead treat 100 as "latest" and 70 as "prior", a +42.857...% increase ->
        // STRONGLY_GROWING — the two directions disagree on the resulting Trend, so this test
        // fails against the pre-fix implementation and passes against the fix.
        assertThat(builder.classifyTrend(series("70", "100"), "revenue", new ArrayList<>()))
                .isEqualTo(Trend.STRONGLY_DECLINING);
    }

    @Test
    void classifyTrend_recordsWarning_whenNotAvailable() {
        List<String> warnings = new ArrayList<>();
        builder.classifyTrend(series("100"), "earnings", warnings);
        assertThat(warnings).anyMatch(w -> w.contains("earningsTrend"));
    }

    @Test
    void build_populatesNetDebtToEbitda_fromRealEbitdaHistory_indexZero_notOperatingIncome() {
        ThesisInputBuilder wired = new ThesisInputBuilder(marketDataClient, valuationResultRepository,
                valueScoreRepository, ratioSnapshotRepository);
        Security security = security("AAPL", "Technology");

        // History lists are FMP's own newest-first order (index 0 = most recent/TTM, matching
        // FmpAdapter.toFundamentalSnapshot's income.get(0)/balance.get(0) convention and
        // SeedTickerService.persistFundamentals's i==0 scalar fallback). ebitdaHistory and
        // operatingIncomeHistory are deliberately given different index-0 values so this test
        // fails if either the wrong field or the wrong index is read.
        FundamentalSnapshot fundamentals = new FundamentalSnapshot(
                "AAPL", "AAPL Inc.", "Technology", "Consumer Electronics", "US", "USD",
                new BigDecimal("150"), new BigDecimal("6"), new BigDecimal("4"), 16_000_000_000L,
                series("110", "100"), series("22", "20"), series("19", "18"),
                series("6.2", "6"), List.of(16_000_000_000L, 16_000_000_000L),
                series("30", "25"), series("24", "22"), series("310", "300"),
                series("105", "100"), series("55", "50"), series("9", "10"),
                series("160", "150"), series("20", "18"), series("4.5", "4"),
                series("6", "5"), series("48", "40"), List.of(), List.of(),
                new BigDecimal("100"), new BigDecimal("150"), new BigDecimal("50"));
        when(marketDataClient.getFundamentals("AAPL")).thenReturn(fundamentals);
        when(marketDataClient.getRatios("AAPL")).thenReturn(null);
        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security)).thenReturn(Optional.empty());
        when(valueScoreRepository.findTopBySecurityOrderByScoreDateDesc(security)).thenReturn(Optional.empty());

        ThesisInput input = wired.build(security);

        // netDebt (100) / latest (index-0) ebitda (48) = 2.08 — NOT netDebt / latest operating
        // income (30) = 3.33, and NOT netDebt / oldest (index-1) ebitda (40) = 2.50.
        assertThat(input.netDebtToEbitda()).isEqualByComparingTo("2.08");
    }

    @Test
    void build_reitSecurityWithSectorMetrics_populatesFiveReitFields() {
        ThesisInputBuilder wired = new ThesisInputBuilder(marketDataClient, valuationResultRepository,
                valueScoreRepository, ratioSnapshotRepository);
        Security security = security("O", "Real Estate");

        when(marketDataClient.getFundamentals("O")).thenReturn(null);
        when(marketDataClient.getRatios("O")).thenReturn(null);
        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security)).thenReturn(Optional.empty());
        when(valueScoreRepository.findTopBySecurityOrderByScoreDateDesc(security)).thenReturn(Optional.empty());
        when(ratioSnapshotRepository.findTopBySecurityOrderByReportDateDesc(security))
                .thenReturn(Optional.of(reitRatioSnapshot()));

        ThesisInput input = wired.build(security);

        assertThat(input.ffoPerShare()).isEqualByComparingTo("3.9024");
        assertThat(input.affoPerShare()).isEqualByComparingTo("1.9897");
        assertThat(input.priceToFfo()).isEqualByComparingTo("15.6442");
        assertThat(input.priceToAffo()).isEqualByComparingTo("30.6830");
        assertThat(input.affoPayoutRatio()).isEqualByComparingTo("1.6225");
    }

    @Test
    void build_reitSecurityMissingSectorMetrics_leavesFiveReitFieldsNullWithNoExtraWarning() {
        ThesisInputBuilder wired = new ThesisInputBuilder(marketDataClient, valuationResultRepository,
                valueScoreRepository, ratioSnapshotRepository);
        Security security = security("O", "Real Estate");

        when(marketDataClient.getFundamentals("O")).thenReturn(null);
        when(marketDataClient.getRatios("O")).thenReturn(null);
        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security)).thenReturn(Optional.empty());
        when(valueScoreRepository.findTopBySecurityOrderByScoreDateDesc(security)).thenReturn(Optional.empty());
        // RatioSnapshot row exists (e.g. seeded before RM2's ordering fix) but its RM2 fields are still null.
        when(ratioSnapshotRepository.findTopBySecurityOrderByReportDateDesc(security))
                .thenReturn(Optional.of(new RatioSnapshot()));

        ThesisInput input = wired.build(security);

        assertThat(input.ffoPerShare()).isNull();
        assertThat(input.affoPerShare()).isNull();
        assertThat(input.priceToFfo()).isNull();
        assertThat(input.priceToAffo()).isNull();
        assertThat(input.affoPayoutRatio()).isNull();
        assertThat(input.deterministicWarnings()).noneMatch(w -> w.toLowerCase().contains("ffo") || w.toLowerCase().contains("affo"));
    }

    @Test
    void build_nonReitSecurity_leavesFiveReitFieldsNullAndNeverQueriesRatioSnapshotRepository() {
        ThesisInputBuilder wired = new ThesisInputBuilder(marketDataClient, valuationResultRepository,
                valueScoreRepository, ratioSnapshotRepository);
        Security security = security("AAPL", "Technology");

        when(marketDataClient.getFundamentals("AAPL")).thenReturn(null);
        when(marketDataClient.getRatios("AAPL")).thenReturn(null);
        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security)).thenReturn(Optional.empty());
        when(valueScoreRepository.findTopBySecurityOrderByScoreDateDesc(security)).thenReturn(Optional.empty());

        ThesisInput input = wired.build(security);

        assertThat(input.ffoPerShare()).isNull();
        assertThat(input.affoPerShare()).isNull();
        assertThat(input.priceToFfo()).isNull();
        assertThat(input.priceToAffo()).isNull();
        assertThat(input.affoPayoutRatio()).isNull();
        verifyNoInteractions(ratioSnapshotRepository);
    }
}
