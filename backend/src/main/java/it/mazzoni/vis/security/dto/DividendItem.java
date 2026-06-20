package it.mazzoni.vis.security.dto;

import it.mazzoni.vis.domain.entity.DividendRecord;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DividendItem(
        LocalDate exDividendDate,
        LocalDate paymentDate,
        BigDecimal amount,
        String currency
) {
    public static DividendItem from(DividendRecord r) {
        return new DividendItem(r.getExDividendDate(), r.getPaymentDate(), r.getAmount(), r.getCurrency());
    }
}
