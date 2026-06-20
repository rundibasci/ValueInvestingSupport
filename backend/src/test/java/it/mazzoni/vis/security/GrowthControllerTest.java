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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GrowthControllerTest {

    @Mock SecurityRepository securityRepository;
    @Mock FundamentalSnapshotRepository fundamentalSnapshotRepository;

    GrowthService growthService = new GrowthService();
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new GrowthController(securityRepository, fundamentalSnapshotRepository, growthService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(om))
                .build();
    }

    @Test
    void growth_with4Annuals_cagr3yNonNullOthersNull() throws Exception {
        Security s = security("AAPL");
        when(securityRepository.findBySymbol("AAPL")).thenReturn(Optional.of(s));
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(eq(s), eq(Period.ANNUAL)))
                .thenReturn(annuals(4));

        mockMvc.perform(get("/api/v1/securities/AAPL/growth"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.revenue.cagr3y").isNumber())
                .andExpect(jsonPath("$.revenue.cagr5y").doesNotExist())
                .andExpect(jsonPath("$.revenue.cagr10y").doesNotExist());
    }

    @Test
    void growth_withInsufficientHistory_allCagrNull() throws Exception {
        Security s = security("NEW");
        when(securityRepository.findBySymbol("NEW")).thenReturn(Optional.of(s));
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(eq(s), eq(Period.ANNUAL)))
                .thenReturn(annuals(2));

        mockMvc.perform(get("/api/v1/securities/NEW/growth"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue.cagr3y").doesNotExist());
    }

    @Test
    void growth_unknownSymbol_returns404() throws Exception {
        when(securityRepository.findBySymbol("XYZ")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/securities/XYZ/growth"))
                .andExpect(status().isNotFound());
    }

    private Security security(String symbol) {
        Security s = new Security();
        s.setSymbol(symbol);
        s.setCompanyName("Test Corp.");
        return s;
    }

    private List<FundamentalSnapshot> annuals(int count) {
        List<FundamentalSnapshot> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            FundamentalSnapshot f = new FundamentalSnapshot();
            f.setPeriod(Period.ANNUAL);
            f.setFiscalYear(2025 - i);
            f.setReportDate(LocalDate.of(2025 - i, 9, 30));
            f.setRevenue(new BigDecimal("100000000000").subtract(BigDecimal.valueOf(i * 5000000000L)));
            f.setFreeCashFlow(new BigDecimal("20000000000").subtract(BigDecimal.valueOf(i * 1000000000L)));
            f.setEps(new BigDecimal("6.43").subtract(BigDecimal.valueOf(i * 0.3)));
            list.add(f);
        }
        return list;
    }
}
