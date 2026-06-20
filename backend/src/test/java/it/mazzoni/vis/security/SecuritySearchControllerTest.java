package it.mazzoni.vis.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.mazzoni.vis.demo.GlobalExceptionHandler;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SecuritySearchControllerTest {

    @Mock SecurityRepository securityRepository;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SecuritySearchController(securityRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(om))
                .build();
    }

    @Test
    void search_bySymbol_returns200WithResults() throws Exception {
        Security s = security("AAPL", "Apple Inc.", "Technology", "NASDAQ");
        when(securityRepository.findTop10BySymbolContainingIgnoreCaseOrCompanyNameContainingIgnoreCase("AAPL", "AAPL"))
                .thenReturn(List.of(s));

        mockMvc.perform(get("/api/v1/securities/search").param("q", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].companyName").value("Apple Inc."))
                .andExpect(jsonPath("$[0].sector").value("Technology"));
    }

    @Test
    void search_blankQuery_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/securities/search").param("q", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void search_noQueryParam_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/securities/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    private Security security(String symbol, String name, String sector, String exchange) {
        Security s = new Security();
        s.setSymbol(symbol);
        s.setCompanyName(name);
        s.setSector(sector);
        s.setExchange(exchange);
        return s;
    }
}
