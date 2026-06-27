package it.mazzoni.vis.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.mazzoni.vis.demo.GlobalExceptionHandler;
import it.mazzoni.vis.domain.entity.Recommendation;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UniverseSeedControllerTest {

    @Mock SeedService seedService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UniverseSeedController(seedService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void seed_trimsTickerCsvForAuthenticatedSharedUniverseEndpoint() throws Exception {
        when(seedService.seedTickers(List.of("AAPL", "MSFT"))).thenReturn(List.of(
                SeedResult.success("AAPL", "Apple Inc.",
                        "Technology", "NASDAQ", "US", null, new BigDecimal("182.50"),
                        new BigDecimal("210.50"), new BigDecimal("13.60"),
                        null, Recommendation.QUALITY_VALUE, "FMP", LocalDate.of(2026, 6, 27))));

        mockMvc.perform(post("/api/v1/universe/seed").param("tickers", " AAPL, MSFT "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].status").value("seeded"))
                .andExpect(jsonPath("$[0].source").value("FMP"));
    }
}
