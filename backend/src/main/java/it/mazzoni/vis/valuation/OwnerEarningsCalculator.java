package it.mazzoni.vis.valuation;

import java.math.BigDecimal;

public class OwnerEarningsCalculator {

    public BigDecimal calculate(BigDecimal netIncome, BigDecimal depreciation, BigDecimal maintenanceCapex) {
        if (netIncome == null) {
            return null;
        }
        BigDecimal depreciationValue = depreciation != null ? depreciation : BigDecimal.ZERO;
        BigDecimal maintenanceCapexValue = maintenanceCapex != null ? maintenanceCapex : BigDecimal.ZERO;
        return netIncome.add(depreciationValue).subtract(maintenanceCapexValue);
    }

    public BigDecimal estimateMaintenanceCapex(BigDecimal depreciation, BigDecimal maintenanceCapexDepreciationRatio) {
        if (depreciation == null || maintenanceCapexDepreciationRatio == null) {
            return BigDecimal.ZERO;
        }
        return depreciation.multiply(maintenanceCapexDepreciationRatio);
    }
}
