package it.mazzoni.vis.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCorrelationFilterTest {

    @Test
    void doFilterInternal_reusesSafeIncomingCorrelationId() throws Exception {
        ObservabilityProperties properties = new ObservabilityProperties(false, "X-Correlation-ID");
        RequestCorrelationFilter filter = new RequestCorrelationFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.addHeader("X-Correlation-ID", "demo-correlation-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {};

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Correlation-ID")).isEqualTo("demo-correlation-123");
    }

    @Test
    void doFilterInternal_replacesUnsafeIncomingCorrelationId() throws Exception {
        ObservabilityProperties properties = new ObservabilityProperties(false, "X-Correlation-ID");
        RequestCorrelationFilter filter = new RequestCorrelationFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.addHeader("X-Correlation-ID", "bad token with spaces and too short");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {};

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Correlation-ID")).isNotBlank();
        assertThat(response.getHeader("X-Correlation-ID")).isNotEqualTo("bad token with spaces and too short");
    }
}
