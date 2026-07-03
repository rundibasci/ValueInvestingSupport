package it.mazzoni.vis.common;

import java.util.Locale;

public final class SymbolNormalizer {
    private SymbolNormalizer() {
    }

    public static String canonical(String symbol) {
        if (symbol == null) {
            return null;
        }
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        if ("BRK.B".equals(normalized)) {
            return "BRK-B";
        }
        return normalized;
    }
}
