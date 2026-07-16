package it.mazzoni.vis.portfolio.importing;

import java.util.Locale;

public final class IsinValidator {
    private IsinValidator() {}
    public static String normalize(String value) {
        return value == null ? null : value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
    public static boolean isValid(String value) {
        String isin = normalize(value);
        if (isin == null || !isin.matches("[A-Z]{2}[A-Z0-9]{9}[0-9]")) return false;
        StringBuilder expanded = new StringBuilder();
        for (char c : isin.toCharArray()) {
            if (Character.isDigit(c)) expanded.append(c);
            else expanded.append(c - 'A' + 10);
        }
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = expanded.length() - 1; i >= 0; i--) {
            int n = expanded.charAt(i) - '0';
            if (doubleDigit) { n *= 2; if (n > 9) n -= 9; }
            sum += n;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }
}
