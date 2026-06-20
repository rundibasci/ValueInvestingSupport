package it.mazzoni.vis.security.dto;

import it.mazzoni.vis.domain.entity.Security;

public record SecuritySearchItem(
        String symbol,
        String companyName,
        String sector,
        String exchange
) {
    public static SecuritySearchItem from(Security s) {
        return new SecuritySearchItem(s.getSymbol(), s.getCompanyName(), s.getSector(), s.getExchange());
    }
}
