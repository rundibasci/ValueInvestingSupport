package it.mazzoni.vis.watchlist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.mazzoni.vis.demo.GlobalExceptionHandler;
import it.mazzoni.vis.watchlist.dto.AddWatchlistItemRequest;
import it.mazzoni.vis.watchlist.dto.AlertResponse;
import it.mazzoni.vis.watchlist.dto.UpdateWatchlistThresholdRequest;
import it.mazzoni.vis.watchlist.dto.WatchlistItemResponse;
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
class WatchlistControllerTest {

    @Mock WatchlistService watchlistService;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    final UUID itemId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WatchlistController(watchlistService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    // --- GET /api/v1/watchlist ---

    @Test
    void list_returnsItemList() throws Exception {
        WatchlistItemResponse item = item("AAPL", new BigDecimal("10.0"), new BigDecimal("30.0"));
        when(watchlistService.list(any())).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/watchlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].mosAlertMin").value(10.0))
                .andExpect(jsonPath("$[0].id").value(itemId.toString()));
    }

    @Test
    void list_emptyWatchlist_returnsEmptyArray() throws Exception {
        when(watchlistService.list(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/watchlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // --- POST /api/v1/watchlist ---

    @Test
    void add_validRequest_returns201WithItem() throws Exception {
        WatchlistItemResponse saved = item("AAPL", null, null);
        when(watchlistService.add(any(), any(AddWatchlistItemRequest.class))).thenReturn(saved);

        mockMvc.perform(post("/api/v1/watchlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddWatchlistItemRequest("AAPL", null, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.id").value(itemId.toString()));
    }

    @Test
    void add_blankSymbol_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/watchlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddWatchlistItemRequest("", null, null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void add_duplicateSymbol_returns409() throws Exception {
        when(watchlistService.add(any(), any(AddWatchlistItemRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                        "Symbol already in watchlist: AAPL"));

        mockMvc.perform(post("/api/v1/watchlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddWatchlistItemRequest("AAPL", null, null, null))))
                .andExpect(status().isConflict());
    }

    // --- PUT /api/v1/watchlist/{id} ---

    @Test
    void update_validRequest_returns200WithUpdatedItem() throws Exception {
        WatchlistItemResponse updated = item("AAPL", new BigDecimal("15.0"), new BigDecimal("25.0"));
        when(watchlistService.updateThresholds(any(), eq(itemId), any(UpdateWatchlistThresholdRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/v1/watchlist/" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateWatchlistThresholdRequest(
                                        new BigDecimal("15.0"), new BigDecimal("25.0"), null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.mosAlertMin").value(15.0))
                .andExpect(jsonPath("$.mosAlertMax").value(25.0));
    }

    @Test
    void update_unknownItem_returns404() throws Exception {
        when(watchlistService.updateThresholds(any(), eq(itemId), any(UpdateWatchlistThresholdRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Watchlist item not found"));

        mockMvc.perform(put("/api/v1/watchlist/" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    // --- DELETE /api/v1/watchlist/{id} ---

    @Test
    void delete_validItem_returns204() throws Exception {
        doNothing().when(watchlistService).remove(any(), eq(itemId));

        mockMvc.perform(delete("/api/v1/watchlist/" + itemId))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_unknownItem_returns404() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Watchlist item not found"))
                .when(watchlistService).remove(any(), eq(itemId));

        mockMvc.perform(delete("/api/v1/watchlist/" + itemId))
                .andExpect(status().isNotFound());
    }

    // --- GET /api/v1/watchlist/alerts ---

    @Test
    void listAlerts_returnsActiveAlerts() throws Exception {
        AlertResponse alert = new AlertResponse(
                UUID.randomUUID(), "MOS_ENTRY", "AAPL",
                new BigDecimal("15.0"), LocalDateTime.now().minusHours(2));
        when(watchlistService.listActiveAlerts(any())).thenReturn(List.of(alert));

        mockMvc.perform(get("/api/v1/watchlist/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].alertType").value("MOS_ENTRY"))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].threshold").value(15.0));
    }

    @Test
    void listAlerts_noAlerts_returnsEmptyArray() throws Exception {
        when(watchlistService.listActiveAlerts(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/watchlist/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // --- helpers ---

    private WatchlistItemResponse item(String symbol, BigDecimal min, BigDecimal max) {
        return new WatchlistItemResponse(itemId, symbol, min, max, null, LocalDateTime.now());
    }
}
