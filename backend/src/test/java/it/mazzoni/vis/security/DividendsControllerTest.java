package it.mazzoni.vis.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.mazzoni.vis.demo.GlobalExceptionHandler;
import it.mazzoni.vis.domain.entity.DividendRecord;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.DividendRecordRepository;
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
class DividendsControllerTest {

    @Mock SecurityRepository securityRepository;
    @Mock DividendRecordRepository dividendRecordRepository;

    DividendsService dividendsService = new DividendsService();
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DividendsController(securityRepository, dividendRecordRepository, dividendsService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(om))
                .build();
    }

    @Test
    void dividends_withHistory_returns200WithStreakAndCagr() throws Exception {
        Security s = security("AAPL");
        List<DividendRecord> records = List.of(
                dividend(LocalDate.now().minusYears(0), new BigDecimal("1.00")),
                dividend(LocalDate.now().minusYears(1), new BigDecimal("0.90")),
                dividend(LocalDate.now().minusYears(2), new BigDecimal("0.80")),
                dividend(LocalDate.now().minusYears(3), new BigDecimal("0.73"))
        );
        when(securityRepository.findBySymbol("AAPL")).thenReturn(Optional.of(s));
        when(dividendRecordRepository.findBySecurityOrderByExDividendDateDesc(s)).thenReturn(records);

        mockMvc.perform(get("/api/v1/securities/AAPL/dividends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.history.length()").value(4))
                .andExpect(jsonPath("$.streak").value(4))
                .andExpect(jsonPath("$.cagr3y").isNumber());
    }

    @Test
    void dividends_insufficientHistory_cagr3yIsNull() throws Exception {
        Security s = security("NEW");
        List<DividendRecord> records = List.of(
                dividend(LocalDate.now().minusYears(0), new BigDecimal("0.50")),
                dividend(LocalDate.now().minusYears(1), new BigDecimal("0.40"))
        );
        when(securityRepository.findBySymbol("NEW")).thenReturn(Optional.of(s));
        when(dividendRecordRepository.findBySecurityOrderByExDividendDateDesc(s)).thenReturn(records);

        mockMvc.perform(get("/api/v1/securities/NEW/dividends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cagr3y").doesNotExist());
    }

    @Test
    void dividends_unknownSymbol_returns404() throws Exception {
        when(securityRepository.findBySymbol("XYZ")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/securities/XYZ/dividends"))
                .andExpect(status().isNotFound());
    }

    private Security security(String symbol) {
        Security s = new Security();
        s.setSymbol(symbol);
        s.setCompanyName("Test Corp.");
        return s;
    }

    private DividendRecord dividend(LocalDate date, BigDecimal amount) {
        DividendRecord r = new DividendRecord();
        r.setExDividendDate(date);
        r.setAmount(amount);
        r.setCurrency("USD");
        return r;
    }
}
