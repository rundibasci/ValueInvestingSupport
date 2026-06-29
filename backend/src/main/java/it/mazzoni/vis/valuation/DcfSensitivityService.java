package it.mazzoni.vis.valuation;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class DcfSensitivityService {

    private static final List<BigDecimal> WACC_OFFSETS = List.of(
            new BigDecimal("-0.01"), BigDecimal.ZERO, new BigDecimal("0.01"));
    private static final List<BigDecimal> TERMINAL_OFFSETS = List.of(
            new BigDecimal("-0.005"), BigDecimal.ZERO, new BigDecimal("0.005"));

    public DcfSensitivityResult analyze(DcfInput input) {
        List<BigDecimal> waccValues = WACC_OFFSETS.stream()
                .map(input.wacc()::add)
                .toList();
        List<BigDecimal> terminalValues = TERMINAL_OFFSETS.stream()
                .map(input.terminalRate()::add)
                .toList();
        List<DcfSensitivityCell> cells = new ArrayList<>();
        DcfCalculator calculator = new DcfCalculator();

        for (BigDecimal wacc : waccValues) {
            for (BigDecimal terminalRate : terminalValues) {
                if (terminalRate.compareTo(wacc) >= 0) {
                    continue;
                }
                DcfInput adjusted = new DcfInput(
                        input.fcfTtm(),
                        input.growthY1Y5(),
                        input.growthY6Y10(),
                        terminalRate,
                        wacc,
                        input.shares(),
                        input.netDebt(),
                        input.fcfYearsPositive());
                calculator.calculate(adjusted).ifPresent(result -> cells.add(new DcfSensitivityCell(
                        wacc,
                        terminalRate,
                        result.fairValue(),
                        result.terminalValuePercentage(),
                        result.highTerminalDependence())));
            }
        }
        return new DcfSensitivityResult(waccValues, terminalValues, cells);
    }
}
