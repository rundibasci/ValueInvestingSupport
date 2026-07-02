package it.mazzoni.vis.realdemo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RealDemoPropertiesTest {

    @Test
    void parseTickers_usesDefaultWhenBlank() {
        assertThat(RealDemoProperties.parseTickers(" "))
                .containsExactly("AAPL", "MSFT", "KO", "JNJ", "PG", "PEP", "WMT", "BRK-B", "UNP", "XOM");
    }

    @Test
    void parseTickers_normalizesTrimsAndDeduplicates() {
        List<String> tickers = RealDemoProperties.parseTickers(" ko, JNJ,ko,, brk-b ");

        assertThat(tickers).containsExactly("KO", "JNJ", "BRK-B");
    }
}
