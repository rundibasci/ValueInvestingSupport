package it.mazzoni.vis.portfolio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.mazzoni.vis.demo.GlobalExceptionHandler;
import it.mazzoni.vis.portfolio.dto.AddHoldingRequest;
import it.mazzoni.vis.portfolio.dto.CreatePortfolioRequest;
import it.mazzoni.vis.portfolio.dto.HoldingDetailItem;
import it.mazzoni.vis.portfolio.dto.PortfolioDetailResponse;
import it.mazzoni.vis.portfolio.dto.PortfolioSummaryResponse;
import it.mazzoni.vis.portfolio.dto.UpdateHoldingRequest;
import it.mazzoni.vis.portfolio.dto.PortfolioSimulationResponse;
import it.mazzoni.vis.portfolio.dto.SimulationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PortfolioControllerTest {

    @Mock
    PortfolioService portfolioService;

    @Mock
    PortfolioSimulationService portfolioSimulationService;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    final UUID portfolioId = UUID.randomUUID();
    final UUID holdingId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PortfolioController(portfolioService, portfolioSimulationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    // --- GET /api/v1/portfolios ---

    @Test
    void list_returnsPortfolioSummaries() throws Exception {
        PortfolioSummaryResponse summary = summary("Growth Portfolio", 3);
        when(portfolioService.listPortfolios(any())).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/v1/portfolios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Growth Portfolio"))
                .andExpect(jsonPath("$[0].holdingCount").value(3))
                .andExpect(jsonPath("$[0].id").value(portfolioId.toString()));
    }

    @Test
    void list_emptyPortfolios_returnsEmptyArray() throws Exception {
        when(portfolioService.listPortfolios(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/portfolios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // --- POST /api/v1/portfolios ---

    @Test
    void create_validRequest_returns201WithSummary() throws Exception {
        PortfolioSummaryResponse created = summary("My Portfolio", 0);
        when(portfolioService.createPortfolio(any(), any(CreatePortfolioRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreatePortfolioRequest("My Portfolio", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("My Portfolio"))
                .andExpect(jsonPath("$.holdingCount").value(0))
                .andExpect(jsonPath("$.id").value(portfolioId.toString()));
    }

    @Test
    void create_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreatePortfolioRequest("", null))))
                .andExpect(status().isBadRequest());
    }

    // --- GET /api/v1/portfolios/{id} ---

    @Test
    void detail_returnsPortfolioDetail() throws Exception {
        PortfolioDetailResponse detail = new PortfolioDetailResponse(
                portfolioId, "Growth Portfolio", null,
                new BigDecimal("1800.00"), new BigDecimal("16.67"),
                List.of(holding("AAPL", new BigDecimal("10"), new BigDecimal("180.00"),
                        new BigDecimal("100.00"), new BigDecimal("210.00"),
                        new BigDecimal("16.67"), "QUALITY_VALUE")),
                LocalDateTime.now(), LocalDateTime.now());
        when(portfolioService.getPortfolioDetail(any(), eq(portfolioId))).thenReturn(detail);

        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Growth Portfolio"))
                .andExpect(jsonPath("$.totalValue").value(1800.00))
                .andExpect(jsonPath("$.weightedMoS").value(16.67))
                .andExpect(jsonPath("$.holdings[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.holdings[0].currentPrice").value(180.00));
    }

    @Test
    void detail_unknownPortfolio_returns404() throws Exception {
        when(portfolioService.getPortfolioDetail(any(), eq(portfolioId)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found"));

        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId))
                .andExpect(status().isNotFound());
    }

    @Test
    void simulate_invalidBudget_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budget\":0}"))
                .andExpect(status().isBadRequest());
    }

    // --- POST /api/v1/portfolios/{id}/holdings ---

    @Test
    void addHolding_validRequest_returns201() throws Exception {
        HoldingDetailItem item = holding("AAPL", new BigDecimal("10"), null, null, null, null, null);
        when(portfolioService.addHolding(any(), eq(portfolioId), any(AddHoldingRequest.class)))
                .thenReturn(item);

        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/holdings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddHoldingRequest("AAPL", new BigDecimal("10"), null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.id").value(holdingId.toString()));
    }

    @Test
    void addHolding_blankSymbol_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/holdings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddHoldingRequest("", new BigDecimal("10"), null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addHolding_nullQuantity_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/holdings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"quantity\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addHolding_unknownPortfolio_returns404() throws Exception {
        when(portfolioService.addHolding(any(), eq(portfolioId), any(AddHoldingRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found"));

        mockMvc.perform(post("/api/v1/portfolios/" + portfolioId + "/holdings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddHoldingRequest("AAPL", new BigDecimal("10"), null, null))))
                .andExpect(status().isNotFound());
    }

    // --- PUT /api/v1/portfolios/{id}/holdings/{holdingId} ---

    @Test
    void updateHolding_validRequest_returns200() throws Exception {
        HoldingDetailItem updated = holding("AAPL", new BigDecimal("15"), null, null, null, null, null);
        when(portfolioService.updateHolding(any(), eq(portfolioId), eq(holdingId),
                any(UpdateHoldingRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/portfolios/" + portfolioId + "/holdings/" + holdingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateHoldingRequest(new BigDecimal("15"), null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(15));
    }

    @Test
    void updateHolding_unknownHolding_returns404() throws Exception {
        when(portfolioService.updateHolding(any(), eq(portfolioId), eq(holdingId),
                any(UpdateHoldingRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Holding not found"));

        mockMvc.perform(put("/api/v1/portfolios/" + portfolioId + "/holdings/" + holdingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateHoldingRequest(new BigDecimal("15"), null, null))))
                .andExpect(status().isNotFound());
    }

    // --- DELETE /api/v1/portfolios/{id}/holdings/{holdingId} ---

    @Test
    void removeHolding_validId_returns204() throws Exception {
        doNothing().when(portfolioService).removeHolding(any(), eq(portfolioId), eq(holdingId));

        mockMvc.perform(delete("/api/v1/portfolios/" + portfolioId + "/holdings/" + holdingId))
                .andExpect(status().isNoContent());
    }

    @Test
    void removeHolding_unknownHolding_returns404() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Holding not found"))
                .when(portfolioService).removeHolding(any(), eq(portfolioId), eq(holdingId));

        mockMvc.perform(delete("/api/v1/portfolios/" + portfolioId + "/holdings/" + holdingId))
                .andExpect(status().isNotFound());
    }

    // --- helpers ---

    private PortfolioSummaryResponse summary(String name, int holdingCount) {
        return new PortfolioSummaryResponse(portfolioId, name, null, holdingCount,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private HoldingDetailItem holding(String symbol, BigDecimal quantity,
                                      BigDecimal currentPrice, BigDecimal weightPercent,
                                      BigDecimal fairValue, BigDecimal mos, String rec) {
        BigDecimal currentValue = currentPrice != null ? quantity.multiply(currentPrice) : null;
        return new HoldingDetailItem(holdingId, symbol, quantity, null, "USD",
                currentPrice, currentValue, weightPercent, fairValue, mos, rec, LocalDateTime.now());
    }
}
