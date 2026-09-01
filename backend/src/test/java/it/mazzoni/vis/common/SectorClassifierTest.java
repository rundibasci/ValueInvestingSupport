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
}
