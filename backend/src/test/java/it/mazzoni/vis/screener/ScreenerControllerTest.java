package it.mazzoni.vis.screener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.mazzoni.vis.common.dto.AvailabilityResponse;
import it.mazzoni.vis.demo.GlobalExceptionHandler;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.screener.dto.ScreenerRequest;
import it.mazzoni.vis.screener.dto.ScreenerResponse;
import it.mazzoni.vis.screener.dto.ScreenerResultItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ScreenerControllerTest {

    @Mock ScreenerService screenerService;
    @Mock SecurityRepository securityRepository;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ScreenerController(screenerService, securityRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void screen_basicRequest_returns200WithResultsAndPagination() throws Exception {
        ScreenerResultItem item = new ScreenerResultItem(
                "KO", "Coca-Cola Co.", "Consumer Staples", "NYSE",
                new BigDecimal("62.10"), new BigDecimal("73.80"),
                new BigDecimal("18.40"), new BigDecimal("72.50"),
                new BigDecimal("20.00"), new BigDecimal("25.00"),
                new BigDecimal("14.00"), new BigDecimal("10.00"),
                new BigDecimal("0.00"),
                "QUALITY_VALUE", LocalDate.of(2026, 6, 20),
                AvailabilityResponse.available(LocalDate.of(2026, 6, 20)),
                AvailabilityResponse.available(LocalDate.of(2026, 6, 20)));

        ScreenerResponse response = new ScreenerResponse(List.of(item), 0, 20, 1L, 1);
        when(screenerService.search(any(ScreenerRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/screener")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].symbol").value("KO"))
                .andExpect(jsonPath("$.results[0].totalScore").value(72.50))
                .andExpect(jsonPath("$.results[0].recommendation").value("QUALITY_VALUE"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void screen_withFilters_passes200() throws Exception {
        when(screenerService.search(any(ScreenerRequest.class)))
                .thenReturn(new ScreenerResponse(List.of(), 0, 20, 0L, 0));

        String body = objectMapper.writeValueAsString(new ScreenerRequest(
                "Technology", "NASDAQ",
                new BigDecimal("15"), null,
                new BigDecimal("60"),
                new BigDecimal("10"), new BigDecimal("1.0"),
                null, null,
                "totalScore", "DESC", 0, 20));

        mockMvc.perform(post("/api/v1/screener")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void presets_returns200WithThreeKeys() throws Exception {
        mockMvc.perform(get("/api/v1/screener/presets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graham").exists())
                .andExpect(jsonPath("$.dividend").exists())
                .andExpect(jsonPath("$.quality").exists());
    }

    @Test
    void sectors_returns200WithList() throws Exception {
        when(securityRepository.findDistinctSectors())
                .thenReturn(List.of("Consumer Staples", "Technology"));

        mockMvc.perform(get("/api/v1/screener/sectors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Consumer Staples"))
                .andExpect(jsonPath("$[1]").value("Technology"));
    }

    @Test
    void exchanges_returns200WithList() throws Exception {
        when(securityRepository.findDistinctExchanges())
                .thenReturn(List.of("NASDAQ", "NYSE"));

        mockMvc.perform(get("/api/v1/screener/exchanges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("NASDAQ"))
                .andExpect(jsonPath("$[1]").value("NYSE"));
    }
}
