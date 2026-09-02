package it.mazzoni.vis.screener;

import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Recommendation;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.entity.ValueScore;
import it.mazzoni.vis.domain.entity.PiotroskiResult;
import it.mazzoni.vis.domain.entity.AltmanResult;
import it.mazzoni.vis.domain.entity.AltmanFormulaVariant;
import it.mazzoni.vis.domain.entity.AltmanZone;
import it.mazzoni.vis.domain.entity.RiskAvailabilityStatus;
import it.mazzoni.vis.screener.dto.ScreenerRequest;
import it.mazzoni.vis.screener.dto.ScreenerResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@Import(ScreenerService.class)
class ScreenerServiceTest {

    @Autowired ScreenerService screenerService;
    @PersistenceContext EntityManager em;

    @BeforeEach
    void seed() {
        seedSecurity("AAPL", "Apple Inc.", "Technology", "NASDAQ",
                new BigDecimal("72.50"), new BigDecimal("22.00"),
                new BigDecimal("0.15"), new BigDecimal("0.8"),
                new BigDecimal("0.006"));

        seedSecurity("KO", "Coca-Cola Co.", "Consumer Staples", "NYSE",
                new BigDecimal("58.00"), new BigDecimal("18.00"),
                new BigDecimal("0.12"), new BigDecimal("1.2"),
                new BigDecimal("0.030"));

        seedSecurity("XOM", "Exxon Mobil", "Energy", "NYSE",
                new BigDecimal("35.00"), new BigDecimal("8.00"),
                new BigDecimal("0.08"), new BigDecimal("2.5"),
                new BigDecimal("0.040"));

        em.flush();
    }

    @Test
    void search_reitSecurity_returnsPersistedSectorMetricFields() {
        seedReitO();
        em.flush();

        ScreenerResponse result = screenerService.search(emptyRequest());
        var item = result.results().stream().filter(r -> "O".equals(r.symbol())).findFirst().orElseThrow();

        assertThat(item.ffoPerShare()).isEqualByComparingTo("3.9024");
        assertThat(item.affoPerShare()).isEqualByComparingTo("1.9897");
        assertThat(item.priceToFfo()).isEqualByComparingTo("15.6442");
        assertThat(item.priceToAffo()).isEqualByComparingTo("30.6830");
        assertThat(item.netDebtToEbitda()).isEqualByComparingTo("9.1298");
        assertThat(item.interestCoverageEbitda()).isEqualByComparingTo("3.1088");
        assertThat(item.affoPayoutRatio()).isEqualByComparingTo("1.6225");
    }

    @Test
    void search_nonReitSecurity_sectorMetricFieldsAreNull() {
        ScreenerResponse result = screenerService.search(emptyRequest());
        var item = result.results().stream().filter(r -> "AAPL".equals(r.symbol())).findFirst().orElseThrow();

        assertThat(item.ffoPerShare()).isNull();
        assertThat(item.affoPerShare()).isNull();
        assertThat(item.priceToFfo()).isNull();
        assertThat(item.priceToAffo()).isNull();
        assertThat(item.netDebtToEbitda()).isNull();
        assertThat(item.interestCoverageEbitda()).isNull();
        assertThat(item.affoPayoutRatio()).isNull();
    }

    @Test
    void search_maxNetDebtToEbitda_excludesHighlyLeveredReits() {
        seedReitO();
        seedReitPld();
        em.flush();

        ScreenerRequest req = new ScreenerRequest(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, new BigDecimal("5.0"), null,
                "totalScore", "DESC", 0, 20);

        ScreenerResponse result = screenerService.search(req);
        assertThat(result.results()).extracting("symbol").containsExactly("PLD");
    }

    @Test
    void search_maxPriceToFfo_excludesNonReitSecurities() {
        // Non-REIT rows have a null priceToFfo; a <= threshold comparison against null excludes
        // them — documented, implicit REIT-only scoping (requirements.md Decision 2).
        seedReitO();
        em.flush();

        ScreenerRequest req = new ScreenerRequest(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                new BigDecimal("20.0"), null, null,
                "totalScore", "DESC", 0, 20);

        ScreenerResponse result = screenerService.search(req);
        assertThat(result.results()).extracting("symbol").containsExactly("O");
    }

    @Test
    void search_sortByPriceToFfo_ordersReitResultsAscending() {
        seedReitO();
        seedReitPld();
        em.flush();

        ScreenerRequest req = new ScreenerRequest(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null,
                "priceToFfo", "ASC", 0, 20);

        ScreenerResponse result = screenerService.search(req);
        // Non-REIT rows sort with a null priceToFfo; only assert the relative order of the two
        // REIT rows, which is what this test is actually about.
        var symbols = result.results().stream().map(r -> r.symbol())
                .filter(s -> s.equals("O") || s.equals("PLD")).toList();
        assertThat(symbols).containsExactly("O", "PLD");
    }

    @Test
    void search_noFilters_returnsAllThreeSecurities() {
        ScreenerResponse result = screenerService.search(emptyRequest());
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.results()).hasSize(3);
    }

    @Test
    void search_nullRequest_usesDefaultScreen() {
        ScreenerResponse result = screenerService.search(null);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.page()).isZero();
        assertThat(result.pageSize()).isEqualTo(20);
    }

    @Test
    void search_fractionalPercentThreshold_returnsBadRequest() {
        ScreenerRequest req = new ScreenerRequest(
                null, null,
                new BigDecimal("0.15"), null,
                null, null, null, null, null,
                null, null, null,
                "totalScore", "DESC", 0, 20);

        assertThatThrownBy(() -> screenerService.search(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("minMarginOfSafety expects percentages");
    }

    @Test
    void search_sectorFilter_returnsOnlyMatchingSector() {
        ScreenerRequest req = new ScreenerRequest(
                "Technology", null, null, null, null,
                null, null, null, null,
                null, null, null,
                "totalScore", "DESC", 0, 20);

        ScreenerResponse result = screenerService.search(req);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.results().get(0).symbol()).isEqualTo("AAPL");
    }

    @Test
    void search_minValueScore_excludesLowScoringSecurities() {
        ScreenerRequest req = new ScreenerRequest(
                null, null, null, null,
                new BigDecimal("50"),
                null, null, null, null,
                null, null, null,
                "totalScore", "DESC", 0, 20);

        ScreenerResponse result = screenerService.search(req);
        assertThat(result.results()).allSatisfy(
                item -> assertThat(item.totalScore()).isGreaterThanOrEqualTo(new BigDecimal("50")));
    }

    @Test
    void search_minMarginOfSafety_filtersCorrectly() {
        // Only AAPL (22%) and KO (18%) have marginOfSafety >= 15
        ScreenerRequest req = new ScreenerRequest(
                null, null,
                new BigDecimal("15"), null,
                null, null, null, null, null,
                null, null, null,
                "totalScore", "DESC", 0, 20);

        ScreenerResponse result = screenerService.search(req);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.results()).allSatisfy(
                item -> assertThat(item.marginOfSafety())
                        .isGreaterThanOrEqualTo(new BigDecimal("15")));
    }

    @Test
    void search_maxDebtToEquity_excludesHighlyLeveredSecurities() {
        // XOM has D/E = 2.5, should be excluded when maxDebtToEquity = 2.0
        ScreenerRequest req = new ScreenerRequest(
                null, null, null, null, null,
                null, new BigDecimal("2.0"),
                null, null,
                null, null, null,
                "totalScore", "DESC", 0, 20);

        ScreenerResponse result = screenerService.search(req);
        assertThat(result.results()).noneMatch(item -> "XOM".equals(item.symbol()));
    }

    @Test
    void search_defaultSort_firstItemHasHighestScore() {
        ScreenerResponse result = screenerService.search(emptyRequest());
        BigDecimal firstScore = result.results().get(0).totalScore();
        BigDecimal lastScore = result.results().get(result.results().size() - 1).totalScore();
        assertThat(firstScore).isGreaterThanOrEqualTo(lastScore);
    }

    @Test
    void search_pagination_respectsPageSizeAndPage() {
        ScreenerRequest page0 = new ScreenerRequest(
                null, null, null, null, null, null, null, null, null,
                null, null, null,
                "symbol", "ASC", 0, 2);
        ScreenerRequest page1 = new ScreenerRequest(
                null, null, null, null, null, null, null, null, null,
                null, null, null,
                "symbol", "ASC", 1, 2);

        ScreenerResponse r0 = screenerService.search(page0);
        ScreenerResponse r1 = screenerService.search(page1);

        assertThat(r0.results()).hasSize(2);
        assertThat(r1.results()).hasSize(1);
        assertThat(r0.totalElements()).isEqualTo(3);
        assertThat(r0.results().get(0).symbol())
                .isNotEqualTo(r1.results().get(0).symbol());
    }

    // --- helpers ---

    private ScreenerRequest emptyRequest() {
        return new ScreenerRequest(
                null, null, null, null, null, null, null, null, null,
                null, null, null,
                "totalScore", "DESC", 0, 20);
    }

    private void seedSecurity(String symbol, String name, String sector, String exchange,
                               BigDecimal totalScore, BigDecimal marginOfSafety,
                               BigDecimal roic, BigDecimal debtToEquity,
                               BigDecimal dividendYield) {
        Security sec = new Security();
        sec.setSymbol(symbol);
        sec.setCompanyName(name);
        sec.setSector(sector);
        sec.setExchange(exchange);
        em.persist(sec);

        ValuationResult vr = new ValuationResult();
        vr.setSecurity(sec);
        vr.setValuationDate(LocalDate.now());
        vr.setCompositeFairValue(new BigDecimal("100.00"));
        vr.setCurrentPrice(new BigDecimal("80.00"));
        vr.setMarginOfSafety(marginOfSafety);
        vr.setRecommendation(Recommendation.QUALITY_VALUE);
        vr.setSource("test");
        em.persist(vr);

        RatioSnapshot rs = new RatioSnapshot();
        rs.setSecurity(sec);
        rs.setPeriod(Period.TTM);
        rs.setReportDate(LocalDate.now());
        rs.setRoic(roic);
        rs.setDebtToEquity(debtToEquity);
        rs.setDividendYield(dividendYield);
        em.persist(rs);

        BigDecimal mos = marginOfSafety;
        BigDecimal mosScore = mos.compareTo(new BigDecimal("30")) >= 0 ? new BigDecimal("30")
                : mos.compareTo(new BigDecimal("15")) >= 0 ? new BigDecimal("20")
                : mos.compareTo(new BigDecimal("5")) >= 0 ? new BigDecimal("10")
                : BigDecimal.ZERO;

        ValueScore vs = new ValueScore();
        vs.setSecurity(sec);
        vs.setScoreDate(LocalDate.now());
        vs.setMosScore(mosScore);
        vs.setQualityScore(BigDecimal.ZERO);
        vs.setSafetyScore(BigDecimal.ZERO);
        vs.setGrowthScore(BigDecimal.ZERO);
        vs.setDividendScore(BigDecimal.ZERO);
        vs.setTotalScore(totalScore);
        em.persist(vs);

        PiotroskiResult pr = new PiotroskiResult();
        pr.setSecurity(sec);
        pr.setResultDate(LocalDate.now());
        pr.setTotalScore("AAPL".equals(symbol) ? 8 : "KO".equals(symbol) ? 6 : 3);
        pr.setAvailabilityStatus(RiskAvailabilityStatus.AVAILABLE);
        em.persist(pr);

        AltmanResult ar = new AltmanResult();
        ar.setSecurity(sec);
        ar.setResultDate(LocalDate.now());
        ar.setZone("XOM".equals(symbol) ? AltmanZone.GREY : AltmanZone.SAFE);
        ar.setFormulaVariant(AltmanFormulaVariant.NON_MANUFACTURING);
        ar.setAvailabilityStatus(RiskAvailabilityStatus.AVAILABLE);
        em.persist(ar);

        // FundamentalSnapshot not required for screener query (only for scoring)
        FundamentalSnapshot fs = new FundamentalSnapshot();
        fs.setSecurity(sec);
        fs.setPeriod(Period.ANNUAL);
        fs.setFiscalYear(2025);
        fs.setReportDate(LocalDate.now());
        fs.setRevenue(new BigDecimal("1000000000"));
        em.persist(fs);
    }

    // RM3 (specs/2026-09-02-rm3-screener-security-detail-surfacing/): REIT fixtures reusing the
    // exact RM2-validated values from specs/2026-09-02-rm2-sector-metric-profile/validation.md's
    // live psql inspection, so these tests double as a regression check against real numbers.
    private void seedReitO() {
        seedReitSecurity("O", "Realty Income Corp.",
                new BigDecimal("3.9024"), new BigDecimal("1.9897"),
                new BigDecimal("15.6442"), new BigDecimal("30.6830"),
                new BigDecimal("9.1298"), new BigDecimal("3.1088"),
                new BigDecimal("1.6225"));
    }

    private void seedReitPld() {
        seedReitSecurity("PLD", "Prologis Inc.",
                new BigDecimal("6.3012"), new BigDecimal("4.3824"),
                new BigDecimal("21.8871"), new BigDecimal("31.4702"),
                new BigDecimal("4.7078"), new BigDecimal("7.1804"),
                new BigDecimal("0.8916"));
    }

    private void seedReitSecurity(String symbol, String name,
                                   BigDecimal ffoPerShare, BigDecimal affoPerShare,
                                   BigDecimal priceToFfo, BigDecimal priceToAffo,
                                   BigDecimal netDebtToEbitda, BigDecimal interestCoverageEbitda,
                                   BigDecimal affoPayoutRatio) {
        Security sec = new Security();
        sec.setSymbol(symbol);
        sec.setCompanyName(name);
        sec.setSector("Real Estate");
        sec.setExchange("NYSE");
        em.persist(sec);

        ValuationResult vr = new ValuationResult();
        vr.setSecurity(sec);
        vr.setValuationDate(LocalDate.now());
        vr.setCompositeFairValue(new BigDecimal("60.00"));
        vr.setCurrentPrice(new BigDecimal("55.00"));
        vr.setMarginOfSafety(new BigDecimal("5.00"));
        vr.setRecommendation(Recommendation.QUALITY_VALUE);
        vr.setSource("test");
        em.persist(vr);

        RatioSnapshot rs = new RatioSnapshot();
        rs.setSecurity(sec);
        rs.setPeriod(Period.TTM);
        rs.setReportDate(LocalDate.now());
        rs.setFfoPerShare(ffoPerShare);
        rs.setAffoPerShare(affoPerShare);
        rs.setPriceToFfo(priceToFfo);
        rs.setPriceToAffo(priceToAffo);
        rs.setNetDebtToEbitda(netDebtToEbitda);
        rs.setInterestCoverageEbitda(interestCoverageEbitda);
        rs.setAffoPayoutRatio(affoPayoutRatio);
        em.persist(rs);

        ValueScore vs = new ValueScore();
        vs.setSecurity(sec);
        vs.setScoreDate(LocalDate.now());
        vs.setMosScore(new BigDecimal("20"));
        vs.setQualityScore(BigDecimal.ZERO);
        vs.setSafetyScore(BigDecimal.ZERO);
        vs.setGrowthScore(BigDecimal.ZERO);
        vs.setDividendScore(BigDecimal.ZERO);
        vs.setTotalScore(new BigDecimal("40.00"));
        em.persist(vs);
    }
}
