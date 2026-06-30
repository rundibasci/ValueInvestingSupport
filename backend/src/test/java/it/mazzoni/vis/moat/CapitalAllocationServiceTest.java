package it.mazzoni.vis.moat;

import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.CapitalAllocationResultRepository;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CapitalAllocationServiceTest {
    @Mock SecurityRepository securityRepository;
    @Mock FundamentalSnapshotRepository fundamentalSnapshotRepository;
    @Mock RatioSnapshotRepository ratioSnapshotRepository;
    @Mock CapitalAllocationResultRepository capitalAllocationResultRepository;

    CapitalAllocationService service;
    Security security;

    @BeforeEach
    void setUp() {
        security = new Security();
        security.setSymbol("KO");
        service = new CapitalAllocationService(securityRepository, fundamentalSnapshotRepository,
                ratioSnapshotRepository, capitalAllocationResultRepository);
        lenient().when(securityRepository.findBySymbol("KO")).thenReturn(Optional.of(security));
        lenient().when(capitalAllocationResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void analyze_netDiluter_whenSharesGrowMoreThanTwoPercentAnnually() {
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL))
                .thenReturn(List.of(fundamental(1300), fundamental(1000)));
        when(ratioSnapshotRepository.findTopBySecurityOrderByReportDateDesc(security)).thenReturn(Optional.empty());

        CapitalAllocationResult result = service.analyze("KO");

        assertThat(result.getSharesOutstandingTrend()).isEqualTo(SharesOutstandingTrend.NET_DILUTER);
        assertThat(result.getClassification()).isEqualTo(CapitalAllocatorClassification.NET_DILUTER);
        assertThat(result.getSharesChangePercentage()).isEqualByComparingTo("30.00");
    }

    @Test
    void analyze_disciplined_whenSharesFlatAndDividendPresent() {
        RatioSnapshot ratio = new RatioSnapshot();
        ratio.setDividendYield(new BigDecimal("0.03"));
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL))
                .thenReturn(List.of(fundamental(990), fundamental(1000)));
        when(ratioSnapshotRepository.findTopBySecurityOrderByReportDateDesc(security)).thenReturn(Optional.of(ratio));

        CapitalAllocationResult result = service.analyze("KO");

        assertThat(result.getSharesOutstandingTrend()).isEqualTo(SharesOutstandingTrend.STABLE);
        assertThat(result.getClassification()).isEqualTo(CapitalAllocatorClassification.DISCIPLINED_CAPITAL_ALLOCATOR);
    }

    private FundamentalSnapshot fundamental(long shares) {
        FundamentalSnapshot snapshot = new FundamentalSnapshot();
        snapshot.setSharesOutstanding(shares);
        return snapshot;
    }
}
