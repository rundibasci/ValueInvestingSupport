package it.mazzoni.vis.portfolio.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PortfolioAnalysisControllerTest {
    @Mock PortfolioAnalysisService service;
    MockMvc mvc;
    @BeforeEach void setup(){mvc=MockMvcBuilders.standaloneSetup(new PortfolioAnalysisController(service))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper())).build();}

    @Test void startReturnsAcceptedRunDescriptor() throws Exception {
        UUID portfolioId=UUID.randomUUID(), importId=UUID.randomUUID(), runId=UUID.randomUUID();
        when(service.submit(any(),eq(portfolioId),eq(importId))).thenReturn(new PortfolioAnalysisAcceptedResponse(
                runId,"QUEUED",4,"/status","/outcomes",1500,false));
        mvc.perform(post("/api/v1/portfolios/{portfolioId}/analysis-runs",portfolioId)
                .contentType("application/json").content("{\"importId\":\""+importId+"\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.analysisRunId").value(runId.toString()))
                .andExpect(jsonPath("$.total").value(4)).andExpect(jsonPath("$.joined").value(false));
    }
}
