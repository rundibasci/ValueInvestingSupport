package it.mazzoni.vis.scoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.mazzoni.vis.demo.GlobalExceptionHandler;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import it.mazzoni.vis.scoring.dto.ValueScoreResponse;
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

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ScoreControllerTest {

    @Mock ScoreService scoreService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ScoreController(scoreService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(om))
                .build();
    }

    @Test
    void score_existingSymbol_returns200WithScoreFields() throws Exception {
        ValueScoreResponse response = new ValueScoreResponse(
                "AAPL", "Apple Inc.",
                new BigDecimal("72.50"),
                new BigDecimal("20.00"), new BigDecimal("25.00"),
                new BigDecimal("14.00"), new BigDecimal("10.00"),
                new BigDecimal("0.00"),
                LocalDate.of(2026, 6, 20));
        when(scoreService.getScore("AAPL")).thenReturn(response);

        mockMvc.perform(get("/api/v1/securities/AAPL/score"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.companyName").value("Apple Inc."))
                .andExpect(jsonPath("$.totalScore").value(72.50))
                .andExpect(jsonPath("$.mosScore").value(20.00))
                .andExpect(jsonPath("$.qualityScore").value(25.00))
                .andExpect(jsonPath("$.scoreDate").value("2026-06-20"));

        verify(scoreService, times(1)).getScore("AAPL");
    }

    @Test
    void score_unknownSymbol_returns404() throws Exception {
        when(scoreService.getScore("XYZ")).thenThrow(new SymbolNotFoundException("XYZ"));

        mockMvc.perform(get("/api/v1/securities/XYZ/score"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Symbol not found: XYZ"));
    }

    @Test
    void score_lowercaseSymbol_delegatesWithOriginalCase() throws Exception {
        ValueScoreResponse response = new ValueScoreResponse(
                "ko", "Coca-Cola",
                new BigDecimal("65.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDate.now());
        when(scoreService.getScore("ko")).thenReturn(response);

        mockMvc.perform(get("/api/v1/securities/ko/score"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("ko"));
    }
}
