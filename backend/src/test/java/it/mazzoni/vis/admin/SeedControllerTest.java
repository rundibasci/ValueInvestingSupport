package it.mazzoni.vis.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.mazzoni.vis.demo.GlobalExceptionHandler;
import it.mazzoni.vis.domain.entity.Recommendation;
import it.mazzoni.vis.marketdata.MarketDataException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SeedControllerTest {

    @Mock SeedService seedService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        SeedController controller = new SeedController(seedService, "AAPL,MSFT,KO,JNJ");
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void seed_withTickersParam_returns200WithResultArray() throws Exception {
        when(seedService.seedTickers(anyList())).thenReturn(List.of(
                SeedResult.success("AAPL", "Apple Inc.",
                        "Technology", "NASDAQ", "US", null, new BigDecimal("182.50"),
                        new BigDecimal("210.50"), new BigDecimal("13.60"),
                        null, Recommendation.QUALITY_VALUE, "FMP", java.time.LocalDate.of(2026, 6, 27))));

        mockMvc.perform(post("/api/v1/admin/seed").param("tickers", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].companyName").value("Apple Inc."))
                .andExpect(jsonPath("$[0].compositeFairValue").value(210.50))
                .andExpect(jsonPath("$[0].marginOfSafety").value(13.60))
                .andExpect(jsonPath("$[0].recommendation").value("QUALITY_VALUE"))
                .andExpect(jsonPath("$[0].error").doesNotExist());
    }

    @Test
    void seed_withoutTickersParam_usesDefaultList() throws Exception {
        when(seedService.seedTickers(List.of("AAPL", "MSFT", "KO", "JNJ"))).thenReturn(
                List.of(SeedResult.success("AAPL", "Apple Inc.",
                        "Technology", "NASDAQ", "US", null, new BigDecimal("182.50"),
                        new BigDecimal("210.50"), new BigDecimal("13.60"),
                        null, Recommendation.QUALITY_VALUE, "FMP", java.time.LocalDate.of(2026, 6, 27))));

        mockMvc.perform(post("/api/v1/admin/seed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"));
    }

    @Test
    void seed_oneTickerFails_returnsErrorFieldInArray() throws Exception {
        when(seedService.seedTickers(anyList())).thenReturn(List.of(
                SeedResult.failed("XYZ", "not found"),
                SeedResult.success("AAPL", "Apple Inc.",
                        "Technology", "NASDAQ", "US", null, new BigDecimal("182.50"),
                        new BigDecimal("210.50"), new BigDecimal("13.60"),
                        null, Recommendation.QUALITY_VALUE, "FMP", java.time.LocalDate.of(2026, 6, 27))));

        mockMvc.perform(post("/api/v1/admin/seed").param("tickers", "XYZ,AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("XYZ"))
                .andExpect(jsonPath("$[0].error").value("not found"))
                .andExpect(jsonPath("$[1].symbol").value("AAPL"))
                .andExpect(jsonPath("$[1].error").doesNotExist());
    }

    @Test
    void seed_fmpUnavailable_returns503() throws Exception {
        when(seedService.seedTickers(anyList()))
                .thenThrow(new MarketDataException(
                        MarketDataException.ErrorCode.SERVICE_UNAVAILABLE, "AAPL"));

        mockMvc.perform(post("/api/v1/admin/seed").param("tickers", "AAPL"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").exists());
    }
}
