package it.mazzoni.vis.thesis;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ThesisInputBuilderTest {

    private final ThesisInputBuilder builder = new ThesisInputBuilder(null, null, null);

    private static List<BigDecimal> series(String... values) {
        List<BigDecimal> list = new ArrayList<>();
        for (String v : values) list.add(v == null ? null : new BigDecimal(v));
        return list;
    }

    @Test
    void classifyTrend_returnsNotAvailable_whenFewerThanTwoPoints() {
        assertThat(builder.classifyTrend(series("100"), "revenue", new ArrayList<>())).isEqualTo(Trend.NOT_AVAILABLE);
        assertThat(builder.classifyTrend(null, "revenue", new ArrayList<>())).isEqualTo(Trend.NOT_AVAILABLE);
    }

    @Test
    void classifyTrend_stronglyGrowing_onLargeIncrease() {
        assertThat(builder.classifyTrend(series("100", "120"), "revenue", new ArrayList<>()))
                .isEqualTo(Trend.STRONGLY_GROWING);
    }

    @Test
    void classifyTrend_growing_onModerateIncrease() {
        assertThat(builder.classifyTrend(series("100", "105"), "revenue", new ArrayList<>()))
                .isEqualTo(Trend.GROWING);
    }

    @Test
    void classifyTrend_stable_onSmallChange() {
        assertThat(builder.classifyTrend(series("100", "101"), "revenue", new ArrayList<>()))
                .isEqualTo(Trend.STABLE);
    }

    @Test
    void classifyTrend_declining_onModerateDecrease() {
        assertThat(builder.classifyTrend(series("100", "95"), "revenue", new ArrayList<>()))
                .isEqualTo(Trend.DECLINING);
    }

    @Test
    void classifyTrend_stronglyDeclining_onLargeDecrease() {
        assertThat(builder.classifyTrend(series("100", "70"), "revenue", new ArrayList<>()))
                .isEqualTo(Trend.STRONGLY_DECLINING);
    }

    @Test
    void classifyTrend_volatile_whenAnyPeriodSwingExceedsThreshold() {
        // 100 -> 200 (+100%) -> 210 (+5%): the huge first swing marks this volatile even
        // though the latest period alone would read as merely "growing".
        assertThat(builder.classifyTrend(series("100", "200", "210"), "revenue", new ArrayList<>()))
                .isEqualTo(Trend.VOLATILE);
    }

    @Test
    void classifyTrend_recordsWarning_whenNotAvailable() {
        List<String> warnings = new ArrayList<>();
        builder.classifyTrend(series("100"), "earnings", warnings);
        assertThat(warnings).anyMatch(w -> w.contains("earningsTrend"));
    }
}
