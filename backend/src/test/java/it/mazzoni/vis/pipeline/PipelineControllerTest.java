package it.mazzoni.vis.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.mazzoni.vis.demo.GlobalExceptionHandler;
import it.mazzoni.vis.pipeline.dto.PipelineRunRequest;
import it.mazzoni.vis.pipeline.dto.PipelineRunResult;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PipelineControllerTest {

    @Mock PipelineRunService pipelineRunService;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        PipelineController controller = new PipelineController(pipelineRunService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void pipelineRun_withTwoTickers_returns200WithRankedResults() throws Exception {
        when(pipelineRunService.run(anyList())).thenReturn(List.of(
                PipelineRunResult.success("KO", "Coca-Cola Co.",
                        new BigDecimal("58.20"), new BigDecimal("18.40"),
                        new BigDecimal("72.5"), null),
                PipelineRunResult.success("AAPL", "Apple Inc.",
                        new BigDecimal("210.50"), new BigDecimal("13.60"),
                        new BigDecimal("65.0"), null)));

        mockMvc.perform(post("/api/v1/admin/pipeline-run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PipelineRunRequest(List.of("KO", "AAPL")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("KO"))
                .andExpect(jsonPath("$[0].totalScore").value(72.5))
                .andExpect(jsonPath("$[0].error").doesNotExist())
                .andExpect(jsonPath("$[1].symbol").value("AAPL"))
                .andExpect(jsonPath("$[1].totalScore").value(65.0));
    }

    @Test
    void pipelineRun_oneTickerFails_returnsErrorRow() throws Exception {
        when(pipelineRunService.run(anyList())).thenReturn(List.of(
                PipelineRunResult.failed("XYZ", "not found"),
                PipelineRunResult.success("AAPL", "Apple Inc.",
                        new BigDecimal("210.50"), new BigDecimal("13.60"),
                        new BigDecimal("65.0"), null)));

        mockMvc.perform(post("/api/v1/admin/pipeline-run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PipelineRunRequest(List.of("XYZ", "AAPL")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("XYZ"))
                .andExpect(jsonPath("$[0].error").value("not found"))
                .andExpect(jsonPath("$[1].symbol").value("AAPL"))
                .andExpect(jsonPath("$[1].error").doesNotExist());
    }
}
