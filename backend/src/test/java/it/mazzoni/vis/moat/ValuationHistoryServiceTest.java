package it.mazzoni.vis.moat;

import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.ValuationBandResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValuationHistoryServiceTest {
    @Mock SecurityRepository securityRepository;
    @Mock RatioSnapshotRepository ratioSnapshotRepository;
    @Mock ValuationBandResultRepository valuationBandResultRepository;

    ValuationHistoryService service;
    Security security;

    @BeforeEach
    void setUp() {
        security = new Security();
        security.setSymbol("MSFT");
        service = new ValuationHistoryService(securityRepository, ratioSnapshotRepository, valuationBandResultRepository);
        lenient().when(valuationBandResultRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void compute_marksLowPeHistoricallyCheap() {
        when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL))
                .thenReturn(List.of(
                        ratio("8", "2", "10", "0.02"),
                        ratio("12", "2", "10", "0.02"),
                        ratio("15", "2", "10", "0.02"),
                        ratio("20", "2", "10", "0.02"),
                        ratio("25", "2", "10", "0.02")
                ));

        List<ValuationBandResult> results = service.compute(security);

        ValuationBandResult pe = results.stream().filter(r -> r.getMetric().equals("PE")).findFirst().orElseThrow();
        assertThat(pe.getPosition()).isEqualTo(ValuationBandPosition.HISTORICALLY_CHEAP);
        assertThat(pe.getPercentile25()).isEqualByComparingTo("12");
        assertThat(pe.getPercentile75()).isEqualByComparingTo("20");
    }

    // RM2 (specs/sector-aware-valuation-metrics.md §4.3): P_FFO band is additive — a REIT fixture
    // with priceToFfo populated produces a real band, mirroring the PE band's own assertion shape.
    @Test
    void compute_reitFixture_producesPFfoBand() {
        when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL))
                .thenReturn(List.of(
                        ratioWithPriceToFfo("10"),
                        ratioWithPriceToFfo("14"),
                        ratioWithPriceToFfo("16"),
                        ratioWithPriceToFfo("18"),
                        ratioWithPriceToFfo("22")
                ));

        List<ValuationBandResult> results = service.compute(security);

        ValuationBandResult pFfo = results.stream().filter(r -> r.getMetric().equals("P_FFO")).findFirst().orElseThrow();
        assertThat(pFfo.getPosition()).isEqualTo(ValuationBandPosition.HISTORICALLY_CHEAP);
        assertThat(pFfo.getPercentile25()).isEqualByComparingTo("14");
        assertThat(pFfo.getPercentile75()).isEqualByComparingTo("18");
    }

    // RM2: a non-REIT fixture (priceToFfo null on every row, matching SectorMetricService's
    // no-op for a non-REIT security) must resolve P_FFO to INSUFFICIENT_DATA — zero code change
    // required to the non-REIT path for this to hold, confirming Group 6 is truly additive.
    @Test
    void compute_nonReitFixture_pFfoIsInsufficientData() {
        when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL))
                .thenReturn(List.of(
                        ratio("8", "2", "10", "0.02"),
                        ratio("12", "2", "10", "0.02"),
                        ratio("15", "2", "10", "0.02")
                ));

        List<ValuationBandResult> results = service.compute(security);

        ValuationBandResult pFfo = results.stream().filter(r -> r.getMetric().equals("P_FFO")).findFirst().orElseThrow();
        assertThat(pFfo.getPosition()).isEqualTo(ValuationBandPosition.INSUFFICIENT_DATA);
        assertThat(pFfo.getYearsAnalyzed()).isZero();
    }

    // RM5 (specs/2026-09-03-rm5-reit-composite-fair-value/): P_AFFO band is additive, same
    // mechanism as P_FFO above — a REIT fixture with priceToAffo populated produces a real band.
    @Test
    void compute_reitFixture_producesPAffoBand() {
        when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL))
                .thenReturn(List.of(
                        ratioWithPriceToAffo("10"),
                        ratioWithPriceToAffo("14"),
                        ratioWithPriceToAffo("16"),
                        ratioWithPriceToAffo("18"),
                        ratioWithPriceToAffo("22")
                ));

        List<ValuationBandResult> results = service.compute(security);

        ValuationBandResult pAffo = results.stream().filter(r -> r.getMetric().equals("P_AFFO")).findFirst().orElseThrow();
        assertThat(pAffo.getPosition()).isEqualTo(ValuationBandPosition.HISTORICALLY_CHEAP);
        assertThat(pAffo.getPercentile25()).isEqualByComparingTo("14");
        assertThat(pAffo.getPercentile75()).isEqualByComparingTo("18");
        assertThat(pAffo.getMedianValue()).isEqualByComparingTo("16");
    }

    // RM5: a non-REIT fixture (priceToAffo null on every row, matching SectorMetricService's
    // no-op for a non-REIT security) must resolve P_AFFO to INSUFFICIENT_DATA.
    @Test
    void compute_nonReitFixture_pAffoIsInsufficientData() {
        when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL))
                .thenReturn(List.of(
                        ratio("8", "2", "10", "0.02"),
                        ratio("12", "2", "10", "0.02"),
                        ratio("15", "2", "10", "0.02")
                ));

        List<ValuationBandResult> results = service.compute(security);

        ValuationBandResult pAffo = results.stream().filter(r -> r.getMetric().equals("P_AFFO")).findFirst().orElseThrow();
        assertThat(pAffo.getPosition()).isEqualTo(ValuationBandPosition.INSUFFICIENT_DATA);
        assertThat(pAffo.getYearsAnalyzed()).isZero();
    }

    private RatioSnapshot ratio(String pe, String pb, String ev, String yield) {
        RatioSnapshot ratio = new RatioSnapshot();
        ratio.setPeRatio(new BigDecimal(pe));
        ratio.setPbRatio(new BigDecimal(pb));
        ratio.setEvToEbitda(new BigDecimal(ev));
        ratio.setDividendYield(new BigDecimal(yield));
        return ratio;
    }

    private RatioSnapshot ratioWithPriceToFfo(String priceToFfo) {
        RatioSnapshot ratio = new RatioSnapshot();
        ratio.setPriceToFfo(new BigDecimal(priceToFfo));
        return ratio;
    }

    private RatioSnapshot ratioWithPriceToAffo(String priceToAffo) {
        RatioSnapshot ratio = new RatioSnapshot();
        ratio.setPriceToAffo(new BigDecimal(priceToAffo));
        return ratio;
    }
}
