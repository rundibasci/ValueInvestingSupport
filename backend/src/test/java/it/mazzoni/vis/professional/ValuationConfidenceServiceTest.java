package it.mazzoni.vis.professional;

import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ValuationConfidenceServiceTest {
    @Test
    void computesHighConfidenceWhenHistoryModelsAndDataAreAvailable() {
        Security security = new Security();
        security.setSymbol("KO");
        SecurityRepository securities = mock(SecurityRepository.class);
        FundamentalSnapshotRepository fundamentals = mock(FundamentalSnapshotRepository.class);
        ValuationResultRepository valuations = mock(ValuationResultRepository.class);
        RatioSnapshotRepository ratios = mock(RatioSnapshotRepository.class);
        when(securities.findBySymbol("KO")).thenReturn(Optional.of(security));

        FundamentalSnapshot snapshot = new FundamentalSnapshot();
        snapshot.setEps(new BigDecimal("2.10"));
        snapshot.setFreeCashFlow(new BigDecimal("1000"));
        snapshot.setSharesOutstanding(100L);
        snapshot.setNetIncome(new BigDecimal("900"));
        when(fundamentals.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL))
                .thenReturn(List.of(snapshot, snapshot, snapshot, snapshot, snapshot, snapshot, snapshot, snapshot, snapshot, snapshot));
        when(fundamentals.findTopBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL))
                .thenReturn(Optional.of(snapshot));
        when(ratios.findTopBySecurityOrderByReportDateDesc(security)).thenReturn(Optional.of(new RatioSnapshot()));

        ValuationResult valuation = new ValuationResult();
        valuation.setDcfFairValue(new BigDecimal("100"));
        valuation.setDcfFairValueLow(new BigDecimal("95"));
        valuation.setDcfFairValueHigh(new BigDecimal("110"));
        valuation.setGrahamNumber(new BigDecimal("90"));
        valuation.setDdmFairValue(new BigDecimal("80"));
        when(valuations.findTopBySecurityOrderByValuationDateDesc(security)).thenReturn(Optional.of(valuation));

        var response = new ValuationConfidenceService(securities, fundamentals, valuations, ratios).compute("ko");

        assertThat(response.symbol()).isEqualTo("KO");
        assertThat(response.overallLevel()).isEqualTo("HIGH");
        assertThat(response.factors()).hasSize(5);
    }

    @Test
    void returnsLowConfidenceForUnseededSymbol() {
        SecurityRepository securities = mock(SecurityRepository.class);
        when(securities.findBySymbol("XYZ")).thenReturn(Optional.empty());

        var response = new ValuationConfidenceService(securities, mock(FundamentalSnapshotRepository.class),
                mock(ValuationResultRepository.class), mock(RatioSnapshotRepository.class)).compute("xyz");

        assertThat(response.overallLevel()).isEqualTo("LOW");
        assertThat(response.factors().getFirst().message()).contains("not seeded");
    }
}
