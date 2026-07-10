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
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UniverseSelectionControllerTest {

    @Mock UniverseSelectionService universeSelectionService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UniverseSelectionController(universeSelectionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void templates_returnsConfiguredTemplates() throws Exception {
        when(universeSelectionService.templates()).thenReturn(List.of(
                new UniverseTemplateResponse("us-blue-chip", "US blue chip", "Large US-listed companies",
                        new UniverseSelectionRequest(List.of("NYSE"), List.of("US"), List.of(),
                                false, new BigDecimal("10000000000"), null, null, 100, UniverseSortBy.MARKET_CAP))));

        mockMvc.perform(get("/api/v1/admin/universe/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("us-blue-chip"))
                .andExpect(jsonPath("$[0].criteria.exchanges[0]").value("NYSE"));
    }

    @Test
    void preview_returnsMatchingSymbolsAndCapWarning() throws Exception {
        when(universeSelectionService.preview(any())).thenReturn(new UniversePreviewResponse(
                2, 1, true, "Results capped at 1 symbols",
                List.of(new UniversePreviewRow("AAPL", "Apple Inc.", "NASDAQ", "US",
                        "Technology", new BigDecimal("3000000000000"), 70000000L))));

        mockMvc.perform(post("/api/v1/admin/universe/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exchanges":["NASDAQ"],"countries":["US"],"maxSymbols":1,"sortBy":"MARKET_CAP_DESC"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMatches").value(2))
                .andExpect(jsonPath("$.capped").value(true))
                .andExpect(jsonPath("$.symbols[0].symbol").value("AAPL"));
    }

    @Test
    void seed_returnsPreviewAndSeedResults() throws Exception {
        when(universeSelectionService.seed(any())).thenReturn(new UniverseSeedCriteriaResponse(
                new UniversePreviewResponse(1, 1, false, null,
                        List.of(new UniversePreviewRow("KO", "Coca-Cola", "NYSE", "US",
                                "Consumer Staples", new BigDecimal("260000000000"), 15000000L))),
                List.of(SeedResult.success("KO", "Coca-Cola", "Consumer Staples", "NYSE", "US", null,
                        new BigDecimal("60"), new BigDecimal("70"), new BigDecimal("16.7"),
                        new BigDecimal("78"), Recommendation.QUALITY_VALUE, "FMP", LocalDate.of(2026, 7, 1)))));

        mockMvc.perform(post("/api/v1/admin/universe/seed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exchanges":["NYSE"],"sectors":["Consumer Staples"],"maxSymbols":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preview.symbols[0].symbol").value("KO"))
                .andExpect(jsonPath("$.results[0].status").value("seeded"));
    }
}
