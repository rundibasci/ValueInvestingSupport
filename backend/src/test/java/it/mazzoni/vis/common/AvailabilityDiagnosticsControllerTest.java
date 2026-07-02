package it.mazzoni.vis.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.mazzoni.vis.common.dto.AvailabilityDiagnosticResponse;
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
class AvailabilityDiagnosticsControllerTest {

    @Mock AvailabilityDiagnosticsService service;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AvailabilityDiagnosticsController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();
    }

    @Test
    void diagnostics_returnsStructuredExamples() throws Exception {
        when(service.all()).thenReturn(List.of(new AvailabilityDiagnosticResponse(
                AvailabilityStatus.GUARDRAIL_BLOCKED,
                "DCF valuation",
                "A valuation model was skipped because eligibility rules were not met.",
                List.of("security review"),
                "Inspect the guardrail reason.",
                "Guardrails prevent weak model output from looking precise."
        )));

        mockMvc.perform(get("/api/v1/availability/diagnostics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("GUARDRAIL_BLOCKED"))
                .andExpect(jsonPath("$[0].exampleCategory").value("DCF valuation"))
                .andExpect(jsonPath("$[0].surfaces[0]").value("security review"));
    }
}
