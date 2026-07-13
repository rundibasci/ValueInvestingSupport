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
        when(ratioSnapshotRepository.findBySecurity(s))
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
        when(ratioSnapshotRepository.findBySecurity(s)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/securities/KO/ratios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratios").isEmpty());
    }

    @Test
    void ratios_repeatedPersistedHistory_returnsCurrentRowOnly() throws Exception {
        Security s = security("INGR");
        when(securityRepository.findBySymbol("INGR")).thenReturn(Optional.of(s));
        when(ratioSnapshotRepository.findBySecurity(s))
                .thenReturn(List.of(
                        ratioSnapshot(LocalDate.of(2026, 7, 3)),
                        ratioSnapshot(LocalDate.of(2025, 12, 31)),
                        ratioSnapshot(LocalDate.of(2024, 12, 31)),
                        ratioSnapshot(LocalDate.of(2023, 12, 31)),
                        ratioSnapshot(LocalDate.of(2022, 12, 31)),
                        ratioSnapshot(LocalDate.of(2021, 12, 31))));

        mockMvc.perform(get("/api/v1/securities/INGR/ratios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratios.length()").value(1))
                .andExpect(jsonPath("$.ratios[0].date").value("2026-07-03"))
                .andExpect(jsonPath("$.ratios[0].pe").value(28.4));
    }

    @Test
    void ratios_currentFmpRowWithRepeatedLegacyTail_returnsCurrentRowOnly() throws Exception {
        Security s = security("INGR");
        when(securityRepository.findBySymbol("INGR")).thenReturn(Optional.of(s));
        when(ratioSnapshotRepository.findBySecurity(s))
                .thenReturn(List.of(
                        ratioSnapshot(LocalDate.of(2026, 7, 13),
                                "9.7060", "0.1182", "0.1680", "0.2532"),
                        ratioSnapshot(LocalDate.of(2025, 12, 31),
                                "9.3865", null, "0.1619", null),
                        ratioSnapshot(LocalDate.of(2024, 12, 31),
                                "9.3865", null, "0.1619", null),
                        ratioSnapshot(LocalDate.of(2023, 12, 31),
                                "9.3865", null, "0.1619", null),
                        ratioSnapshot(LocalDate.of(2022, 12, 31),
                                "9.3865", null, "0.1619", null),
                        ratioSnapshot(LocalDate.of(2021, 12, 31),
                                "9.3865", null, "0.1619", null)));

        mockMvc.perform(get("/api/v1/securities/INGR/ratios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratios.length()").value(1))
                .andExpect(jsonPath("$.ratios[0].date").value("2026-07-13"))
                .andExpect(jsonPath("$.ratios[0].roic").value(0.1182))
                .andExpect(jsonPath("$.ratios[0].grossMargin").value(0.2532));
    }

    private Security security(String symbol) {
        Security s = new Security();
        s.setSymbol(symbol);
        s.setCompanyName("Test Corp.");
        return s;
    }

    private RatioSnapshot ratioSnapshot(LocalDate date) {
        return ratioSnapshot(date, "28.4", "26.4", "147.3", "46.2");
    }

    private RatioSnapshot ratioSnapshot(LocalDate date, String pe, String roic, String roe, String grossMargin) {
        RatioSnapshot r = new RatioSnapshot();
        r.setPeriod(Period.TTM);
        r.setReportDate(date);
        r.setPeRatio(decimal(pe));
        r.setRoic(decimal(roic));
        r.setRoe(decimal(roe));
        r.setDebtToEquity(new BigDecimal("1.87"));
        r.setGrossMargin(decimal(grossMargin));
        r.setDividendYield(new BigDecimal("0.56"));
        return r;
    }

    private BigDecimal decimal(String value) {
        return value != null ? new BigDecimal(value) : null;
    }
}
