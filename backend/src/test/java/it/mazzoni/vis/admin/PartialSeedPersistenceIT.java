package it.mazzoni.vis.admin;

import it.mazzoni.vis.domain.CompanyProfile;
import it.mazzoni.vis.auth.JwtService;
import it.mazzoni.vis.domain.FundamentalSnapshot;
import it.mazzoni.vis.domain.MarketPriceQuote;
import it.mazzoni.vis.domain.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.marketdata.MarketDataClient;
import it.mazzoni.vis.marketdata.SourceTracker;
import it.mazzoni.vis.moat.CapitalAllocationService;
import it.mazzoni.vis.moat.MoatAssessmentService;
import it.mazzoni.vis.scoring.RiskAnalysisService;
import it.mazzoni.vis.scoring.ValueScoreService;
import it.mazzoni.vis.valuation.ValuationNotApplicableException;
import it.mazzoni.vis.valuation.ValuationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("partial-seed-test")
class PartialSeedPersistenceIT {
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean MarketDataClient marketDataClient;
    @MockitoBean ValuationService valuationService;
    @MockitoBean ValueScoreService valueScoreService;
    @MockitoBean RiskAnalysisService riskAnalysisService;
    @MockitoBean MoatAssessmentService moatAssessmentService;
    @MockitoBean CapitalAllocationService capitalAllocationService;
    @MockitoBean SourceTracker sourceTracker;
    @MockitoBean StringRedisTemplate redisTemplate;
    @MockitoBean JwtService jwtService;

    @Autowired SeedService seedService;
    @Autowired SecurityRepository securityRepository;
    @Autowired FundamentalSnapshotRepository fundamentalRepository;
    @Autowired RatioSnapshotRepository ratioRepository;
    @Autowired PriceQuoteRepository quoteRepository;

    @BeforeEach
    void clean() {
        quoteRepository.deleteAll();
        ratioRepository.deleteAll();
        fundamentalRepository.deleteAll();
        securityRepository.deleteAll();
    }

    @Test
    void valuationGuardrail_commitsMarketDataAsPartial() {
        stubMarketData("APD");
        when(valuationService.calculate(eq("APD"), any())).thenThrow(new ValuationNotApplicableException("APD"));
        when(sourceTracker.summarize()).thenReturn("profile:fmp,fundamentals:yahoo,ratios:fmp,quote:fmp");

        SeedResult result = seedService.seedTickers(List.of("APD")).getFirst();

        assertThat(result.status()).isEqualTo("seeded_partial");
        assertThat(result.compositeFairValue()).isNull();
        var security = securityRepository.findBySymbol("APD").orElseThrow();
        assertThat(security.isActive()).isTrue();
        assertThat(fundamentalRepository.findTopBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL)).isPresent();
        assertThat(ratioRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL)).isNotEmpty();
        assertThat(quoteRepository.findTopBySecurityOrderByQuoteDateDesc(security)).isPresent();
    }

    @Test
    void unexpectedFailure_rollsBackNewSecurityAndSnapshots() {
        when(marketDataClient.getProfile("BROKEN")).thenReturn(profile("BROKEN"));
        when(marketDataClient.getFundamentals("BROKEN")).thenThrow(new IllegalStateException("fixture failure"));

        SeedResult result = seedService.seedTickers(List.of("BROKEN")).getFirst();

        assertThat(result.status()).isEqualTo("failed");
        assertThat(securityRepository.findBySymbol("BROKEN")).isEmpty();
        assertThat(fundamentalRepository.count()).isZero();
        assertThat(ratioRepository.count()).isZero();
        assertThat(quoteRepository.count()).isZero();
    }

    private void stubMarketData(String symbol) {
        when(marketDataClient.getProfile(symbol)).thenReturn(profile(symbol));
        when(marketDataClient.getFundamentals(symbol)).thenReturn(new FundamentalSnapshot(
                symbol, "Partial Company", "Industrials", "Chemicals", "US", "USD",
                new BigDecimal("100.00"), null, null, 1_000_000L,
                List.of(new BigDecimal("1000000")), List.of(), List.of(),
                new BigDecimal("100000"), BigDecimal.ZERO, new BigDecimal("500000")));
        RatioSnapshot ratios = new RatioSnapshot(symbol, null, null, null, null, null,
                new BigDecimal("1.5"), new BigDecimal("1.1"), null, null, null, null, null, null, null);
        when(marketDataClient.getAnnualRatios(symbol)).thenReturn(List.of(ratios));
        when(marketDataClient.getQuote(symbol)).thenReturn(new MarketPriceQuote(
                symbol, new BigDecimal("100.00"), "USD", null, null, 1000L));
        when(marketDataClient.getDividendHistory(symbol)).thenReturn(List.of());
        when(marketDataClient.getInsiderTransactions(symbol)).thenReturn(List.of());
    }

    private CompanyProfile profile(String symbol) {
        return new CompanyProfile(symbol, "Partial Company", "Industrials", "Chemicals", "US", "USD",
                "NYSE", new BigDecimal("1000000000"), "Provider-valid partial company.", "https://example.com");
    }
}
