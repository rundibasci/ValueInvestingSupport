package it.mazzoni.vis.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MarketDataFallbackAdminControllerTest {

    @Mock MarketDataFallbackAdminService service;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(new MarketDataFallbackAdminController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void events_acceptsOperationalFilters() throws Exception {
        when(service.events(eq("KO"), eq("quote"), isNull(), eq("SUCCESS"),
                eq("MISSING_FIELD"), isNull(), any(LocalDateTime.class), isNull(), eq(0), eq(50)))
                .thenReturn(new PageResponse<>(List.of(), 0, 50, 0, 0));

        mockMvc.perform(get("/api/v1/admin/market-data-fallbacks")
                        .param("symbol", "KO")
                        .param("operation", "quote")
                        .param("outcome", "SUCCESS")
                        .param("triggerReason", "MISSING_FIELD")
                        .param("from", "2026-07-15T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void summary_exposesSeparatedFallbackCounts() throws Exception {
        when(service.summary(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(new MarketDataFallbackSummaryResponse(
                        5, 1, 2, 1, 1, 3, LocalDateTime.of(2026, 7, 15, 12, 0),
                        Map.of("PLAN_RESTRICTION", 2L), Map.of("QUOTE", 3L), Map.of("SUCCESS", 3L)));

        mockMvc.perform(get("/api/v1/admin/market-data-fallbacks/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAttempts").value(5))
                .andExpect(jsonPath("$.successfulFallbacks").value(1))
                .andExpect(jsonPath("$.successfulEnrichments").value(2));
    }
}

