package it.mazzoni.vis.common;

import it.mazzoni.vis.common.dto.AvailabilityResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityResponseTest {

    @Test
    void factories_returnStructuredStatesForUiAndApiTests() {
        LocalDate dataAsOf = LocalDate.of(2026, 6, 28);

        AvailabilityResponse available = AvailabilityResponse.available(dataAsOf);
        AvailabilityResponse missing = AvailabilityResponse.missingComputation("No persisted score.");
        AvailabilityResponse limited = AvailabilityResponse.providerLimited("Provider did not return dividends.");

        assertThat(available.status()).isEqualTo(AvailabilityStatus.AVAILABLE);
        assertThat(available.reason()).isEqualTo("Data is available.");
        assertThat(available.dataAsOf()).isEqualTo(dataAsOf);

        assertThat(missing.status()).isEqualTo(AvailabilityStatus.MISSING_INTERNAL_COMPUTATION);
        assertThat(missing.reason()).isEqualTo("No persisted score.");
        assertThat(missing.dataAsOf()).isNull();

        assertThat(limited.status()).isEqualTo(AvailabilityStatus.PROVIDER_LIMITED);
        assertThat(limited.reason()).isEqualTo("Provider did not return dividends.");
        assertThat(limited.dataAsOf()).isNull();
    }
}
