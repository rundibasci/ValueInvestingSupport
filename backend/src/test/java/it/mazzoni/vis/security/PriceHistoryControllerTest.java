package it.mazzoni.vis.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.mazzoni.vis.demo.GlobalExceptionHandler;
import it.mazzoni.vis.domain.HistoricalPriceQuote;
import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.marketdata.MarketDataClient;
import it.mazzoni.vis.marketdata.MarketDataException;
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
class PriceHistoryControllerTest {

    @Mock SecurityRepository securityRepository;
    @Mock PriceQuoteRepository priceQuoteRepository;
    @Mock MarketDataClient marketDataClient;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PriceHistoryController(securityRepository, priceQuoteRepository, marketDataClient))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void prices_returnsFmpHistorySortedAscending() throws Exception {
        Security security = security("INGR");
        when(securityRepository.findBySymbol("INGR")).thenReturn(Optional.of(security));
        when(marketDataClient.getHistoricalPrices(eq("INGR"), any(), any())).thenReturn(List.of(
                new HistoricalPriceQuote("INGR", LocalDate.of(2026, 7, 10), new BigDecimal("99.08"), 100L),
                new HistoricalPriceQuote("INGR", LocalDate.of(2026, 7, 9), new BigDecimal("98.50"), 90L)
        ));

        mockMvc.perform(get("/api/v1/securities/INGR/prices").param("range", "10y"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("FMP"))
                .andExpect(jsonPath("$.range").value("10y"))
                .andExpect(jsonPath("$.prices[0].date").value("2026-07-09"))
                .andExpect(jsonPath("$.prices[1].close").value(99.08));
    }

    @Test
    void prices_fallsBackToLocalQuotesWhenProviderUnavailable() throws Exception {
        Security security = security("INGR");
        when(securityRepository.findBySymbol("INGR")).thenReturn(Optional.of(security));
        when(marketDataClient.getHistoricalPrices(eq("INGR"), any(), any())).thenThrow(
                new MarketDataException(MarketDataException.ErrorCode.SERVICE_UNAVAILABLE, "INGR"));
        when(priceQuoteRepository.findBySecurityAndQuoteDateBetweenOrderByQuoteDateDesc(eq(security), any(), any()))
                .thenReturn(List.of(quote(security, LocalDate.of(2026, 7, 10), "99.08")));

        mockMvc.perform(get("/api/v1/securities/INGR/prices").param("range", "10y"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("LOCAL"))
                .andExpect(jsonPath("$.prices[0].date").value("2026-07-10"));
    }

    private static Security security(String symbol) {
        Security security = new Security();
        security.setSymbol(symbol);
        security.setCompanyName(symbol);
        return security;
    }

    private static PriceQuote quote(Security security, LocalDate date, String close) {
        PriceQuote quote = new PriceQuote();
        quote.setSecurity(security);
        quote.setQuoteDate(date);
        quote.setClose(new BigDecimal(close));
        return quote;
    }
}
