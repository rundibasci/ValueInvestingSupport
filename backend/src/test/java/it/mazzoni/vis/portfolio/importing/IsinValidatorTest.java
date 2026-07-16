package it.mazzoni.vis.portfolio.importing;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IsinValidatorTest {
    @Test void validatesAndNormalizesKnownIsins() {
        assertThat(IsinValidator.isValid(" US0378331005 ")).isTrue();
        assertThat(IsinValidator.isValid("NL0000313286")).isTrue();
        assertThat(IsinValidator.normalize(" us7170811035 ")).isEqualTo("US7170811035");
    }
    @Test void rejectsShapeAndCheckDigitFailures() {
        assertThat(IsinValidator.isValid("XX123")).isFalse();
        assertThat(IsinValidator.isValid("US0378331004")).isFalse();
        assertThat(IsinValidator.isValid(null)).isFalse();
    }
}
