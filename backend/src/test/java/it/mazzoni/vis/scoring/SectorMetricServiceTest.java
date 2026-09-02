package it.mazzoni.vis.scoring;

import it.mazzoni.vis.config.ValuationEnhancementProperties;
import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectorMetricServiceTest {

    @Mock SecurityRepository securityRepository;
    @Mock RatioSnapshotRepository ratioSnapshotRepository;
    @Mock FundamentalSnapshotRepository fundamentalSnapshotRepository;
    @Mock PriceQuoteRepository priceQuoteRepository;

    // Real instance (all-default 0.70 maintenance-capex-depreciation-ratio) — no behavior to mock.
    ValuationEnhancementProperties valuationEnhancementProperties =
            new ValuationEnhancementProperties(null, null, null, null, null, null);

    SectorMetricService service;
    Security security;

    @BeforeEach
    void setUp() {
        service = new SectorMetricService(securityRepository, ratioSnapshotRepository,
                fundamentalSnapshotRepository, priceQuoteRepository, valuationEnhancementProperties);
        security = new Security();
        security.setSymbol("O");
    }

    @Test
    void compute_nonReitSecurity_isNoOp() {
        security.setSector("Technology");

        service.compute(security);

        Mockito.verifyNoInteractions(ratioSnapshotRepository, fundamentalSnapshotRepository, priceQuoteRepository);
    }

    @Test
    void compute_reitSecurity_ttmRow_computesKnownValues() {
        security.setSector("REIT - Retail");
        LocalDate reportDate = LocalDate.of(2026, 9, 1);

        RatioSnapshot ttmRow = new RatioSnapshot();
        ttmRow.setPeriod(Period.TTM);
        ttmRow.setReportDate(reportDate);
        ttmRow.setPayoutRatio(new BigDecimal("0.90")); // GAAP payout ratio (>100% net-income-based is common for REITs; kept ≤1 here for a clean expected value)
        when(ratioSnapshotRepository.findBySecurity(security)).thenReturn(List.of(ttmRow));

        FundamentalSnapshot fundamentals = new FundamentalSnapshot();
        fundamentals.setNetIncome(new BigDecimal("40"));
        fundamentals.setDepreciationAndAmortization(new BigDecimal("60")); // FFO = 100
        fundamentals.setSharesOutstanding(50L);                            // FFO/share = 2.00
        fundamentals.setEps(new BigDecimal("0.80"));
        fundamentals.setTotalDebt(new BigDecimal("550"));
        fundamentals.setCash(new BigDecimal("50"));                        // net debt = 500
        fundamentals.setEbitda(new BigDecimal("100"));                     // net debt/EBITDA = 5.0
        fundamentals.setInterestExpense(new BigDecimal("25"));             // interest coverage = 4.0
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodAndReportDate(security, Period.TTM, reportDate))
                .thenReturn(Optional.of(fundamentals));

        PriceQuote quote = new PriceQuote();
        quote.setClose(new BigDecimal("30.00"));
        when(priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(security)).thenReturn(Optional.of(quote));
        when(ratioSnapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.compute(security);

        // maintenance capex/share = D&A(60) * 0.70 / shares(50) = 0.84; AFFO/share = 2.00 - 0.84 = 1.16
        assertThat(ttmRow.getFfoPerShare()).isEqualByComparingTo("2.0000");
        assertThat(ttmRow.getAffoPerShare()).isEqualByComparingTo("1.1600");
        // P/FFO = 30.00 / 2.00 = 15; P/AFFO = 30.00 / 1.16 ≈ 25.8621
        assertThat(ttmRow.getPriceToFfo()).isEqualByComparingTo("15.0000");
        assertThat(ttmRow.getPriceToAffo()).isEqualByComparingTo(new BigDecimal("30.00").divide(new BigDecimal("1.1600"), 4, java.math.RoundingMode.HALF_UP));
        assertThat(ttmRow.getNetDebtToEbitda()).isEqualByComparingTo("5.0000");
        // dividendPerShare = payoutRatio(0.90) * eps(0.80) = 0.72; AFFO payout = 0.72 / 1.16
        assertThat(ttmRow.getAffoPayoutRatio()).isEqualByComparingTo(
                new BigDecimal("0.72").divide(new BigDecimal("1.1600"), 4, java.math.RoundingMode.HALF_UP));
        assertThat(ttmRow.getInterestCoverageEbitda()).isEqualByComparingTo("4.0000"); // 100/25
        verify(ratioSnapshotRepository).save(ttmRow);
    }

    @Test
    void compute_reitSecurity_annualRow_derivesImpliedPriceFromPeRatioAndEps() {
        security.setSector("Real Estate");
        LocalDate reportDate = LocalDate.of(2023, 1, 1);

        RatioSnapshot annualRow = new RatioSnapshot();
        annualRow.setPeriod(Period.ANNUAL);
        annualRow.setReportDate(reportDate);
        annualRow.setPeRatio(new BigDecimal("20"));
        when(ratioSnapshotRepository.findBySecurity(security)).thenReturn(List.of(annualRow));

        FundamentalSnapshot fundamentals = new FundamentalSnapshot();
        fundamentals.setNetIncome(new BigDecimal("40"));
        fundamentals.setDepreciationAndAmortization(new BigDecimal("60"));
        fundamentals.setSharesOutstanding(50L);
        fundamentals.setEps(new BigDecimal("2.00")); // impliedPrice = 20 * 2.00 = 40.00
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodAndReportDate(security, Period.ANNUAL, reportDate))
                .thenReturn(Optional.of(fundamentals));
        when(priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(security)).thenReturn(Optional.empty());
        when(ratioSnapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.compute(security);

        // ffoPerShare = (40+60)/50 = 2.00; P/FFO = 40.00 / 2.00 = 20.00
        assertThat(annualRow.getPriceToFfo()).isEqualByComparingTo("20.0000");
    }

    @Test
    void compute_noMatchingFundamentalSnapshot_leavesFieldsNull() {
        security.setSector("REIT");
        RatioSnapshot row = new RatioSnapshot();
        row.setPeriod(Period.TTM);
        row.setReportDate(LocalDate.now());
        when(ratioSnapshotRepository.findBySecurity(security)).thenReturn(List.of(row));
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodAndReportDate(any(), any(), any()))
                .thenReturn(Optional.empty());

        service.compute(security);

        assertThat(row.getFfoPerShare()).isNull();
        verify(ratioSnapshotRepository, never()).save(any());
    }

    @Test
    void compute_ebitdaNonPositive_netDebtToEbitdaStaysNull() {
        security.setSector("REIT");
        LocalDate reportDate = LocalDate.now();
        RatioSnapshot row = new RatioSnapshot();
        row.setPeriod(Period.TTM);
        row.setReportDate(reportDate);
        when(ratioSnapshotRepository.findBySecurity(security)).thenReturn(List.of(row));

        FundamentalSnapshot fundamentals = new FundamentalSnapshot();
        fundamentals.setTotalDebt(new BigDecimal("500"));
        fundamentals.setCash(new BigDecimal("50"));
        fundamentals.setEbitda(BigDecimal.ZERO);
        fundamentals.setNetIncome(new BigDecimal("10"));
        fundamentals.setDepreciationAndAmortization(new BigDecimal("5"));
        fundamentals.setSharesOutstanding(10L);
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodAndReportDate(security, Period.TTM, reportDate))
                .thenReturn(Optional.of(fundamentals));
        when(priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(security)).thenReturn(Optional.empty());
        when(ratioSnapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.compute(security);

        assertThat(row.getNetDebtToEbitda()).isNull();
        assertThat(row.getInterestCoverageEbitda()).isNull(); // interestExpense not set on this fixture
    }

    @Test
    void compute_interestExpenseZero_interestCoverageEbitdaStaysNull() {
        security.setSector("REIT");
        LocalDate reportDate = LocalDate.now();
        RatioSnapshot row = new RatioSnapshot();
        row.setPeriod(Period.TTM);
        row.setReportDate(reportDate);
        when(ratioSnapshotRepository.findBySecurity(security)).thenReturn(List.of(row));

        FundamentalSnapshot fundamentals = new FundamentalSnapshot();
        fundamentals.setEbitda(new BigDecimal("100"));
        fundamentals.setInterestExpense(BigDecimal.ZERO); // undrawn/no debt — never a divide-by-zero
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodAndReportDate(security, Period.TTM, reportDate))
                .thenReturn(Optional.of(fundamentals));
        when(priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(security)).thenReturn(Optional.empty());
        when(ratioSnapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.compute(security);

        assertThat(row.getInterestCoverageEbitda()).isNull();
    }
}
