package it.mazzoni.vis.security.dto;

import it.mazzoni.vis.domain.entity.Security;
import java.util.UUID;

public record SecuritySearchItem(
        UUID id,
        String symbol,
        String companyName,
        String sector,
        String exchange
) {
    public static SecuritySearchItem from(Security s) {
        return new SecuritySearchItem(s.getId(), s.getSymbol(), s.getCompanyName(), s.getSector(), s.getExchange());
    }
}
