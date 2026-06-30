package it.mazzoni.vis.moat;

import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.ValuationBandResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValuationHistoryServiceTest {
    @Mock SecurityRepository securityRepository;
    @Mock RatioSnapshotRepository ratioSnapshotRepository;
    @Mock ValuationBandResultRepository valuationBandResultRepository;

    ValuationHistoryService service;
    Security security;

    @BeforeEach
    void setUp() {
        security = new Security();
        security.setSymbol("MSFT");
        service = new ValuationHistoryService(securityRepository, ratioSnapshotRepository, valuationBandResultRepository);
        lenient().when(valuationBandResultRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void compute_marksLowPeHistoricallyCheap() {
        when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL))
                .thenReturn(List.of(
                        ratio("8", "2", "10", "0.02"),
                        ratio("12", "2", "10", "0.02"),
                        ratio("15", "2", "10", "0.02"),
                        ratio("20", "2", "10", "0.02"),
                        ratio("25", "2", "10", "0.02")
                ));

        List<ValuationBandResult> results = service.compute(security);

        ValuationBandResult pe = results.stream().filter(r -> r.getMetric().equals("PE")).findFirst().orElseThrow();
        assertThat(pe.getPosition()).isEqualTo(ValuationBandPosition.HISTORICALLY_CHEAP);
        assertThat(pe.getPercentile25()).isEqualByComparingTo("12");
        assertThat(pe.getPercentile75()).isEqualByComparingTo("20");
    }

    private RatioSnapshot ratio(String pe, String pb, String ev, String yield) {
        RatioSnapshot ratio = new RatioSnapshot();
        ratio.setPeRatio(new BigDecimal(pe));
        ratio.setPbRatio(new BigDecimal(pb));
        ratio.setEvToEbitda(new BigDecimal(ev));
        ratio.setDividendYield(new BigDecimal(yield));
        return ratio;
    }
}
