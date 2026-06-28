package it.mazzoni.vis.auth.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.UserRole;
import it.mazzoni.vis.domain.repository.OAuthIdentityRepository;
import it.mazzoni.vis.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuthTokenExchangeTest {

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
    static void props(DynamicPropertyRegistry registry) {
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
    OAuthIdentityRepository oauthRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    OAuthLoginSuccessHandler successHandler;

    final Map<String, String> store = new ConcurrentHashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        store.clear();
        oauthRepository.deleteAll();
        userRepository.deleteAll();

        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doAnswer(inv -> store.put(inv.getArgument(0), inv.getArgument(1)))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));
        Mockito.when(valueOps.get(anyString()))
                .thenAnswer(inv -> store.get(inv.getArgument(0)));
        Mockito.when(redisTemplate.delete(anyString()))
                .thenAnswer(inv -> store.remove(inv.getArgument(0)) != null);

        User user = new User();
        user.setEmail("oauth@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password1!"));
        user.setRole(UserRole.INVESTOR);
        userRepository.save(user);
    }

    @Test
    void exchangeValidHandoffCode_returnsAccessToken() throws Exception {
        String code = UUID.randomUUID().toString();
        store.put("oauth_handoff:" + code, "fake-jwt-access-token");

        mockMvc.perform(get("/auth/oauth2/token").param("code", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("fake-jwt-access-token"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void exchangeSameCodeTwice_secondReturns401() throws Exception {
        String code = UUID.randomUUID().toString();
        store.put("oauth_handoff:" + code, "fake-jwt-access-token");

        mockMvc.perform(get("/auth/oauth2/token").param("code", code))
                .andExpect(status().isOk());

        mockMvc.perform(get("/auth/oauth2/token").param("code", code))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exchangeUnknownCode_returns401() throws Exception {
        mockMvc.perform(get("/auth/oauth2/token").param("code", "nonexistent"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withoutToken_stillReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isUnauthorized());
    }
}
