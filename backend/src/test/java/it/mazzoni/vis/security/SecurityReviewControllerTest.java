package it.mazzoni.vis.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.mazzoni.vis.common.dto.AvailabilityResponse;
import it.mazzoni.vis.demo.GlobalExceptionHandler;
import it.mazzoni.vis.security.dto.SecurityReviewResponse;
import it.mazzoni.vis.security.dto.SectorMetricResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SecurityReviewControllerTest {

    @Mock SecurityReviewService securityReviewService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SecurityReviewController(securityReviewService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(om))
                .build();
    }

    @Test
    void review_knownSymbol_returnsAggregatedPacket() throws Exception {
        SecurityReviewResponse response = new SecurityReviewResponse(
                "AAPL",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new SecurityReviewResponse.SourceCoverageItem("Profile", null, "AVAILABLE",
                        "Application data is available.")),
                List.of(),
                List.of(new SecurityReviewResponse.AvailabilityItem("Score",
                        AvailabilityResponse.missingComputation("No persisted value score is available."))),
                List.of(new SecurityReviewResponse.DataQualityNote("Advice boundary", "INFO",
                        "Decision-support outputs, not investment advice."))
        );
        when(securityReviewService.getReview("AAPL")).thenReturn(response);

        mockMvc.perform(get("/api/v1/securities/AAPL/review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.sourceCoverage[0].category").value("Profile"))
                .andExpect(jsonPath("$.sourceCoverage[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.dataQualityNotes[0].category").value("Advice boundary"));
    }

    @Test
    void review_reitSymbol_serializesSectorMetrics() throws Exception {
        SectorMetricResponse sectorMetrics = new SectorMetricResponse(
                new BigDecimal("3.9024"), "ffo formula",
                new BigDecimal("1.9897"), "affo formula",
                new BigDecimal("15.6442"), new BigDecimal("30.6830"), "valuation multiple formula",
                new BigDecimal("9.1298"), new BigDecimal("3.1088"), "safety formula",
                new BigDecimal("1.6225"), "payout formula",
                AvailabilityResponse.available(LocalDate.now()));

        SecurityReviewResponse response = new SecurityReviewResponse(
                "O", null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null,
                sectorMetrics,
                null,
                List.of(), List.of(), List.of(), List.of());
        when(securityReviewService.getReview("O")).thenReturn(response);

        mockMvc.perform(get("/api/v1/securities/O/review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("O"))
                .andExpect(jsonPath("$.sectorMetrics.ffoPerShare").value(3.9024))
                .andExpect(jsonPath("$.sectorMetrics.priceToFfo").value(15.6442))
                .andExpect(jsonPath("$.sectorMetrics.availability.status").value("AVAILABLE"));
    }
}
