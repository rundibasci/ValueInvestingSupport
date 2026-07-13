package it.mazzoni.vis.professional;

import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DataVerificationServiceTest {
    @Test
    void returnsNoFlagsWhenVerificationPasses() {
        Security security = new Security();
        security.setSymbol("INGR");
        SecurityRepository securities = mock(SecurityRepository.class);
        FundamentalSnapshotRepository fundamentals = mock(FundamentalSnapshotRepository.class);
        when(securities.findBySymbol("INGR")).thenReturn(Optional.of(security));

        FundamentalSnapshot latest = completeSnapshot("2026-07-13", "100", "10");
        FundamentalSnapshot prior = completeSnapshot("2025-07-13", "95", "9");
        when(fundamentals.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL))
                .thenReturn(List.of(latest, prior));

        var response = new DataVerificationService(securities, fundamentals).check("ingr");

        assertThat(response.flags()).isEmpty();
    }

    @Test
    void flagsMissingCriticalFields() {
        Security security = new Security();
        security.setSymbol("MSFT");
        SecurityRepository securities = mock(SecurityRepository.class);
        FundamentalSnapshotRepository fundamentals = mock(FundamentalSnapshotRepository.class);
        when(securities.findBySymbol("MSFT")).thenReturn(Optional.of(security));

        FundamentalSnapshot latest = new FundamentalSnapshot();
        latest.setReportDate(LocalDate.now());
        latest.setRevenue(new BigDecimal("100"));
        FundamentalSnapshot prior = new FundamentalSnapshot();
        prior.setRevenue(new BigDecimal("90"));
        when(fundamentals.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL))
                .thenReturn(List.of(latest, prior));

        var response = new DataVerificationService(securities, fundamentals).check("msft");

        assertThat(response.flags()).anyMatch(flag -> flag.field().equals("criticalFields"));
    }

    private FundamentalSnapshot completeSnapshot(String reportDate, String revenue, String eps) {
        FundamentalSnapshot snapshot = new FundamentalSnapshot();
        snapshot.setReportDate(LocalDate.parse(reportDate));
        snapshot.setRevenue(new BigDecimal(revenue));
        snapshot.setEps(new BigDecimal(eps));
        snapshot.setTotalEquity(new BigDecimal("50"));
        snapshot.setFreeCashFlow(new BigDecimal("8"));
        snapshot.setSharesOutstanding(10_000_000L);
        return snapshot;
    }
}
