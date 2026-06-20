package it.mazzoni.vis.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.mazzoni.vis.demo.GlobalExceptionHandler;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RatiosControllerTest {

    @Mock SecurityRepository securityRepository;
    @Mock RatioSnapshotRepository ratioSnapshotRepository;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RatiosController(securityRepository, ratioSnapshotRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(om))
                .build();
    }

    @Test
    void ratios_knownSymbol_returns200WithList() throws Exception {
        Security s = security("AAPL");
        when(securityRepository.findBySymbol("AAPL")).thenReturn(Optional.of(s));
        when(ratioSnapshotRepository.findTop10BySecurityOrderByReportDateDesc(s))
                .thenReturn(List.of(ratioSnapshot(LocalDate.of(2025, 9, 30)),
                        ratioSnapshot(LocalDate.of(2024, 9, 30))));

        mockMvc.perform(get("/api/v1/securities/AAPL/ratios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.ratios.length()").value(2))
                .andExpect(jsonPath("$.ratios[0].pe").value(28.4));
    }

    @Test
    void ratios_unknownSymbol_returns404() throws Exception {
        when(securityRepository.findBySymbol("XYZ")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/securities/XYZ/ratios"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ratios_noRatioData_returns200WithEmptyList() throws Exception {
        Security s = security("KO");
        when(securityRepository.findBySymbol("KO")).thenReturn(Optional.of(s));
        when(ratioSnapshotRepository.findTop10BySecurityOrderByReportDateDesc(s)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/securities/KO/ratios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratios").isEmpty());
    }

    private Security security(String symbol) {
        Security s = new Security();
        s.setSymbol(symbol);
        s.setCompanyName("Test Corp.");
        return s;
    }

    private RatioSnapshot ratioSnapshot(LocalDate date) {
        RatioSnapshot r = new RatioSnapshot();
        r.setPeriod(Period.TTM);
        r.setReportDate(date);
        r.setPeRatio(new BigDecimal("28.4"));
        r.setRoic(new BigDecimal("26.4"));
        r.setRoe(new BigDecimal("147.3"));
        r.setDebtToEquity(new BigDecimal("1.87"));
        r.setGrossMargin(new BigDecimal("46.2"));
        r.setDividendYield(new BigDecimal("0.56"));
        return r;
    }
}
