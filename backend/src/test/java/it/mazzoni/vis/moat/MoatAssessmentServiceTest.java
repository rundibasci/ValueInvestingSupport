package it.mazzoni.vis.moat;

import it.mazzoni.vis.config.ValuationEnhancementProperties;
import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.*;
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
class MoatAssessmentServiceTest {
    @Mock SecurityRepository securityRepository;
    @Mock RatioSnapshotRepository ratioSnapshotRepository;
    @Mock FundamentalSnapshotRepository fundamentalSnapshotRepository;
    @Mock ValuationResultRepository valuationResultRepository;
    @Mock WaccResultRepository waccResultRepository;
    @Mock MoatResultRepository moatResultRepository;
    @Mock StabilityService stabilityService;

    MoatAssessmentService service;
    Security security;

    @BeforeEach
    void setUp() {
        security = new Security();
        security.setSymbol("AAPL");
        service = new MoatAssessmentService(
                securityRepository,
                ratioSnapshotRepository,
                fundamentalSnapshotRepository,
                valuationResultRepository,
                waccResultRepository,
                moatResultRepository,
                stabilityService,
                new ValuationEnhancementProperties(null, null, new BigDecimal("0.09"), null, null, null)
        );
        lenient().when(moatResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void analyze_wideMoat_whenRoicExceedsWaccMostYearsWithStableTrend() {
        when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL))
                .thenReturn(List.of(
                        ratio("0.18"), ratio("0.18"), ratio("0.17"), ratio("0.17"), ratio("0.16"),
                        ratio("0.16"), ratio("0.15"), ratio("0.15"), ratio("0.14"), ratio("0.14")
                ));
        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security)).thenReturn(Optional.empty());
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL))
                .thenReturn(List.of());

        MoatResult result = service.analyze(security);

        assertThat(result.getMoatStrength()).isEqualTo(MoatStrength.WIDE);
        assertThat(result.getRoicTrend()).isIn(RoicTrend.STABLE, RoicTrend.IMPROVING);
        assertThat(result.getYearsRoicAboveWacc()).isEqualTo(10);
        assertThat(result.getRoicConsistencyPercentage()).isEqualByComparingTo("100.00");
    }

    @Test
    void analyze_insufficient_whenFewerThanFiveRoicYears() {
        when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL))
                .thenReturn(List.of(ratio("0.12"), ratio("0.11")));
        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security)).thenReturn(Optional.empty());

        MoatResult result = service.analyze(security);

        assertThat(result.getMoatStrength()).isEqualTo(MoatStrength.INSUFFICIENT_DATA);
        assertThat(result.getRoicTrend()).isEqualTo(RoicTrend.INSUFFICIENT_DATA);
        assertThat(result.getAvailabilityMessage()).contains("five annual ROIC");
    }

    private RatioSnapshot ratio(String roic) {
        RatioSnapshot ratio = new RatioSnapshot();
        ratio.setRoic(new BigDecimal(roic));
        return ratio;
    }
}
