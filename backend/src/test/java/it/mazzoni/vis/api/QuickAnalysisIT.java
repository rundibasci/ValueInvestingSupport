package it.mazzoni.vis.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.mazzoni.vis.api.dto.QuickAnalysisResponse;
import it.mazzoni.vis.auth.dto.LoginRequest;
import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.UserRole;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.UserRepository;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuickAnalysisIT {

    static final KeyPair KEY_PAIR;

    static {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KEY_PAIR = gen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void jwtProps(DynamicPropertyRegistry registry) {
        Base64.Encoder enc = Base64.getEncoder();
        registry.add("jwt.private-key", () ->
                "-----BEGIN PRIVATE KEY-----\n" + enc.encodeToString(KEY_PAIR.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----");
        registry.add("jwt.public-key", () ->
                "-----BEGIN PUBLIC KEY-----\n" + enc.encodeToString(KEY_PAIR.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----");
    }

    @MockitoBean
    StringRedisTemplate redisTemplate;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UserRepository userRepository;
    @Autowired SecurityRepository securityRepository;
    @Autowired FundamentalSnapshotRepository fundamentalSnapshotRepository;
    @Autowired PriceQuoteRepository priceQuoteRepository;
    @Autowired ValuationResultRepository valuationResultRepository;

    final Map<String, String> tokenStore = new ConcurrentHashMap<>();

    Security aaplSecurity;
    FundamentalSnapshot aaplSnapshot;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        valuationResultRepository.deleteAll();
        priceQuoteRepository.deleteAll();
        fundamentalSnapshotRepository.deleteAll();
        securityRepository.deleteAll();
        userRepository.deleteAll();

        tokenStore.clear();
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doAnswer(inv -> tokenStore.put(inv.getArgument(0), inv.getArgument(1)))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));
        Mockito.when(valueOps.get(anyString()))
                .thenAnswer(inv -> tokenStore.get(inv.getArgument(0)));
        Mockito.when(redisTemplate.delete(anyString()))
                .thenAnswer(inv -> tokenStore.remove(inv.getArgument(0)) != null);

        User admin = new User();
        admin.setEmail("admin@test.com");
        admin.setPasswordHash(passwordEncoder.encode("Password1!"));
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);

        aaplSecurity = new Security();
        aaplSecurity.setSymbol("AAPL");
        aaplSecurity.setCompanyName("Apple Inc.");
        aaplSecurity.setSector("Technology");
        aaplSecurity.setCurrency("USD");
        aaplSecurity = securityRepository.save(aaplSecurity);

        aaplSnapshot = new FundamentalSnapshot();
        aaplSnapshot.setSecurity(aaplSecurity);
        aaplSnapshot.setPeriod(Period.TTM);
        aaplSnapshot.setReportDate(LocalDate.now().minusDays(1));
        aaplSnapshot.setEpsDiluted(new BigDecimal("6.13"));
        aaplSnapshot.setEps(new BigDecimal("6.13"));
        aaplSnapshot.setTotalEquity(new BigDecimal("62146000000"));
        aaplSnapshot.setSharesOutstanding(15550061000L);
        aaplSnapshot.setTotalDebt(new BigDecimal("108040000000"));
        aaplSnapshot.setCash(new BigDecimal("29965000000"));
        aaplSnapshot.setFreeCashFlow(new BigDecimal("111443000000"));
        aaplSnapshot.setRevenue(new BigDecimal("394330000000"));
        aaplSnapshot.setNetIncome(new BigDecimal("96995000000"));
        aaplSnapshot = fundamentalSnapshotRepository.save(aaplSnapshot);

        PriceQuote quote = new PriceQuote();
        quote.setSecurity(aaplSecurity);
        quote.setQuoteDate(LocalDate.now());
        quote.setClose(new BigDecimal("182.50"));
        priceQuoteRepository.save(quote);
    }

    @AfterEach
    void tearDown() {
        valuationResultRepository.deleteAll();
        priceQuoteRepository.deleteAll();
        fundamentalSnapshotRepository.deleteAll();
        securityRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void quickAnalysis_returnsCompositeAndAllFields() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/v1/securities/AAPL/quick-analysis")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.companyName").value("Apple Inc."))
                .andExpect(jsonPath("$.currentPrice").isNotEmpty())
                .andExpect(jsonPath("$.source").value("fmp"))
                .andExpect(jsonPath("$.dataAsOf").isNotEmpty())
                .andExpect(jsonPath("$.disclaimer").value(QuickAnalysisResponse.DISCLAIMER))
                .andExpect(jsonPath("$.valuation.composite").isNotEmpty())
                .andExpect(jsonPath("$.marginOfSafety").isNotEmpty())
                .andExpect(jsonPath("$.recommendation").isNotEmpty());
    }

    @Test
    void quickAnalysis_unknownSymbol_returns404() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/v1/securities/ZZZZ/quick-analysis")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void quickAnalysis_staleSnapshot_returns422() throws Exception {
        aaplSnapshot.setReportDate(LocalDate.now().minusDays(8));
        fundamentalSnapshotRepository.save(aaplSnapshot);

        String token = login();

        mockMvc.perform(get("/api/v1/securities/AAPL/quick-analysis")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString("stale")));
    }

    @Test
    void quickAnalysis_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/securities/AAPL/quick-analysis"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void quickAnalysis_noApplicableModel_returns422() throws Exception {
        // Negative EPS → Graham excluded; null FCF → DCF excluded → ValuationNotApplicableException
        aaplSnapshot.setEpsDiluted(new BigDecimal("-1.00"));
        aaplSnapshot.setEps(new BigDecimal("-1.00"));
        aaplSnapshot.setFreeCashFlow(null);
        fundamentalSnapshotRepository.save(aaplSnapshot);

        String token = login();

        mockMvc.perform(get("/api/v1/securities/AAPL/quick-analysis")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("admin@test.com", "Password1!"))))
                .andReturn();
        return (String) objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class).get("accessToken");
    }
}
