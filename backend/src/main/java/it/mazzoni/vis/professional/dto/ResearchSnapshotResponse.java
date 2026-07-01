package it.mazzoni.vis.professional.dto;

import it.mazzoni.vis.domain.entity.ResearchSnapshot;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ResearchSnapshotResponse(
        UUID id,
        String symbol,
        String actionType,
        LocalDateTime capturedAt,
        BigDecimal currentPrice,
        BigDecimal compositeFairValue,
        BigDecimal marginOfSafety,
        BigDecimal valueScore,
        BigDecimal waccUsed,
        String dataSource,
        Integer piotroskiScore,
        String moatClassification,
        String rationale
) {
    public static ResearchSnapshotResponse from(ResearchSnapshot snapshot) {
        return new ResearchSnapshotResponse(
                snapshot.getId(),
                snapshot.getSymbol(),
                snapshot.getActionType(),
                snapshot.getCapturedAt(),
                snapshot.getCurrentPrice(),
                snapshot.getCompositeFairValue(),
                snapshot.getMarginOfSafety(),
                snapshot.getValueScore(),
                snapshot.getWaccUsed(),
                snapshot.getDataSource(),
                snapshot.getPiotroskiScore(),
                snapshot.getMoatClassification(),
                snapshot.getRationale()
        );
    }
}
