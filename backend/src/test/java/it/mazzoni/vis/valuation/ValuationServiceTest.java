package it.mazzoni.vis.valuation;

import it.mazzoni.vis.config.ValuationWeightsProperties;
import it.mazzoni.vis.domain.entity.DividendRecord;
import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.Recommendation;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.repository.DividendRecordRepository;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ValuationServiceTest {

    @Mock SecurityRepository securityRepository;
    @Mock FundamentalSnapshotRepository fundamentalSnapshotRepository;
    @Mock DividendRecordRepository dividendRecordRepository;
    @Mock PriceQuoteRepository priceQuoteRepository;
    @Mock ValuationResultRepository valuationResultRepository;

    private ValuationService service;

    private final ValuationWeightsProperties defaultWeights = new ValuationWeightsProperties(
            new BigDecimal("0.60"), new BigDecimal("0.25"), new BigDecimal("0.15"));

    private Security security;
    private FundamentalSnapshot snapshot;

    @BeforeEach
    void setUp() {
        service = new ValuationService(
                securityRepository, fundamentalSnapshotRepository, dividendRecordRepository,
                priceQuoteRepository, valuationResultRepository, defaultWeights);

        security = new Security();
        security.setSymbol("AAPL");

        snapshot = new FundamentalSnapshot();
        snapshot.setEpsDiluted(new BigDecimal("6.00"));
        snapshot.setTotalEquity(new BigDecimal("600000000"));
        snapshot.setSharesOutstanding(100_000_000L);
        snapshot.setFreeCashFlow(new BigDecimal("10000000000"));
        snapshot.setTotalDebt(new BigDecimal("5000000000"));
        snapshot.setCash(new BigDecimal("2000000000"));

        when(securityRepository.findBySymbol("AAPL")).thenReturn(Optional.of(security));
        when(fundamentalSnapshotRepository.findTopBySecurityAndPeriodOrderByReportDateDesc(security, Period.TTM))
                .thenReturn(Optional.of(snapshot));
        when(valuationResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubThreePositiveFcfYears() {
        FundamentalSnapshot y1 = new FundamentalSnapshot();
        y1.setFreeCashFlow(new BigDecimal("9000000000"));
        FundamentalSnapshot y2 = new FundamentalSnapshot();
        y2.setFreeCashFlow(new BigDecimal("8000000000"));
        FundamentalSnapshot y3 = new FundamentalSnapshot();
        y3.setFreeCashFlow(new BigDecimal("7000000000"));
        when(fundamentalSnapshotRepository
                .findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL))
                .thenReturn(List.of(y1, y2, y3));
    }

    private void stubNoPrice() {
        when(priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(security)).thenReturn(Optional.empty());
    }

    private void stubPrice(String price) {
        PriceQuote quote = new PriceQuote();
        quote.setClose(new BigDecimal(price));
        when(priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(security))
                .thenReturn(Optional.of(quote));
    }

    private ValuationParams dcfOnlyParams() {
        return new ValuationParams(
                new BigDecimal("0.09"), new BigDecimal("0.08"), new BigDecimal("0.04"),
                new BigDecimal("0.025"), null, null);
    }

    // ── happy paths ─────────────────────────────────────────────────────────

    @Test
    void dcfAndGraham_noPrice_compositePresentMosNull() {
        stubThreePositiveFcfYears();
        stubNoPrice();

        ValuationOutcome outcome = service.calculate("AAPL", dcfOnlyParams());
        ValuationResult result = outcome.result();

        assertThat(result.getDcfFairValue()).isNotNull().isGreaterThan(BigDecimal.ZERO);
        assertThat(result.getGrahamNumber()).isNotNull().isGreaterThan(BigDecimal.ZERO);
        assertThat(result.getCompositeFairValue()).isNotNull().isGreaterThan(BigDecimal.ZERO);
        assertThat(result.getMarginOfSafety()).isNull();
        assertThat(result.getRecommendation()).isNull();
    }

    @Test
    void dcfAndGraham_withPrice_mosAndRecommendationSet() {
        stubThreePositiveFcfYears();
        // price well below composite → strong buy territory
        stubPrice("50.00");

        ValuationOutcome outcome = service.calculate("AAPL", dcfOnlyParams());
        ValuationResult result = outcome.result();

        assertThat(result.getMarginOfSafety()).isNotNull();
        assertThat(result.getRecommendation()).isNotNull();
    }

    // ── DDM-absent proportional weight normalization (reference-value test) ──

    @Test
    void referenceValue_dcfAndGraham_proportionalWeights() {
        // Force predictable DCF fair value by controlling inputs precisely
        // graham: eps=6.00, bvps=6.00 → sqrt(22.5 * 6 * 6) = sqrt(810) ≈ 28.46
        snapshot.setEpsDiluted(new BigDecimal("6.00"));
        snapshot.setTotalEquity(new BigDecimal("600000000"));   // bvps = 6.00
        snapshot.setSharesOutstanding(100_000_000L);

        stubThreePositiveFcfYears();
        stubNoPrice();

        ValuationOutcome outcome = service.calculate("AAPL", dcfOnlyParams());
        Map<String, BigDecimal> eff = outcome.effectiveWeights();

        // Effective weights must normalize over dcf+graham (ddm=0)
        assertThat(eff.get("ddm")).isEqualByComparingTo(BigDecimal.ZERO);
        BigDecimal sum = eff.get("dcf").add(eff.get("graham"));
        assertThat(sum).isCloseTo(BigDecimal.ONE, offset(new BigDecimal("0.001")));

        // dcf weight ≈ 60/85 ≈ 0.705882, graham ≈ 25/85 ≈ 0.294117
        assertThat(eff.get("dcf")).isCloseTo(new BigDecimal("0.705882"), offset(new BigDecimal("0.000001")));
        assertThat(eff.get("graham")).isCloseTo(new BigDecimal("0.294117"), offset(new BigDecimal("0.000001")));
    }

    @Test
    void referenceValue_knownInputs_compositeCorrect() {
        // Construct a scenario where we can predict the exact composite:
        // Graham: eps=6.00, bvps=6.00 → sqrt(22.5*36) = sqrt(810) ≈ 28.46
        // DCF: suppress (fcfYearsPositive=0) → only Graham
        // Composite = Graham * 1.0
        snapshot.setEpsDiluted(new BigDecimal("6.00"));
        snapshot.setTotalEquity(new BigDecimal("600000000"));
        snapshot.setSharesOutstanding(100_000_000L);
        snapshot.setFreeCashFlow(new BigDecimal("-1")); // negative FCF in TTM

        // 0 positive FCF years → DCF returns empty
        when(fundamentalSnapshotRepository
                .findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL))
                .thenReturn(List.of());
        stubNoPrice();

        ValuationOutcome outcome = service.calculate("AAPL", dcfOnlyParams());
        ValuationResult result = outcome.result();

        BigDecimal expectedGraham = GrahamCalculator.calculate(
                new BigDecimal("6.00"), new BigDecimal("6.00"));
        assertThat(result.getGrahamNumber()).isEqualByComparingTo(expectedGraham);
        assertThat(result.getCompositeFairValue()).isEqualByComparingTo(expectedGraham);
        assertThat(result.getDcfFairValue()).isNull();
        assertThat(outcome.effectiveWeights().get("dcf")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(outcome.effectiveWeights().get("graham")).isEqualByComparingTo(BigDecimal.ONE);
    }

    // ── DCF absent (< 3 years positive FCF) ─────────────────────────────────

    @Test
    void dcfAbsent_grahamOnly_compositeEqualsGraham() {
        when(fundamentalSnapshotRepository
                .findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL))
                .thenReturn(List.of()); // 0 positive FCF years
        stubNoPrice();

        ValuationOutcome outcome = service.calculate("AAPL", dcfOnlyParams());
        ValuationResult result = outcome.result();

        assertThat(result.getDcfFairValue()).isNull();
        assertThat(result.getGrahamNumber()).isNotNull();
        assertThat(result.getCompositeFairValue()).isEqualByComparingTo(result.getGrahamNumber());
        assertThat(outcome.effectiveWeights().get("dcf")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(outcome.effectiveWeights().get("graham")).isEqualByComparingTo(BigDecimal.ONE);
    }

    // ── Graham absent (negative EPS) ─────────────────────────────────────────

    @Test
    void grahamAbsent_negativEps_dcfOnlyComposite() {
        snapshot.setEpsDiluted(new BigDecimal("-1.00"));
        stubThreePositiveFcfYears();
        stubNoPrice();

        ValuationOutcome outcome = service.calculate("AAPL", dcfOnlyParams());
        ValuationResult result = outcome.result();

        assertThat(result.getGrahamNumber()).isNull();
        assertThat(result.getDcfFairValue()).isNotNull();
        assertThat(result.getCompositeFairValue()).isEqualByComparingTo(result.getDcfFairValue());
        assertThat(outcome.effectiveWeights().get("graham")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(outcome.effectiveWeights().get("dcf")).isEqualByComparingTo(BigDecimal.ONE);
    }

    // ── all models absent ────────────────────────────────────────────────────

    @Test
    void allModelsAbsent_throwsValuationNotApplicableException() {
        snapshot.setEpsDiluted(new BigDecimal("-1.00")); // Graham fails
        snapshot.setFreeCashFlow(new BigDecimal("-1"));  // DCF TTM negative

        when(fundamentalSnapshotRepository
                .findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL))
                .thenReturn(List.of()); // 0 positive FCF years

        assertThatThrownBy(() -> service.calculate("AAPL", dcfOnlyParams()))
                .isInstanceOf(ValuationNotApplicableException.class)
                .hasMessageContaining("AAPL");
    }

    // ── symbol not found ─────────────────────────────────────────────────────

    @Test
    void symbolNotFound_throwsSymbolNotFoundException() {
        when(securityRepository.findBySymbol("FAKE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calculate("FAKE", dcfOnlyParams()))
                .isInstanceOf(SymbolNotFoundException.class);
    }

    // ── symbol lookup is case-insensitive ────────────────────────────────────

    @Test
    void symbolLookupIsUppercased() {
        when(securityRepository.findBySymbol("AAPL")).thenReturn(Optional.of(security));
        stubThreePositiveFcfYears();
        stubNoPrice();

        ValuationOutcome outcome = service.calculate("aapl", dcfOnlyParams());
        assertThat(outcome.result().getCompositeFairValue()).isNotNull();
    }

    // ── recommendation thresholds ────────────────────────────────────────────

    @Test
    void recommendation_strongBuy_whenMosAbove25() {
        stubThreePositiveFcfYears();
        stubPrice("10.00"); // very low price → high MoS → STRONG_BUY

        ValuationOutcome outcome = service.calculate("AAPL", dcfOnlyParams());
        assertThat(outcome.result().getRecommendation()).isEqualTo(Recommendation.STRONG_BUY);
    }

    @Test
    void recommendation_overvalued_whenMosNegative() {
        stubThreePositiveFcfYears();
        stubPrice("99999.00"); // absurdly high price → negative MoS → OVERVALUED

        ValuationOutcome outcome = service.calculate("AAPL", dcfOnlyParams());
        assertThat(outcome.result().getRecommendation()).isEqualTo(Recommendation.OVERVALUED);
    }

    // ── result is persisted ──────────────────────────────────────────────────

    @Test
    void valuationDateIsToday() {
        stubThreePositiveFcfYears();
        stubNoPrice();

        ValuationResult result = service.calculate("AAPL", dcfOnlyParams()).result();
        assertThat(result.getValuationDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void sourceIsFmp() {
        stubThreePositiveFcfYears();
        stubNoPrice();

        ValuationResult result = service.calculate("AAPL", dcfOnlyParams()).result();
        assertThat(result.getSource()).isEqualTo("fmp");
    }

    // ── DDM with eligible dividends ──────────────────────────────────────────

    @Test
    void ddm_eligible_includedInComposite() {
        stubThreePositiveFcfYears();
        stubNoPrice();

        // Build 6 years of dividend records
        LocalDate now = LocalDate.now();
        List<DividendRecord> dividends = List.of(
                dividendRecord(now.minusMonths(3), "1.00"),
                dividendRecord(now.minusMonths(6), "1.00"),
                dividendRecord(now.minusMonths(9), "1.00"),
                dividendRecord(now.minusMonths(15), "0.95"),
                dividendRecord(now.minusMonths(27), "0.90"),
                dividendRecord(now.minusMonths(39), "0.85"),
                dividendRecord(now.minusMonths(51), "0.80"),
                dividendRecord(now.minusMonths(63), "0.75"),
                dividendRecord(now.minusYears(5).minusMonths(3), "0.70")
        );
        when(dividendRecordRepository.findBySecurityOrderByExDividendDateDesc(security))
                .thenReturn(dividends);

        ValuationParams paramsWithDdm = new ValuationParams(
                new BigDecimal("0.09"), new BigDecimal("0.08"), new BigDecimal("0.04"),
                new BigDecimal("0.025"), new BigDecimal("0.10"), new BigDecimal("0.05"));

        ValuationOutcome outcome = service.calculate("AAPL", paramsWithDdm);
        ValuationResult result = outcome.result();

        assertThat(result.getDdmFairValue()).isNotNull().isGreaterThan(BigDecimal.ZERO);
        assertThat(outcome.effectiveWeights().get("ddm")).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void ddm_paramsAbsent_ddmSkipped() {
        stubThreePositiveFcfYears();
        stubNoPrice();

        ValuationOutcome outcome = service.calculate("AAPL", dcfOnlyParams());

        assertThat(outcome.result().getDdmFairValue()).isNull();
        assertThat(outcome.effectiveWeights().get("ddm")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private DividendRecord dividendRecord(LocalDate date, String amount) {
        DividendRecord r = new DividendRecord();
        r.setExDividendDate(date);
        r.setAmount(new BigDecimal(amount));
        return r;
    }
}
