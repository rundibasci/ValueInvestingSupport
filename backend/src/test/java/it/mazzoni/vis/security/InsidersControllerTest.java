package it.mazzoni.vis.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.mazzoni.vis.demo.GlobalExceptionHandler;
import it.mazzoni.vis.domain.entity.InsiderTrade;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.TransactionType;
import it.mazzoni.vis.domain.repository.InsiderTradeRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InsidersControllerTest {

    @Mock SecurityRepository securityRepository;
    @Mock InsiderTradeRepository insiderTradeRepository;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new InsidersController(securityRepository, insiderTradeRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(om))
                .build();
    }

    @Test
    void insiders_withTrades_returns200WithList() throws Exception {
        Security s = security("AAPL");
        InsiderTrade trade = trade("Tim Cook", "CEO", TransactionType.SELL, 100000L, new BigDecimal("185.0"));

        when(securityRepository.findBySymbol("AAPL")).thenReturn(Optional.of(s));
        when(insiderTradeRepository.findBySecurityAndTradeDateGreaterThanEqualOrderByTradeDateDesc(eq(s), any(LocalDate.class)))
                .thenReturn(List.of(trade));

        mockMvc.perform(get("/api/v1/securities/AAPL/insiders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.trades.length()").value(1))
                .andExpect(jsonPath("$.trades[0].name").value("Tim Cook"))
                .andExpect(jsonPath("$.trades[0].transactionType").value("SELL"));
    }

    @Test
    void insiders_noRecentTrades_returns200WithEmptyList() throws Exception {
        Security s = security("KO");
        when(securityRepository.findBySymbol("KO")).thenReturn(Optional.of(s));
        when(insiderTradeRepository.findBySecurityAndTradeDateGreaterThanEqualOrderByTradeDateDesc(eq(s), any(LocalDate.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/securities/KO/insiders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trades").isEmpty());
    }

    @Test
    void insiders_unknownSymbol_returns404() throws Exception {
        when(securityRepository.findBySymbol("XYZ")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/securities/XYZ/insiders"))
                .andExpect(status().isNotFound());
    }

    private Security security(String symbol) {
        Security s = new Security();
        s.setSymbol(symbol);
        s.setCompanyName("Test Corp.");
        return s;
    }

    private InsiderTrade trade(String name, String title, TransactionType type, Long shares, BigDecimal price) {
        InsiderTrade t = new InsiderTrade();
        t.setTradeDate(LocalDate.now().minusDays(30));
        t.setInsiderName(name);
        t.setTitle(title);
        t.setTransactionType(type);
        t.setShares(shares);
        t.setPricePerShare(price);
        t.setTradeValue(price.multiply(BigDecimal.valueOf(shares)));
        return t;
    }
}
