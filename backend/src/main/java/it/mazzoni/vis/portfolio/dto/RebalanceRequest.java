package it.mazzoni.vis.portfolio.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.util.List;

public record RebalanceRequest(
        @Valid SimulationRequest simulation,
        List<@Valid RebalanceTarget> targets,
        @DecimalMin("0.0") BigDecimal minimumTradeValue
) {
    @AssertTrue(message = "Provide exactly one of simulation or targets")
    public boolean hasExactlyOneTargetSource() { return (simulation != null) != (targets != null && !targets.isEmpty()); }
}
