package it.mazzoni.vis.common;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SectorClassifierTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Real Estate",
            "REIT - Retail",
            "Utilities",
            "Utility",
            "real estate", // case-insensitive
            "REIT"
    })
    void isReitOrUtility_matchesRealEstateReitAndUtilitySectors(String sector) {
        assertThat(SectorClassifier.isReitOrUtility(sector)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Technology", "Consumer Staples", "Financial Services", "Energy", "Basic Materials"})
    void isReitOrUtility_doesNotMatchOtherSectors(String sector) {
        assertThat(SectorClassifier.isReitOrUtility(sector)).isFalse();
    }

    @Test
    void isReitOrUtility_nullSector_returnsFalse() {
        assertThat(SectorClassifier.isReitOrUtility(null)).isFalse();
    }

    // RM2 (specs/sector-aware-valuation-metrics.md §10, open question 4): isReit is narrower than
    // isReitOrUtility — REIT/real-estate only, excluding utility. Regression-guards that the
    // weight-profile classification above is unchanged by this new, separate method.
    @ParameterizedTest
    @ValueSource(strings = {"Real Estate", "REIT - Retail", "real estate", "REIT"})
    void isReit_matchesRealEstateAndReitSectors(String sector) {
        assertThat(SectorClassifier.isReit(sector)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Utilities", "Utility", "Technology", "Financial Services"})
    void isReit_doesNotMatchUtilityOrOtherSectors(String sector) {
        assertThat(SectorClassifier.isReit(sector)).isFalse();
    }

    @Test
    void isReit_nullSector_returnsFalse() {
        assertThat(SectorClassifier.isReit(null)).isFalse();
    }
}
