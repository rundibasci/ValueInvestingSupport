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
}
