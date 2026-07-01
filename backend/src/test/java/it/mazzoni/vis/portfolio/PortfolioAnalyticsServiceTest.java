package it.mazzoni.vis.portfolio;

import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.*;
import it.mazzoni.vis.portfolio.dto.BenchmarkComparisonResponse;
import it.mazzoni.vis.portfolio.dto.LiquidityResult;
import it.mazzoni.vis.portfolio.dto.PortfolioAnalyticsResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioAnalyticsServiceTest {
    @Mock PortfolioRepository portfolios; @Mock HoldingRepository holdings; @Mock UserRepository users;
    @Mock SecurityRepository securities; @Mock PriceQuoteRepository quotes; @Mock ValuationResultRepository valuations;
    @Mock RatioSnapshotRepository ratios; @Mock ValueScoreRepository scores; @Mock PiotroskiResultRepository piotroski;
    @Mock MoatResultRepository moats; @Mock EarningsQualityResultRepository earningsQuality;
    @Mock PortfolioAnalyticsSnapshotRepository snapshots; @Mock LiquidityService liquidity; @Mock BenchmarkService benchmarks;

    @Test
    void computesWeightedMetricsAndConcentrationWarnings() {
        PortfolioAnalyticsService service = new PortfolioAnalyticsService(portfolios, holdings, users, securities,
                quotes, valuations, ratios, scores, piotroski, moats, earningsQuality, snapshots, liquidity, benchmarks);
        UUID portfolioId = UUID.randomUUID();
        User user = new User(); user.setEmail("investor@example.com");
        Portfolio portfolio = new Portfolio(); portfolio.setId(portfolioId); portfolio.setUser(user);
        Holding aaa = holding(portfolio, "AAA", "9");
        Holding bbb = holding(portfolio, "BBB", "1");
        Security secA = security("AAA", "Technology");
        Security secB = security("BBB", "Healthcare");
        PortfolioAnalyticsSnapshot saved = new PortfolioAnalyticsSnapshot();
        saved.setPortfolio(portfolio);
        saved.setBenchmarkSymbol("SPY");

        when(users.findByEmail("investor@example.com")).thenReturn(Optional.of(user));
        when(portfolios.findByIdAndUser(portfolioId, user)).thenReturn(Optional.of(portfolio));
        when(holdings.findByPortfolio(portfolio)).thenReturn(List.of(aaa, bbb));
        when(securities.findBySymbol("AAA")).thenReturn(Optional.of(secA));
        when(securities.findBySymbol("BBB")).thenReturn(Optional.of(secB));
        stubInputs(secA, "100", "20", "10", "80", 8, MoatStrength.WIDE);
        stubInputs(secB, "100", "30", "0", "40", 4, MoatStrength.NONE);
        when(liquidity.assess(eq("AAA"), any())).thenReturn(new LiquidityResult("AAA", BigDecimal.TEN, BigDecimal.ONE, "LIQUID", "AVAILABLE"));
        when(liquidity.assess(eq("BBB"), any())).thenReturn(new LiquidityResult("BBB", BigDecimal.TEN, BigDecimal.ONE, "LIQUID", "AVAILABLE"));
        when(benchmarks.compare(any(), any(), eq("SPY"))).thenReturn(new BenchmarkComparisonResponse("SPY",
                new BigDecimal("21.00"), null, new BigDecimal("9.00"), null,
                new BigDecimal("19.00"), null, Map.of(), "BENCHMARK_DATA_UNAVAILABLE"));
        when(snapshots.save(any())).thenReturn(saved);

        PortfolioAnalyticsResponse response = service.analyze(
                new UsernamePasswordAuthenticationToken("investor@example.com", "n/a"), portfolioId);

        assertEquals(new BigDecimal("1000.00"), response.totalMarketValue());
        assertEquals(new BigDecimal("21.00"), response.weightedMetrics().peRatio());
        assertEquals(new BigDecimal("9.00"), response.weightedMetrics().marginOfSafety());
        assertTrue(response.sectorConcentrationFlags().contains("Technology"));
        assertTrue(response.holdingConcentration().stream().anyMatch(item -> "CONCENTRATED".equals(item.status())));
        verify(snapshots).save(any(PortfolioAnalyticsSnapshot.class));
    }

    private Holding holding(Portfolio portfolio, String symbol, String quantity) {
        Holding holding = new Holding();
        holding.setPortfolio(portfolio);
        holding.setSymbol(symbol);
        holding.setQuantity(new BigDecimal(quantity));
        return holding;
    }

    private Security security(String symbol, String sector) {
        Security security = new Security();
        security.setSymbol(symbol);
        security.setCompanyName(symbol);
        security.setSector(sector);
        return security;
    }

    private void stubInputs(Security security, String price, String pe, String mos, String score, int fScore, MoatStrength moatStrength) {
        PriceQuote quote = new PriceQuote(); quote.setClose(new BigDecimal(price));
        RatioSnapshot ratio = new RatioSnapshot(); ratio.setPeriod(Period.ANNUAL); ratio.setReportDate(LocalDate.now());
        ratio.setPeRatio(new BigDecimal(pe)); ratio.setDividendYield(BigDecimal.TEN); ratio.setRoic(BigDecimal.ONE); ratio.setRoe(BigDecimal.ONE);
        ValuationResult valuation = new ValuationResult(); valuation.setMarginOfSafety(new BigDecimal(mos));
        ValueScore valueScore = new ValueScore(); valueScore.setTotalScore(new BigDecimal(score));
        PiotroskiResult piotroskiResult = new PiotroskiResult(); piotroskiResult.setTotalScore(fScore);
        MoatResult moat = new MoatResult(); moat.setMoatStrength(moatStrength);
        EarningsQualityResult quality = new EarningsQualityResult(); quality.setClassification(EarningsQualityClassification.ACCEPTABLE);
        when(quotes.findTopBySecurityOrderByQuoteDateDesc(security)).thenReturn(Optional.of(quote));
        when(ratios.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL)).thenReturn(List.of(ratio));
        when(valuations.findTopBySecurityOrderByValuationDateDesc(security)).thenReturn(Optional.of(valuation));
        when(scores.findTopBySecurityOrderByScoreDateDesc(security)).thenReturn(Optional.of(valueScore));
        when(piotroski.findTopBySecurityOrderByResultDateDesc(security)).thenReturn(Optional.of(piotroskiResult));
        when(moats.findTopBySecurityOrderByResultDateDesc(security)).thenReturn(Optional.of(moat));
        when(earningsQuality.findTopBySecurityOrderByResultDateDesc(security)).thenReturn(Optional.of(quality));
    }
}
