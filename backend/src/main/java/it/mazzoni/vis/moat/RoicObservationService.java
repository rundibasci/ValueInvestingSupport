package it.mazzoni.vis.moat;

import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.RoicObservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RoicObservationService {
    private static final int PROVIDER_THRESHOLD = 5;
    private final RatioSnapshotRepository ratios;
    private final FundamentalSnapshotRepository fundamentals;
    private final RoicObservationRepository observations;
    private final DerivedRoicCalculator calculator;

    public RoicObservationService(RatioSnapshotRepository ratios,
                                  FundamentalSnapshotRepository fundamentals,
                                  RoicObservationRepository observations,
                                  DerivedRoicCalculator calculator) {
        this.ratios = ratios;
        this.fundamentals = fundamentals;
        this.observations = observations;
        this.calculator = calculator;
    }

    @Transactional
    public List<RoicObservation> refreshAfterIngestion(Security security, String inputProvider) {
        List<RatioSnapshot> provider = ratios.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL)
                .stream().filter(item -> item.getRoic() != null && item.getReportDate() != null).limit(10).toList();
        observations.deleteBySecurity(security);
        observations.flush();
        boolean fmpProvider = inputProvider != null && inputProvider.toUpperCase().contains("FMP");
        if (fmpProvider && provider.size() >= PROVIDER_THRESHOLD) {
            provider.forEach(item -> observations.save(providerObservation(security, item, inputProvider)));
        } else {
            persistDerivedSeries(security, inputProvider);
        }
        return observations.findBySecurityOrderByFiscalYearDesc(security);
    }

    @Transactional(readOnly = true)
    public List<RoicObservation> findSeries(Security security) {
        return observations.findBySecurityOrderByFiscalYearDesc(security);
    }

    private void persistDerivedSeries(Security security, String inputProvider) {
        List<FundamentalSnapshot> annuals = fundamentals
                .findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL)
                .stream().filter(item -> item.getFiscalYear() != null).limit(11)
                .sorted(Comparator.comparing(FundamentalSnapshot::getFiscalYear).reversed()).toList();
        Map<Integer, FundamentalSnapshot> byYear = annuals.stream().collect(Collectors.toMap(
                FundamentalSnapshot::getFiscalYear, Function.identity(), (first, ignored) -> first));
        annuals.stream().limit(10).forEach(current -> {
            FundamentalSnapshot prior = byYear.get(current.getFiscalYear() - 1);
            DerivedRoicCalculator.Calculation calculation = calculator.calculate(current, prior);
            RoicObservation observation = base(security, current.getFiscalYear(), current.getReportDate());
            observation.setRoic(calculation.roic());
            observation.setSource(calculation.available() ? RoicSource.DERIVED_INTERNAL : RoicSource.UNAVAILABLE);
            observation.setInputProvider(inputProvider);
            observation.setFormulaNote(calculation.formulaNote());
            observation.setUnavailableReason(calculation.unavailableReason());
            observations.save(observation);
        });
    }

    private RoicObservation providerObservation(Security security, RatioSnapshot ratio, String inputProvider) {
        RoicObservation observation = base(security, ratio.getReportDate().getYear(), ratio.getReportDate());
        observation.setRoic(MoatMath.normalizeRatio(ratio.getRoic()));
        observation.setSource(RoicSource.FMP_KEY_METRIC);
        observation.setInputProvider(inputProvider);
        observation.setFormulaNote("Provider-reported return on invested capital; no internal ROIC formula applied.");
        return observation;
    }

    private RoicObservation base(Security security, int fiscalYear, java.time.LocalDate date) {
        RoicObservation observation = new RoicObservation();
        observation.setSecurity(security);
        observation.setFiscalYear(fiscalYear);
        observation.setObservationDate(date);
        return observation;
    }
}
