package it.mazzoni.vis.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = SpaWebConfig.class)
class SpaWebConfigTest {

    @Autowired
    MockMvc mvc;

    @Test
    void forwardsTopLevelReactRoute() throws Exception {
        mvc.perform(get("/screener"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void forwardsDynamicSecurityReviewRoute() throws Exception {
        mvc.perform(get("/securities/AAPL/review"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }
}
