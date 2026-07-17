package it.mazzoni.vis.moat.dto;

import it.mazzoni.vis.domain.entity.RoicObservation;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RoicObservationResponse(
        Integer fiscalYear,
        LocalDate observationDate,
        BigDecimal roic,
        String source,
        String inputProvider,
        String formulaNote,
        String unavailableReason
) {
    static RoicObservationResponse from(RoicObservation observation) {
        return new RoicObservationResponse(observation.getFiscalYear(), observation.getObservationDate(),
                observation.getRoic(), observation.getSource().name(), observation.getInputProvider(),
                observation.getFormulaNote(), observation.getUnavailableReason());
    }
}
