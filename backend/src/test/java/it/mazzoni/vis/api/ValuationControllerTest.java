package it.mazzoni.vis.api;

import it.mazzoni.vis.api.dto.ValuationResponse;
import it.mazzoni.vis.demo.GlobalExceptionHandler;
import it.mazzoni.vis.domain.entity.Recommendation;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import it.mazzoni.vis.valuation.ValuationNotApplicableException;
import it.mazzoni.vis.valuation.ValuationOutcome;
import it.mazzoni.vis.valuation.ValuationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ValuationControllerTest {

    @Mock ValuationService valuationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ValuationController(valuationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    private static final String VALID_BODY = """
            {
              "wacc": 0.09,
              "growthY1Y5": 0.08,
              "growthY6Y10": 0.04,
              "terminalRate": 0.025
            }
            """;

    private ValuationOutcome stubOutcome(String symbol) {
        Security sec = new Security();
        sec.setSymbol(symbol);

        ValuationResult result = new ValuationResult();
        result.setSecurity(sec);
        result.setValuationDate(LocalDate.of(2026, 6, 16));
        result.setDcfFairValue(new BigDecimal("210.50"));
        result.setDcfFairValueLow(new BigDecimal("185.00"));
        result.setDcfFairValueHigh(new BigDecimal("230.00"));
        result.setGrahamNumber(new BigDecimal("148.32"));
        result.setDdmFairValue(null);
        result.setCompositeFairValue(new BigDecimal("191.86"));
        result.setCurrentPrice(new BigDecimal("182.50"));
        result.setMarginOfSafety(new BigDecimal("4.87"));
        result.setRecommendation(Recommendation.FAIR_VALUE);
        result.setSource("fmp");

        Map<String, BigDecimal> weights = Map.of(
                "dcf", new BigDecimal("0.705882"),
                "graham", new BigDecimal("0.294118"),
                "ddm", BigDecimal.ZERO);

        return new ValuationOutcome(result, weights);
    }

    @Test
    void happyPath_returns200WithAllFields() throws Exception {
        when(valuationService.calculate(eq("AAPL"), any())).thenReturn(stubOutcome("AAPL"));

        mockMvc.perform(post("/api/v1/securities/AAPL/valuation/dcf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.valuationDate").value("2026-06-16"))
                .andExpect(jsonPath("$.dcfFairValue").value(210.50))
                .andExpect(jsonPath("$.grahamNumber").value(148.32))
                .andExpect(jsonPath("$.compositeFairValue").value(191.86))
                .andExpect(jsonPath("$.marginOfSafety").value(4.87))
                .andExpect(jsonPath("$.recommendation").value("FAIR_VALUE"))
                .andExpect(jsonPath("$.disclaimer").value(ValuationResponse.DISCLAIMER))
                .andExpect(jsonPath("$.weights.dcf").isNumber())
                .andExpect(jsonPath("$.weights.graham").isNumber())
                .andExpect(jsonPath("$.weights.ddm").isNumber());
    }

    @Test
    void symbolNotFound_returns404() throws Exception {
        when(valuationService.calculate(eq("FAKE"), any()))
                .thenThrow(new SymbolNotFoundException("FAKE"));

        mockMvc.perform(post("/api/v1/securities/FAKE/valuation/dcf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void noModelApplicable_returns422() throws Exception {
        when(valuationService.calculate(eq("XYZ"), any()))
                .thenThrow(new ValuationNotApplicableException("XYZ"));

        mockMvc.perform(post("/api/v1/securities/XYZ/valuation/dcf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void missingRequiredField_returns400() throws Exception {
        String bodyMissingWacc = """
                {
                  "growthY1Y5": 0.08,
                  "growthY6Y10": 0.04,
                  "terminalRate": 0.025
                }
                """;

        mockMvc.perform(post("/api/v1/securities/AAPL/valuation/dcf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyMissingWacc))
                .andExpect(status().isBadRequest());
    }
}
