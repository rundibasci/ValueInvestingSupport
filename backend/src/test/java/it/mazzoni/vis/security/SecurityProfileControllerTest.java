package it.mazzoni.vis.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.mazzoni.vis.demo.GlobalExceptionHandler;
import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.exception.SymbolNotFoundException;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SecurityProfileControllerTest {

    @Mock SecurityRepository securityRepository;
    @Mock FundamentalSnapshotRepository fundamentalSnapshotRepository;
    @Mock RatioSnapshotRepository ratioSnapshotRepository;
    @Mock PriceQuoteRepository priceQuoteRepository;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SecurityProfileController(
                        securityRepository, fundamentalSnapshotRepository,
                        ratioSnapshotRepository, priceQuoteRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(om))
                .build();
    }

    @Test
    void profile_knownSymbol_returns200WithFields() throws Exception {
        Security s = security("AAPL", "Apple Inc.");
        FundamentalSnapshot snap = snapshot(LocalDate.now(), new BigDecimal("100000000000"));

        when(securityRepository.findBySymbol("AAPL")).thenReturn(Optional.of(s));
        when(fundamentalSnapshotRepository.findTopBySecurityAndPeriodOrderByReportDateDesc(eq(s), eq(Period.ANNUAL)))
                .thenReturn(Optional.of(snap));
        when(ratioSnapshotRepository.findTopBySecurityOrderByReportDateDesc(s)).thenReturn(Optional.empty());
        when(priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(s)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/securities/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.companyName").value("Apple Inc."))
                .andExpect(jsonPath("$.revenue").value(100000000000.0));
    }

    @Test
    void profile_unknownSymbol_returns404() throws Exception {
        when(securityRepository.findBySymbol("XYZ")).thenThrow(new SymbolNotFoundException("XYZ"));

        mockMvc.perform(get("/api/v1/securities/XYZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Symbol not found: XYZ"));
    }

    @Test
    void profile_staleSnapshot_returns422() throws Exception {
        Security s = security("STALE", "Stale Corp.");
        FundamentalSnapshot snap = snapshot(LocalDate.now().minusDays(10), new BigDecimal("50000000000"));

        when(securityRepository.findBySymbol("STALE")).thenReturn(Optional.of(s));
        when(fundamentalSnapshotRepository.findTopBySecurityAndPeriodOrderByReportDateDesc(eq(s), eq(Period.ANNUAL)))
                .thenReturn(Optional.of(snap));

        mockMvc.perform(get("/api/v1/securities/STALE"))
                .andExpect(status().isUnprocessableEntity());
    }

    private Security security(String symbol, String name) {
        Security s = new Security();
        s.setSymbol(symbol);
        s.setCompanyName(name);
        return s;
    }

    private FundamentalSnapshot snapshot(LocalDate date, BigDecimal revenue) {
        FundamentalSnapshot f = new FundamentalSnapshot();
        f.setPeriod(Period.ANNUAL);
        f.setReportDate(date);
        f.setRevenue(revenue);
        f.setNetIncome(new BigDecimal("20000000000"));
        f.setEps(new BigDecimal("6.43"));
        return f;
    }
}
