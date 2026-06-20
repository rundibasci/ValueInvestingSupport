package it.mazzoni.vis.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.mazzoni.vis.demo.GlobalExceptionHandler;
import it.mazzoni.vis.domain.entity.Recommendation;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import it.mazzoni.vis.security.domain.AnalystEstimate;
import it.mazzoni.vis.security.domain.AnalystEstimateRepository;
import it.mazzoni.vis.security.dto.ValuationDetailResponse;
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
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SecurityValuationControllerTest {

    @Mock SecurityRepository securityRepository;
    @Mock ValuationResultRepository valuationResultRepository;
    @Mock AnalystEstimateRepository analystEstimateRepository;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SecurityValuationController(securityRepository, valuationResultRepository,
                        analystEstimateRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(om))
                .build();
    }

    @Test
    void valuation_knownSymbol_returns200WithFullResponse() throws Exception {
        Security s = security("AAPL");
        ValuationResult result = valuationResult(s, new BigDecimal("185.0"), new BigDecimal("220.0"),
                Recommendation.QUALITY_VALUE);

        when(securityRepository.findBySymbol("AAPL")).thenReturn(Optional.of(s));
        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(s)).thenReturn(Optional.of(result));
        when(analystEstimateRepository.findBySecuritySymbolOrderByTargetDateDesc("AAPL")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/securities/AAPL/valuation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.currentPrice").value(185.0))
                .andExpect(jsonPath("$.compositeFairValue").value(220.0))
                .andExpect(jsonPath("$.recommendation").value("QUALITY_VALUE"))
                .andExpect(jsonPath("$.disclaimer").value(ValuationDetailResponse.MIFID_DISCLAIMER))
                .andExpect(jsonPath("$.analystEstimates").doesNotExist());
    }

    @Test
    void valuation_emptyAnalystEstimates_analystEstimatesIsNull() throws Exception {
        Security s = security("KO");
        ValuationResult result = valuationResult(s, new BigDecimal("60.0"), new BigDecimal("75.0"), null);

        when(securityRepository.findBySymbol("KO")).thenReturn(Optional.of(s));
        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(s)).thenReturn(Optional.of(result));
        when(analystEstimateRepository.findBySecuritySymbolOrderByTargetDateDesc("KO")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/securities/KO/valuation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analystEstimates").doesNotExist());
    }

    @Test
    void valuation_withAnalystEstimates_consensusBuyCount3() throws Exception {
        Security s = security("MSFT");
        ValuationResult result = valuationResult(s, new BigDecimal("380.0"), new BigDecimal("420.0"),
                Recommendation.QUALITY_VALUE);

        List<AnalystEstimate> estimates = List.of(
                estimate(new BigDecimal("450.0"), "BUY"),
                estimate(new BigDecimal("430.0"), "BUY"),
                estimate(new BigDecimal("400.0"), "HOLD")
        );

        when(securityRepository.findBySymbol("MSFT")).thenReturn(Optional.of(s));
        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(s)).thenReturn(Optional.of(result));
        when(analystEstimateRepository.findBySecuritySymbolOrderByTargetDateDesc("MSFT")).thenReturn(estimates);

        mockMvc.perform(get("/api/v1/securities/MSFT/valuation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analystEstimates.analystCount").value(3))
                .andExpect(jsonPath("$.analystEstimates.consensus").value("BUY"))
                .andExpect(jsonPath("$.analystEstimates.priceTargetMean").isNumber())
                .andExpect(jsonPath("$.analystEstimates.priceTargetLow").value(400.0))
                .andExpect(jsonPath("$.analystEstimates.priceTargetHigh").value(450.0));
    }

    @Test
    void valuation_unknownSymbol_returns404() throws Exception {
        when(securityRepository.findBySymbol("XYZ")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/securities/XYZ/valuation"))
                .andExpect(status().isNotFound());
    }

    @Test
    void valuation_noValuationResult_returns422() throws Exception {
        Security s = security("NEW");
        when(securityRepository.findBySymbol("NEW")).thenReturn(Optional.of(s));
        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(s)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/securities/NEW/valuation"))
                .andExpect(status().isUnprocessableEntity());
    }

    private Security security(String symbol) {
        Security s = new Security();
        s.setSymbol(symbol);
        s.setCompanyName(symbol + " Inc.");
        return s;
    }

    private ValuationResult valuationResult(Security s, BigDecimal currentPrice, BigDecimal compositeFairValue,
                                            Recommendation recommendation) {
        ValuationResult v = new ValuationResult();
        v.setSecurity(s);
        v.setValuationDate(LocalDate.now());
        v.setCurrentPrice(currentPrice);
        v.setDcfFairValue(new BigDecimal("210.0"));
        v.setDcfFairValueLow(new BigDecimal("190.0"));
        v.setDcfFairValueHigh(new BigDecimal("230.0"));
        v.setGrahamNumber(new BigDecimal("180.0"));
        v.setDdmFairValue(new BigDecimal("200.0"));
        v.setCompositeFairValue(compositeFairValue);
        v.setMarginOfSafety(new BigDecimal("15.00"));
        v.setRecommendation(recommendation);
        return v;
    }

    private AnalystEstimate estimate(BigDecimal targetPrice, String ratingLabel) {
        AnalystEstimate e = new AnalystEstimate();
        e.setTargetPrice(targetPrice);
        e.setRatingLabel(ratingLabel);
        e.setTargetDate(LocalDate.now());
        return e;
    }
}
