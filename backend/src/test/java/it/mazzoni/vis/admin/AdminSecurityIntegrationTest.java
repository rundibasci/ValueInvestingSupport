package it.mazzoni.vis.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.mazzoni.vis.auth.dto.LoginRequest;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.UserRole;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.UserRepository;
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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSecurityIntegrationTest {

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

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SecurityRepository securityRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    final Map<String, String> tokenStore = new ConcurrentHashMap<>();
    String adminToken;
    String investorToken;
    Security unbound;
    Security alreadyBound;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        tokenStore.clear();
        securityRepository.deleteAll();
        userRepository.deleteAll();

        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doAnswer(inv -> tokenStore.put(inv.getArgument(0), inv.getArgument(1)))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));
        Mockito.when(valueOps.get(anyString()))
                .thenAnswer(inv -> tokenStore.get(inv.getArgument(0)));
        Mockito.when(redisTemplate.delete(anyString()))
                .thenAnswer(inv -> tokenStore.remove(inv.getArgument(0)) != null);

        User admin = new User();
        admin.setEmail("admin@example.com");
        admin.setPasswordHash(passwordEncoder.encode("Admin1234!"));
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);

        User investor = new User();
        investor.setEmail("investor@example.com");
        investor.setPasswordHash(passwordEncoder.encode("Investor1!"));
        investor.setRole(UserRole.INVESTOR);
        userRepository.save(investor);

        unbound = new Security();
        unbound.setSymbol("UNBOUND");
        unbound.setCompanyName("Unbound Co");
        unbound = securityRepository.save(unbound);

        alreadyBound = new Security();
        alreadyBound.setSymbol("BOUND");
        alreadyBound.setCompanyName("Bound Co");
        alreadyBound.setIsin("US0000000001");
        alreadyBound = securityRepository.save(alreadyBound);

        adminToken = login("admin@example.com", "Admin1234!");
        investorToken = login("investor@example.com", "Investor1!");
    }

    @Test
    void setIsin_asAdmin_bindsNewIsin() throws Exception {
        mockMvc.perform(put("/api/v1/admin/securities/{id}/isin", unbound.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isin\":\"US1234567890\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isin").value("US1234567890"))
                .andExpect(jsonPath("$.symbol").value("UNBOUND"));

        assertThat(securityRepository.findById(unbound.getId()).orElseThrow().getIsin())
                .isEqualTo("US1234567890");
    }

    @Test
    void setIsin_asInvestor_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/admin/securities/{id}/isin", unbound.getId())
                        .header("Authorization", "Bearer " + investorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isin\":\"US1234567890\"}"))
                .andExpect(status().isForbidden());

        assertThat(securityRepository.findById(unbound.getId()).orElseThrow().getIsin()).isNull();
    }

    @Test
    void setIsin_withoutToken_returns401() throws Exception {
        mockMvc.perform(put("/api/v1/admin/securities/{id}/isin", unbound.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isin\":\"US1234567890\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void setIsin_conflictingIsinAlreadyOnAnotherSecurity_returns409() throws Exception {
        mockMvc.perform(put("/api/v1/admin/securities/{id}/isin", unbound.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isin\":\"US0000000001\"}"))
                .andExpect(status().isConflict());

        assertThat(securityRepository.findById(unbound.getId()).orElseThrow().getIsin()).isNull();
    }

    @Test
    void setIsin_targetAlreadyHasDifferentIsin_returns409() throws Exception {
        mockMvc.perform(put("/api/v1/admin/securities/{id}/isin", alreadyBound.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isin\":\"US9999999999\"}"))
                .andExpect(status().isConflict());

        assertThat(securityRepository.findById(alreadyBound.getId()).orElseThrow().getIsin())
                .isEqualTo("US0000000001");
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andReturn();
        return (String) objectMapper.readValue(result.getResponse().getContentAsString(), Map.class)
                .get("accessToken");
    }
}
