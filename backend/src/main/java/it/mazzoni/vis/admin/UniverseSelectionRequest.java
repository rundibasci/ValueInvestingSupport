package it.mazzoni.vis.admin;

import java.math.BigDecimal;
import java.util.List;

public record UniverseSelectionRequest(
        List<String> exchanges,
        List<String> countries,
        List<String> sectors,
        Boolean excludeSectors,
        BigDecimal marketCapMin,
        BigDecimal marketCapMax,
        Long volumeMin,
        Integer maxSymbols,
        UniverseSortBy sortBy
) {}
