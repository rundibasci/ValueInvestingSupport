package it.mazzoni.vis.domain;

import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RM2 (specs/sector-aware-valuation-metrics.md §2, §7): confirms the seven new REIT sector-metric
 * columns (V29) round-trip through save/find, and that {@link FundamentalSnapshotRepository}'s new
 * {@code findBySecurityAndPeriodAndReportDate} pairing lookup — {@link SectorMetricService}'s
 * mechanism for matching a RatioSnapshot row to its FundamentalSnapshot counterpart — works both
 * when a match exists and when it does not.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RatioSnapshotRepositoryTest {

    @Autowired private RatioSnapshotRepository ratioSnapshotRepository;
    @Autowired private FundamentalSnapshotRepository fundamentalSnapshotRepository;
    @Autowired private SecurityRepository securityRepository;

    private Security security;

    @BeforeEach
    void setUp() {
        Security s = new Security();
        s.setSymbol("O");
        s.setCompanyName("Realty Income Corp.");
        s.setSector("REIT - Retail");
        security = securityRepository.save(s);
    }

    @Test
    void savesAndRetrievesReitSectorMetricColumns() {
        RatioSnapshot ratio = new RatioSnapshot();
        ratio.setSecurity(security);
        ratio.setPeriod(Period.TTM);
        ratio.setReportDate(LocalDate.of(2026, 9, 1));
        ratio.setFfoPerShare(new BigDecimal("2.0000"));
        ratio.setAffoPerShare(new BigDecimal("1.1600"));
        ratio.setPriceToFfo(new BigDecimal("15.0000"));
        ratio.setPriceToAffo(new BigDecimal("25.8621"));
        ratio.setNetDebtToEbitda(new BigDecimal("5.0000"));
        ratio.setInterestCoverageEbitda(null); // never confirmed available (see validation.md)
        ratio.setAffoPayoutRatio(new BigDecimal("0.6207"));
        ratioSnapshotRepository.save(ratio);

        List<RatioSnapshot> results = ratioSnapshotRepository.findBySecurity(security);
        assertThat(results).hasSize(1);
        RatioSnapshot saved = results.get(0);
        assertThat(saved.getFfoPerShare()).isEqualByComparingTo("2.0000");
        assertThat(saved.getAffoPerShare()).isEqualByComparingTo("1.1600");
        assertThat(saved.getPriceToFfo()).isEqualByComparingTo("15.0000");
        assertThat(saved.getPriceToAffo()).isEqualByComparingTo("25.8621");
        assertThat(saved.getNetDebtToEbitda()).isEqualByComparingTo("5.0000");
        assertThat(saved.getInterestCoverageEbitda()).isNull();
        assertThat(saved.getAffoPayoutRatio()).isEqualByComparingTo("0.6207");
    }

    @Test
    void nonReitRow_leavesReitSectorMetricColumnsNull() {
        RatioSnapshot ratio = new RatioSnapshot();
        ratio.setSecurity(security);
        ratio.setPeriod(Period.TTM);
        ratio.setReportDate(LocalDate.now());
        ratio.setPeRatio(new BigDecimal("18.5")); // ordinary GAAP field populated, REIT fields not
        ratioSnapshotRepository.save(ratio);

        RatioSnapshot saved = ratioSnapshotRepository.findBySecurity(security).get(0);
        assertThat(saved.getFfoPerShare()).isNull();
        assertThat(saved.getAffoPerShare()).isNull();
        assertThat(saved.getPriceToFfo()).isNull();
        assertThat(saved.getPriceToAffo()).isNull();
        assertThat(saved.getNetDebtToEbitda()).isNull();
        assertThat(saved.getInterestCoverageEbitda()).isNull();
        assertThat(saved.getAffoPayoutRatio()).isNull();
    }

    @Test
    void findBySecurityAndPeriodAndReportDate_matchExists_returnsPairedFundamentalSnapshot() {
        LocalDate reportDate = LocalDate.of(2026, 9, 1);
        FundamentalSnapshot fundamentals = new FundamentalSnapshot();
        fundamentals.setSecurity(security);
        fundamentals.setPeriod(Period.TTM);
        fundamentals.setReportDate(reportDate);
        fundamentals.setNetIncome(new BigDecimal("40"));
        fundamentalSnapshotRepository.save(fundamentals);

        Optional<FundamentalSnapshot> found = fundamentalSnapshotRepository
                .findBySecurityAndPeriodAndReportDate(security, Period.TTM, reportDate);

        assertThat(found).isPresent();
        assertThat(found.get().getNetIncome()).isEqualByComparingTo("40");
    }

    @Test
    void findBySecurityAndPeriodAndReportDate_noMatch_returnsEmpty() {
        Optional<FundamentalSnapshot> found = fundamentalSnapshotRepository
                .findBySecurityAndPeriodAndReportDate(security, Period.TTM, LocalDate.of(2020, 1, 1));

        assertThat(found).isEmpty();
    }
}
