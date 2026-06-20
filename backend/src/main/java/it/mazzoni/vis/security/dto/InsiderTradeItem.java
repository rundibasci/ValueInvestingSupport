package it.mazzoni.vis.security.dto;

import it.mazzoni.vis.domain.entity.InsiderTrade;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InsiderTradeItem(
        LocalDate transactionDate,
        String name,
        String title,
        String transactionType,
        Long shares,
        BigDecimal pricePerShare,
        BigDecimal totalValue
) {
    public static InsiderTradeItem from(InsiderTrade t) {
        return new InsiderTradeItem(
                t.getTradeDate(),
                t.getInsiderName(),
                t.getTitle(),
                t.getTransactionType() != null ? t.getTransactionType().name() : null,
                t.getShares(),
                t.getPricePerShare(),
                t.getTradeValue()
        );
    }
}
