package it.mazzoni.vis.demo;

import it.mazzoni.vis.config.DemoSecurityConfig;
import it.mazzoni.vis.demo.dto.DemoAnalysisResponse;
import it.mazzoni.vis.demo.dto.DemoAnalysisResponse.DcfValuation;
import it.mazzoni.vis.demo.dto.DemoAnalysisResponse.FinancialSummary;
import it.mazzoni.vis.demo.dto.DemoAnalysisResponse.Valuation;
import it.mazzoni.vis.demo.dto.Recommendation;
import it.mazzoni.vis.exception.MarketDataUnavailableException;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {DemoAnalysisController.class, GlobalExceptionHandler.class})
@Import(DemoSecurityConfig.class)
class DemoAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DemoAnalysisService service;

    @Test
    void happyPathReturnsDcfAndAllFields() throws Exception {
        DemoAnalysisResponse response = new DemoAnalysisResponse(
                "AAPL", "Apple Inc.", new BigDecimal("182.50"), "USD", "Technology",
                new FinancialSummary(
                        new BigDecimal("383285000000"),
                        new BigDecimal("96995000000"),
                        new BigDecimal("99584000000"),
                        new BigDecimal("6.13")),
                new Valuation(
                        new DcfValuation(
                                new BigDecimal("210.50"),
                                new BigDecimal("185.00"),
                                new BigDecimal("230.00")),
                        new BigDecimal("9.51"),
                        new BigDecimal("129.68")),
                new BigDecimal("28.86"),
                Recommendation.QUALITY_VALUE,
                DemoAnalysisResponse.DISCLAIMER
        );
        when(service.analyze("AAPL")).thenReturn(response);

        mockMvc.perform(get("/demo/analyze/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.companyName").value("Apple Inc."))
                .andExpect(jsonPath("$.currentPrice").value(182.50))
                .andExpect(jsonPath("$.valuation.dcf.fairValue").value(210.50))
                .andExpect(jsonPath("$.valuation.grahamNumber").value(9.51))
                .andExpect(jsonPath("$.valuation.composite").isNumber())
                .andExpect(jsonPath("$.marginOfSafety").isNumber())
                .andExpect(jsonPath("$.recommendation").value("QUALITY_VALUE"))
                .andExpect(jsonPath("$.disclaimer").value(DemoAnalysisResponse.DISCLAIMER));
    }

    @Test
    void dcfSkippedWhenNotEligible() throws Exception {
        DemoAnalysisResponse response = new DemoAnalysisResponse(
                "XYZ", "XYZ Corp", new BigDecimal("50.00"), "USD", "Industrials",
                new FinancialSummary(null, null, null, new BigDecimal("2.00")),
                new Valuation(
                        null,
                        new BigDecimal("14.97"),
                        new BigDecimal("14.97")),
                new BigDecimal("70.06"),
                Recommendation.QUALITY_VALUE,
                DemoAnalysisResponse.DISCLAIMER
        );
        when(service.analyze("XYZ")).thenReturn(response);

        mockMvc.perform(get("/demo/analyze/XYZ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valuation.dcf").doesNotExist())
                .andExpect(jsonPath("$.valuation.grahamNumber").value(14.97))
                .andExpect(jsonPath("$.valuation.composite").value(14.97));
    }

    @Test
    void symbolNotFoundReturns404() throws Exception {
        when(service.analyze("FAKE999")).thenThrow(new SymbolNotFoundException("FAKE999"));

        mockMvc.perform(get("/demo/analyze/FAKE999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void yahooUnavailableReturns503() throws Exception {
        when(service.analyze("AAPL"))
                .thenThrow(new MarketDataUnavailableException("Yahoo Finance is unreachable"));

        mockMvc.perform(get("/demo/analyze/AAPL"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").exists());
    }
}
