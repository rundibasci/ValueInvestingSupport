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
    @Mock RoicObservationRepository roicObservationRepository;
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
                roicObservationRepository,
                stabilityService,
                new ValuationEnhancementProperties(null, null, new BigDecimal("0.09"), null, null, null)
        );
        lenient().when(moatResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void analyze_wideMoat_whenRoicExceedsWaccMostYearsWithStableTrend() {
        when(roicObservationRepository.findBySecurityOrderByFiscalYearDesc(security))
                .thenReturn(List.of(
                        observation("0.18"), observation("0.18"), observation("0.17"), observation("0.17"), observation("0.16"),
                        observation("0.16"), observation("0.15"), observation("0.15"), observation("0.14"), observation("0.14")
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
        when(roicObservationRepository.findBySecurityOrderByFiscalYearDesc(security))
                .thenReturn(List.of(observation("0.12"), observation("0.11")));
        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security)).thenReturn(Optional.empty());

        MoatResult result = service.analyze(security);

        assertThat(result.getMoatStrength()).isEqualTo(MoatStrength.INSUFFICIENT_DATA);
        assertThat(result.getRoicTrend()).isEqualTo(RoicTrend.INSUFFICIENT_DATA);
        assertThat(result.getAvailabilityMessage()).contains("five annual ROIC");
    }

    private RoicObservation observation(String roic) {
        RoicObservation observation = new RoicObservation();
        observation.setRoic(new BigDecimal(roic));
        return observation;
    }
}
