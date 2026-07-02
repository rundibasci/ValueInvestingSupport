package it.mazzoni.vis.screener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.mazzoni.vis.screener.dto.ConservativeComparisonMetricResponse;
import it.mazzoni.vis.screener.dto.ConservativeComparisonRowResponse;
import it.mazzoni.vis.screener.dto.ConservativeCriterionResponse;
import it.mazzoni.vis.screener.dto.ConservativeEmptyStateDiagnosticResponse;
import it.mazzoni.vis.screener.dto.ConservativePresetResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ConservativeWorkflowControllerTest {

    @Mock ConservativeWorkflowService service;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ConservativeWorkflowController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void preset_returnsCriteriaAndDecisionSupportNote() throws Exception {
        when(service.preset()).thenReturn(new ConservativePresetResponse(
                "conservative",
                "Conservative research preset.",
                ScreenerPresets.CONSERVATIVE,
                List.of(new ConservativeCriterionResponse("minMarginOfSafety", "Positive margin of safety", "15% minimum", "Valuation discipline.", "Lower gradually.")),
                "Use these signals to structure research."
        ));

        mockMvc.perform(get("/api/v1/conservative-workflow/preset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("conservative"))
                .andExpect(jsonPath("$.criteria.minMarginOfSafety").value(15))
                .andExpect(jsonPath("$.criteriaSummary[0].key").value("minMarginOfSafety"));
    }

    @Test
    void emptyStateDiagnostics_returnsLikelyEliminatorsWithoutChangingCriteria() throws Exception {
        when(service.emptyStateDiagnostics(any())).thenReturn(new ConservativeEmptyStateDiagnosticResponse(
                List.of(new ConservativeCriterionResponse("altmanZone", "Liquidity and solvency resilience", "SAFE", "Avoids distress signals.", "Allow GREY with notes.")),
                List.of("Lower the margin-of-safety floor."),
                true,
                "The current criteria are preserved."
        ));

        mockMvc.perform(post("/api/v1/conservative-workflow/empty-state-diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ScreenerPresets.CONSERVATIVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentCriteriaPreserved").value(true))
                .andExpect(jsonPath("$.likelyEliminators[0].key").value("altmanZone"));
    }

    @Test
    void agentOneComparison_returnsComparisonRows() throws Exception {
        when(service.agentOneComparison()).thenReturn(List.of(new ConservativeComparisonRowResponse(
                "KO",
                "Coca-Cola Co.",
                List.of(new ConservativeComparisonMetricResponse("valuation", "Margin of safety", "negative", "AVAILABLE", "Coverage visible."))
        )));

        mockMvc.perform(get("/api/v1/conservative-workflow/agent-one-comparison"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("KO"))
                .andExpect(jsonPath("$[0].metrics[0].group").value("valuation"));
    }
}
