package it.mazzoni.vis.moat;

import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.RoicObservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoicObservationServiceTest {
    @Mock RatioSnapshotRepository ratios;
    @Mock FundamentalSnapshotRepository fundamentals;
    @Mock RoicObservationRepository observations;
    RoicObservationService service;
    Security security;

    @BeforeEach
    void setUp() {
        service = new RoicObservationService(ratios, fundamentals, observations, new DerivedRoicCalculator());
        security = new Security();
        security.setSymbol("INGR");
        lenient().when(observations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(observations.findBySecurityOrderByFiscalYearDesc(security)).thenReturn(List.of());
    }

    @Test
    void prefersProviderHistoryAtFiveObservationThreshold() {
        when(ratios.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL))
                .thenReturn(List.of(ratio(2025), ratio(2024), ratio(2023), ratio(2022), ratio(2021)));

        service.refreshAfterIngestion(security, "FMP");

        ArgumentCaptor<RoicObservation> captor = ArgumentCaptor.forClass(RoicObservation.class);
        verify(observations, times(5)).save(captor.capture());
        assertThat(captor.getAllValues()).allMatch(item -> item.getSource() == RoicSource.FMP_KEY_METRIC);
        verifyNoInteractions(fundamentals);
    }

    @Test
    void derivesSeriesWhenProviderHistoryIsTooShallow() {
        when(ratios.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL))
                .thenReturn(List.of(ratio(2025)));
        when(fundamentals.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL))
                .thenReturn(List.of(fundamental(2025), fundamental(2024), fundamental(2023), fundamental(2022),
                        fundamental(2021), fundamental(2020)));

        service.refreshAfterIngestion(security, "FMP");

        ArgumentCaptor<RoicObservation> captor = ArgumentCaptor.forClass(RoicObservation.class);
        verify(observations, times(6)).save(captor.capture());
        assertThat(captor.getAllValues().stream().filter(item -> item.getSource() == RoicSource.DERIVED_INTERNAL)).hasSize(5);
        assertThat(captor.getAllValues()).anyMatch(item -> "MISSING_OPENING_INVESTED_CAPITAL".equals(item.getUnavailableReason()));
    }

    private RatioSnapshot ratio(int year) {
        RatioSnapshot ratio = new RatioSnapshot();
        ratio.setRoic(new BigDecimal("0.12"));
        ratio.setReportDate(LocalDate.of(year, 12, 31));
        return ratio;
    }

    private FundamentalSnapshot fundamental(int year) {
        FundamentalSnapshot snapshot = new FundamentalSnapshot();
        snapshot.setFiscalYear(year);
        snapshot.setReportDate(LocalDate.of(year, 12, 31));
        snapshot.setOperatingIncome(new BigDecimal("100"));
        snapshot.setTotalEquity(new BigDecimal("250"));
        snapshot.setTotalDebt(new BigDecimal("100"));
        snapshot.setCash(new BigDecimal("50"));
        return snapshot;
    }
}
