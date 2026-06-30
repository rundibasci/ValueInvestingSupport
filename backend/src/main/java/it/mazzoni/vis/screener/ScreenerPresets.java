package it.mazzoni.vis.screener;

import it.mazzoni.vis.screener.dto.ScreenerRequest;

import java.math.BigDecimal;

public final class ScreenerPresets {

    private ScreenerPresets() {}

    public static final ScreenerRequest GRAHAM = new ScreenerRequest(
            null, null,
            new BigDecimal("15"), null,
            null,
            new BigDecimal("10"), new BigDecimal("1.0"),
            null, null,
            null, null, null,
            null, null,
            "totalScore", "DESC", 0, 20
    );

    public static final ScreenerRequest DIVIDEND = new ScreenerRequest(
            null, null,
            new BigDecimal("5"), null,
            null,
            null, null,
            new BigDecimal("2.0"), null,
            null, null, null,
            null, null,
            "totalScore", "DESC", 0, 20
    );

    public static final ScreenerRequest QUALITY = new ScreenerRequest(
            null, null,
            null, null,
            new BigDecimal("60"),
            new BigDecimal("15"), new BigDecimal("1.5"),
            null, null,
            null, null, null,
            null, null,
            "totalScore", "DESC", 0, 20
    );
}
