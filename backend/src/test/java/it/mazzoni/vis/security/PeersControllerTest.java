package it.mazzoni.vis.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.mazzoni.vis.demo.GlobalExceptionHandler;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.entity.ValueScore;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import it.mazzoni.vis.domain.repository.ValueScoreRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PeersControllerTest {

    @Mock SecurityRepository securityRepository;
    @Mock RatioSnapshotRepository ratioSnapshotRepository;
    @Mock ValuationResultRepository valuationResultRepository;
    @Mock ValueScoreRepository valueScoreRepository;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PeersController(securityRepository, ratioSnapshotRepository,
                        valuationResultRepository, valueScoreRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(om))
                .build();
    }

    @Test
    void peers_withSectorPeers_returns200WithList() throws Exception {
        Security subject = security("AAPL", "Technology", new BigDecimal("3000000000000"));
        Security peer1 = security("MSFT", "Technology", new BigDecimal("2800000000000"));
        Security peer2 = security("GOOGL", "Technology", new BigDecimal("2000000000000"));

        when(securityRepository.findBySymbol("AAPL")).thenReturn(Optional.of(subject));
        when(securityRepository.findByActiveTrueAndSectorAndSymbolNot("Technology", "AAPL"))
                .thenReturn(List.of(peer1, peer2));

        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(any(Security.class)))
                .thenReturn(Optional.of(valuation(new BigDecimal("180.0"), new BigDecimal("200.0"))));
        when(valueScoreRepository.findTopBySecurityOrderByScoreDateDesc(any(Security.class)))
                .thenReturn(Optional.of(score(new BigDecimal("78.5"))));
        when(ratioSnapshotRepository.findTopBySecurityOrderByReportDateDesc(any(Security.class)))
                .thenReturn(Optional.of(ratioSnapshot()));

        mockMvc.perform(get("/api/v1/securities/AAPL/peers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.peers.length()").value(2))
                .andExpect(jsonPath("$.peers[0].symbol").value("MSFT"));
    }

    @Test
    void peers_noSectorPeers_returns200WithEmptyList() throws Exception {
        Security subject = security("AAPL", "Technology", new BigDecimal("3000000000000"));
        when(securityRepository.findBySymbol("AAPL")).thenReturn(Optional.of(subject));
        when(securityRepository.findByActiveTrueAndSectorAndSymbolNot("Technology", "AAPL")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/securities/AAPL/peers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peers").isEmpty());
    }

    @Test
    void peers_nullSector_returns200WithEmptyList() throws Exception {
        Security subject = security("NEW", null, null);
        when(securityRepository.findBySymbol("NEW")).thenReturn(Optional.of(subject));

        mockMvc.perform(get("/api/v1/securities/NEW/peers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peers").isEmpty());
    }

    @Test
    void peers_unknownSymbol_returns404() throws Exception {
        when(securityRepository.findBySymbol("XYZ")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/securities/XYZ/peers"))
                .andExpect(status().isNotFound());
    }

    private Security security(String symbol, String sector, BigDecimal marketCap) {
        Security s = new Security();
        s.setSymbol(symbol);
        s.setCompanyName(symbol + " Corp.");
        s.setSector(sector);
        s.setMarketCap(marketCap);
        return s;
    }

    private ValuationResult valuation(BigDecimal currentPrice, BigDecimal compositeFairValue) {
        ValuationResult v = new ValuationResult();
        v.setValuationDate(LocalDate.now());
        v.setCurrentPrice(currentPrice);
        v.setCompositeFairValue(compositeFairValue);
        v.setMarginOfSafety(new BigDecimal("10.00"));
        return v;
    }

    private ValueScore score(BigDecimal totalScore) {
        ValueScore s = new ValueScore();
        s.setScoreDate(LocalDate.now());
        s.setTotalScore(totalScore);
        return s;
    }

    private RatioSnapshot ratioSnapshot() {
        RatioSnapshot r = new RatioSnapshot();
        r.setReportDate(LocalDate.now());
        r.setPeRatio(new BigDecimal("28.4"));
        r.setRoic(new BigDecimal("26.4"));
        return r;
    }
}
