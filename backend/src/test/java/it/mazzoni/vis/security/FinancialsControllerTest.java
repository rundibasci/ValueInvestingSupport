package it.mazzoni.vis.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.mazzoni.vis.demo.GlobalExceptionHandler;
import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FinancialsControllerTest {

    @Mock SecurityRepository securityRepository;
    @Mock FundamentalSnapshotRepository fundamentalSnapshotRepository;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FinancialsController(securityRepository, fundamentalSnapshotRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(om))
                .build();
    }

    @Test
    void financials_knownSymbol_returns200WithAnnualsAndQuarters() throws Exception {
        Security s = security("AAPL");
        when(securityRepository.findBySymbol("AAPL")).thenReturn(Optional.of(s));
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(eq(s), eq(Period.ANNUAL)))
                .thenReturn(List.of(annualSnapshot(2025), annualSnapshot(2024)));
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(eq(s), eq(Period.QUARTERLY)))
                .thenReturn(List.of(quarterlySnapshot(2025, 2)));
        when(fundamentalSnapshotRepository.findTopBySecurityAndPeriodOrderByReportDateDesc(eq(s), eq(Period.TTM)))
                .thenReturn(Optional.of(ttmSnapshot()));

        mockMvc.perform(get("/api/v1/securities/AAPL/financials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.annuals").isArray())
                .andExpect(jsonPath("$.annuals.length()").value(2))
                .andExpect(jsonPath("$.quarters.length()").value(1))
                .andExpect(jsonPath("$.ttm").isNotEmpty());
    }

    @Test
    void financials_unknownSymbol_returns404() throws Exception {
        when(securityRepository.findBySymbol("XYZ")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/securities/XYZ/financials"))
                .andExpect(status().isNotFound());
    }

    @Test
    void financials_unauthenticated_standaloneAllows200() throws Exception {
        // Standalone MockMvc bypasses Spring Security — auth check is validated in the IT
        Security s = security("KO");
        when(securityRepository.findBySymbol("KO")).thenReturn(Optional.of(s));
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(eq(s), eq(Period.ANNUAL)))
                .thenReturn(List.of(annualSnapshot(2024)));
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(eq(s), eq(Period.QUARTERLY)))
                .thenReturn(List.of());
        when(fundamentalSnapshotRepository.findTopBySecurityAndPeriodOrderByReportDateDesc(eq(s), eq(Period.TTM)))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/securities/KO/financials"))
                .andExpect(status().isOk());
    }

    private Security security(String symbol) {
        Security s = new Security();
        s.setSymbol(symbol);
        s.setCompanyName("Test Corp.");
        return s;
    }

    private FundamentalSnapshot annualSnapshot(int year) {
        FundamentalSnapshot f = new FundamentalSnapshot();
        f.setPeriod(Period.ANNUAL);
        f.setFiscalYear(year);
        f.setFiscalQuarter(null);
        f.setReportDate(LocalDate.of(year, 9, 30));
        f.setRevenue(new BigDecimal("100000000000"));
        f.setNetIncome(new BigDecimal("20000000000"));
        f.setEps(new BigDecimal("6.43"));
        return f;
    }

    private FundamentalSnapshot quarterlySnapshot(int year, int quarter) {
        FundamentalSnapshot f = new FundamentalSnapshot();
        f.setPeriod(Period.QUARTERLY);
        f.setFiscalYear(year);
        f.setFiscalQuarter(quarter);
        f.setReportDate(LocalDate.of(year, 3, 31));
        f.setRevenue(new BigDecimal("25000000000"));
        f.setNetIncome(new BigDecimal("5000000000"));
        f.setEps(new BigDecimal("1.60"));
        return f;
    }

    private FundamentalSnapshot ttmSnapshot() {
        FundamentalSnapshot f = new FundamentalSnapshot();
        f.setPeriod(Period.TTM);
        f.setReportDate(LocalDate.now());
        f.setRevenue(new BigDecimal("110000000000"));
        f.setNetIncome(new BigDecimal("22000000000"));
        f.setEps(new BigDecimal("7.00"));
        return f;
    }
}
