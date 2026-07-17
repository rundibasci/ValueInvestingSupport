package it.mazzoni.vis.portfolio.importing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.mazzoni.vis.demo.GlobalExceptionHandler;
import it.mazzoni.vis.portfolio.importing.dto.PortfolioImportCommitRequest;
import it.mazzoni.vis.portfolio.importing.dto.PortfolioImportCommitResponse;
import it.mazzoni.vis.portfolio.importing.dto.PortfolioImportHistoryResponse;
import it.mazzoni.vis.portfolio.importing.dto.PortfolioImportPreviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PortfolioImportControllerTest {

    @Mock
    PortfolioImportService service;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    final UUID importId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PortfolioImportController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper), new ByteArrayHttpMessageConverter())
                .build();
    }

    @Test
    void preview_uploadsMultipartFileAndReturnsPreview() throws Exception {
        PortfolioImportPreviewResponse response = new PortfolioImportPreviewResponse(importId, null,
                "Portfolio.csv", "abc123", "BROKER_IT_V1", "MERGE", "EUR", "PREVIEW",
                1, 1, 0, 0, null, Map.of(), LocalDateTime.now(), LocalDateTime.now().plusHours(24), List.of());
        when(service.preview(any(), any(), isNull(), eq("EUR"), isNull())).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile("file", "Portfolio.csv", "text/csv",
                "Prodotto,Codice\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/portfolios/imports/preview")
                        .file(file)
                        .param("baseCurrency", "EUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importId").value(importId.toString()))
                .andExpect(jsonPath("$.status").value("PREVIEW"));
    }

    @Test
    void commit_delegatesRequestBodyToService() throws Exception {
        PortfolioImportCommitResponse response = new PortfolioImportCommitResponse(importId, UUID.randomUUID(),
                "COMMITTED", "MERGE", 1, 0, 0, null, Map.of(), LocalDateTime.now(), List.of());
        when(service.commit(any(), eq(importId), any(PortfolioImportCommitRequest.class))).thenReturn(response);

        PortfolioImportCommitRequest request = new PortfolioImportCommitRequest(null, false, Set.of(), List.of());

        mockMvc.perform(post("/api/v1/portfolios/imports/{importId}/commit", importId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMMITTED"));
    }

    @Test
    void commit_ownershipSafeNotFound_propagatesAs404() throws Exception {
        when(service.commit(any(), eq(importId), any(PortfolioImportCommitRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio import not found"));

        PortfolioImportCommitRequest request = new PortfolioImportCommitRequest(null, false, Set.of(), List.of());

        mockMvc.perform(post("/api/v1/portfolios/imports/{importId}/commit", importId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void history_passesQueryParamsThrough() throws Exception {
        when(service.history(any(), isNull(), eq("COMMITTED"), eq(1), eq(10)))
                .thenReturn(new PortfolioImportHistoryResponse(List.of(), 1, 10, 0, 0));

        mockMvc.perform(get("/api/v1/portfolios/imports")
                        .param("status", "COMMITTED")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void detail_returnsPersistedPreview() throws Exception {
        PortfolioImportPreviewResponse response = new PortfolioImportPreviewResponse(importId, null,
                "Portfolio.csv", "abc123", "BROKER_IT_V1", "MERGE", "EUR", "COMMITTED",
                1, 1, 0, 0, null, Map.of(), LocalDateTime.now(), LocalDateTime.now().plusHours(24), List.of());
        when(service.detail(any(), eq(importId))).thenReturn(response);

        mockMvc.perform(get("/api/v1/portfolios/imports/{importId}", importId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMMITTED"));
    }

    @Test
    void detail_foreignImport_returnsOwnershipSafe404() throws Exception {
        when(service.detail(any(), eq(importId)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio import not found"));

        mockMvc.perform(get("/api/v1/portfolios/imports/{importId}", importId))
                .andExpect(status().isNotFound());
    }

    @Test
    void report_returnsCsvWithAttachmentHeaders() throws Exception {
        byte[] csv = "Row,Product\n".getBytes(StandardCharsets.UTF_8);
        when(service.reconciliationReport(any(), eq(importId))).thenReturn(csv);

        mockMvc.perform(get("/api/v1/portfolios/imports/{importId}/report.csv", importId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=portfolio-import-" + importId + ".csv"));
    }
}
