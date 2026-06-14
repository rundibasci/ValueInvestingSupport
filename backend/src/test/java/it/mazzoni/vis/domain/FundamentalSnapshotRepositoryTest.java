package it.mazzoni.vis.domain;

import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FundamentalSnapshotRepositoryTest {

    @Autowired
    private FundamentalSnapshotRepository snapshotRepository;

    @Autowired
    private SecurityRepository securityRepository;

    private Security security;

    @BeforeEach
    void setUp() {
        Security s = new Security();
        s.setSymbol("AMZN");
        s.setCompanyName("Amazon.com Inc.");
        security = securityRepository.save(s);
    }

    @Test
    void savesAndRetrievesSnapshotBySecurity() {
        FundamentalSnapshot snap = new FundamentalSnapshot();
        snap.setSecurity(security);
        snap.setPeriod(Period.ANNUAL);
        snap.setFiscalYear(2023);
        snap.setRevenue(new BigDecimal("514000000000"));
        snap.setNetIncome(new BigDecimal("30000000000"));
        snapshotRepository.save(snap);

        List<FundamentalSnapshot> results = snapshotRepository.findBySecurity(security);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRevenue()).isEqualByComparingTo(new BigDecimal("514000000000"));
    }

    @Test
    void filtersSnapshotsByPeriod() {
        FundamentalSnapshot annual = new FundamentalSnapshot();
        annual.setSecurity(security);
        annual.setPeriod(Period.ANNUAL);
        annual.setFiscalYear(2023);

        FundamentalSnapshot quarterly = new FundamentalSnapshot();
        quarterly.setSecurity(security);
        quarterly.setPeriod(Period.QUARTERLY);
        quarterly.setFiscalYear(2023);
        quarterly.setFiscalQuarter(1);

        snapshotRepository.save(annual);
        snapshotRepository.save(quarterly);

        List<FundamentalSnapshot> annuals = snapshotRepository
                .findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL);
        assertThat(annuals).hasSize(1);
        assertThat(annuals.get(0).getPeriod()).isEqualTo(Period.ANNUAL);
    }
}
