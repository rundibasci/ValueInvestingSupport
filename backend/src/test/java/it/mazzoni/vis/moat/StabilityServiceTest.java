package it.mazzoni.vis.moat;

import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.StabilityResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StabilityServiceTest {
    @Mock SecurityRepository securityRepository;
    @Mock FundamentalSnapshotRepository fundamentalSnapshotRepository;
    @Mock StabilityResultRepository stabilityResultRepository;

    StabilityService service;
    Security security;

    @BeforeEach
    void setUp() {
        security = new Security();
        security.setSymbol("JNJ");
        service = new StabilityService(securityRepository, fundamentalSnapshotRepository, stabilityResultRepository);
        lenient().when(securityRepository.findBySymbol("JNJ")).thenReturn(Optional.of(security));
        lenient().when(stabilityResultRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void assess_marksNoNegativeEpsPassAndLargeDeclineFail() {
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL))
                .thenReturn(List.of(
                        annual(2025, "1.20", "120"),
                        annual(2024, "2.00", "115"),
                        annual(2023, "2.20", "112"),
                        annual(2022, "2.10", "109"),
                        annual(2021, "2.00", "106"),
                        annual(2020, "1.90", "103"),
                        annual(2019, "1.80", "100"),
                        annual(2018, "1.70", "98"),
                        annual(2017, "1.60", "96"),
                        annual(2016, "1.50", "94")
                ));

        List<StabilityResult> results = service.assess("JNJ");

        assertThat(status(results, "no_negative_eps_10y")).isEqualTo("PASS");
        assertThat(status(results, "no_large_eps_decline")).isEqualTo("FAIL");
        assertThat(status(results, "positive_revenue_growth_10y")).isEqualTo("PASS");
    }

    private String status(List<StabilityResult> results, String code) {
        return results.stream().filter(r -> r.getCriterionCode().equals(code)).findFirst().orElseThrow().getStatus();
    }

    private FundamentalSnapshot annual(int year, String eps, String revenue) {
        FundamentalSnapshot snapshot = new FundamentalSnapshot();
        snapshot.setFiscalYear(year);
        snapshot.setReportDate(LocalDate.of(year, 12, 31));
        snapshot.setEps(new BigDecimal(eps));
        snapshot.setRevenue(new BigDecimal(revenue));
        return snapshot;
    }
}
