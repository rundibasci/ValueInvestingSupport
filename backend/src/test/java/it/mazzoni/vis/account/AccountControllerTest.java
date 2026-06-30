package it.mazzoni.vis.account;

import it.mazzoni.vis.auth.JwtService;
import it.mazzoni.vis.domain.entity.OAuthIdentity;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.UserRole;
import it.mazzoni.vis.domain.repository.OAuthIdentityRepository;
import it.mazzoni.vis.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountControllerTest {

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

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    OAuthIdentityRepository oauthIdentityRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        oauthIdentityRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void getAccount_returnsLinkedGoogleStatus() throws Exception {
        User user = saveUser("linked@example.com", "Password1!");
        saveGoogleIdentity(user, "google-sub-1");

        mockMvc.perform(get("/api/v1/account").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("linked@example.com"))
                .andExpect(jsonPath("$.role").value("INVESTOR"))
                .andExpect(jsonPath("$.googleLinked").value(true))
                .andExpect(jsonPath("$.localPasswordAvailable").value(true));
    }

    @Test
    void unlinkGoogle_withLocalPasswordDeletesIdentity() throws Exception {
        User user = saveUser("unlink@example.com", "Password1!");
        saveGoogleIdentity(user, "google-sub-2");

        mockMvc.perform(delete("/api/v1/account/oauth/google").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.googleLinked").value(false))
                .andExpect(jsonPath("$.localPasswordAvailable").value(true));
    }

    @Test
    void unlinkGoogle_withoutLocalPasswordReturnsUnauthorized() throws Exception {
        User user = saveUser("google-only@example.com", null);
        saveGoogleIdentity(user, "google-sub-3");

        mockMvc.perform(delete("/api/v1/account/oauth/google").header("Authorization", bearer(user)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Cannot unlink Google because it is the only sign-in method"));
    }

    @Test
    void getAccount_withoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/account"))
                .andExpect(status().isUnauthorized());
    }

    private User saveUser(String email, String password) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(password == null ? null : passwordEncoder.encode(password));
        user.setRole(UserRole.INVESTOR);
        return userRepository.save(user);
    }

    private void saveGoogleIdentity(User user, String subject) {
        OAuthIdentity identity = new OAuthIdentity();
        identity.setUser(user);
        identity.setProvider("GOOGLE");
        identity.setProviderSubject(subject);
        identity.setProviderEmail(user.getEmail());
        oauthIdentityRepository.save(identity);
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issueAccessToken(user.getEmail(), user.getRole().name());
    }
}
