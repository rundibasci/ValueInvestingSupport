package it.mazzoni.vis.realdemo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
@Profile("realDemo")
public class RealDemoProperties {

    static final String DEFAULT_TICKERS = "AAPL,MSFT,KO,JNJ,PG,PEP,WMT,BRK-B,UNP,XOM";

    private final String rawTickers;

    public RealDemoProperties(@Value("${real-demo.tickers:${REAL_DEMO_TICKERS:}}") String rawTickers) {
        this.rawTickers = rawTickers;
    }

    public List<String> tickers() {
        return parseTickers(rawTickers);
    }

    static List<String> parseTickers(String rawTickers) {
        String source = rawTickers == null || rawTickers.isBlank() ? DEFAULT_TICKERS : rawTickers;
        return Arrays.stream(source.split(","))
                .map(String::trim)
                .filter(symbol -> !symbol.isBlank())
                .map(symbol -> symbol.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}
