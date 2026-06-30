package it.mazzoni.vis.scoring;

import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskAnalysisServiceTest {
    @Mock SecurityRepository securityRepository;
    @Mock FundamentalSnapshotRepository fundamentalSnapshotRepository;
    @Mock RatioSnapshotRepository ratioSnapshotRepository;
    @Mock PiotroskiResultRepository piotroskiResultRepository;
    @Mock AltmanResultRepository altmanResultRepository;
    @Mock CyclicalityResultRepository cyclicalityResultRepository;
    @Mock EarningsQualityResultRepository earningsQualityResultRepository;

    RiskAnalysisService service;
    Security security;

    @BeforeEach
    void setUp() {
        service = new RiskAnalysisService(securityRepository, fundamentalSnapshotRepository, ratioSnapshotRepository,
                piotroskiResultRepository, altmanResultRepository, cyclicalityResultRepository, earningsQualityResultRepository);
        security = new Security();
        security.setSymbol("ACME");
        security.setCompanyName("Acme Inc.");
        security.setSector("Industrials");
        security.setIndustry("Manufacturing");
        security.setMarketCap(new BigDecimal("900"));
        when(securityRepository.findBySymbol("ACME")).thenReturn(Optional.of(security));
        Mockito.lenient().when(piotroskiResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.lenient().when(altmanResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.lenient().when(cyclicalityResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.lenient().when(earningsQualityResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void computePiotroski_scoresNineFactors() {
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL))
                .thenReturn(List.of(
                        annual(new BigDecimal("120"), new BigDecimal("20"), new BigDecimal("25"), new BigDecimal("100"), new BigDecimal("20"), 100L),
                        annual(new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("12"), new BigDecimal("90"), new BigDecimal("30"), 110L)
                ));
        RatioSnapshot latest = ratio(new BigDecimal("1.8"), new BigDecimal("0.40"));
        RatioSnapshot prior = ratio(new BigDecimal("1.2"), new BigDecimal("0.30"));
        when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.TTM))
                .thenReturn(List.of(latest, prior));

        PiotroskiResult result = service.computePiotroski("ACME");

        assertThat(result.getTotalScore()).isEqualTo(9);
        assertThat(result.isPositiveNetIncome()).isTrue();
        assertThat(result.isCashFlowQuality()).isTrue();
        assertThat(result.getAvailabilityStatus()).isEqualTo(RiskAvailabilityStatus.AVAILABLE);
    }

    @Test
    void computeAltman_usesManufacturingFormulaAndSafeZone() {
        when(fundamentalSnapshotRepository.findTopBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL))
                .thenReturn(Optional.of(annual(new BigDecimal("1000"), new BigDecimal("100"), new BigDecimal("140"),
                        new BigDecimal("1000"), new BigDecimal("300"), 100L)));
        when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.TTM))
                .thenReturn(List.of(ratio(new BigDecimal("2.0"), new BigDecimal("0.40"))));

        AltmanResult result = service.computeAltman("ACME");

        assertThat(result.getFormulaVariant()).isEqualTo(AltmanFormulaVariant.MANUFACTURING);
        assertThat(result.getScore()).isGreaterThan(new BigDecimal("2.99"));
        assertThat(result.getZone()).isEqualTo(AltmanZone.SAFE);
    }

    @Test
    void computeAltman_usesNonManufacturingFormulaForServices() {
        security.setSector("Technology");
        security.setIndustry("Software");
        when(fundamentalSnapshotRepository.findTopBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL))
                .thenReturn(Optional.of(annual(new BigDecimal("500"), new BigDecimal("20"), new BigDecimal("30"),
                        new BigDecimal("1000"), new BigDecimal("900"), 100L)));
        when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.TTM))
                .thenReturn(List.of(ratio(new BigDecimal("1.0"), new BigDecimal("0.20"))));

        AltmanResult result = service.computeAltman("ACME");

        assertThat(result.getFormulaVariant()).isEqualTo(AltmanFormulaVariant.NON_MANUFACTURING);
        assertThat(result.getZone()).isEqualTo(AltmanZone.DISTRESS);
    }

    @Test
    void assessCyclicality_classifiesHighVolatilityAsHighlyCyclical() {
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL))
                .thenReturn(List.of(
                        annual(new BigDecimal("200"), new BigDecimal("60"), BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE, 1L),
                        annual(new BigDecimal("80"), new BigDecimal("-10"), BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE, 1L),
                        annual(new BigDecimal("180"), new BigDecimal("50"), BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE, 1L)
                ));

        CyclicalityResult result = service.assessCyclicality("ACME");

        assertThat(result.getClassification()).isEqualTo(CyclicalityClassification.HIGHLY_CYCLICAL);
        assertThat(result.getYearsAnalyzed()).isEqualTo(3);
    }

    @Test
    void computeEarningsQuality_flagsDeterioratingWeakQuality() {
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL))
                .thenReturn(List.of(
                        annual(new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("70"), new BigDecimal("1000"), new BigDecimal("100"), 100L),
                        annual(new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("120"), new BigDecimal("1000"), new BigDecimal("100"), 100L)
                ));

        EarningsQualityResult result = service.computeEarningsQuality("ACME");

        assertThat(result.getFcfToNetIncome()).isEqualByComparingTo(new BigDecimal("0.7000"));
        assertThat(result.getClassification()).isEqualTo(EarningsQualityClassification.WEAK);
        assertThat(result.isDeteriorating()).isTrue();
    }

    private FundamentalSnapshot annual(BigDecimal revenue, BigDecimal netIncome, BigDecimal operatingCashFlow,
                                       BigDecimal assets, BigDecimal debt, Long shares) {
        FundamentalSnapshot snapshot = new FundamentalSnapshot();
        snapshot.setRevenue(revenue);
        snapshot.setNetIncome(netIncome);
        snapshot.setOperatingIncome(operatingCashFlow);
        snapshot.setOperatingCashFlow(operatingCashFlow);
        snapshot.setFreeCashFlow(operatingCashFlow);
        snapshot.setTotalAssets(assets);
        snapshot.setTotalLiabilities(debt);
        snapshot.setTotalDebt(debt);
        snapshot.setSharesOutstanding(shares);
        return snapshot;
    }

    private RatioSnapshot ratio(BigDecimal currentRatio, BigDecimal grossMargin) {
        RatioSnapshot snapshot = new RatioSnapshot();
        snapshot.setCurrentRatio(currentRatio);
        snapshot.setGrossMargin(grossMargin);
        return snapshot;
    }
}
