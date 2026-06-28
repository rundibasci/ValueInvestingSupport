package it.mazzoni.vis.auth.oauth;

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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuthAccountLinkingIntegrationTest {

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
    UserRepository userRepository;

    @Autowired
    OAuthIdentityRepository oauthRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    OAuthAccountResolver resolver;

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
    }

    @Test
    void newGoogleUser_createsUserAndOAuthIdentity() {
        User user = resolver.resolve("sub-new-1", "brand-new@example.com", "Brand New");

        assertEquals(1, userRepository.count());
        assertEquals(1, oauthRepository.count());
        assertEquals(UserRole.INVESTOR, user.getRole());
        assertNull(user.getPasswordHash());

        var identity = oauthRepository.findByProviderAndProviderSubject("GOOGLE", "sub-new-1");
        assertTrue(identity.isPresent());
        assertEquals(user.getId(), identity.get().getUser().getId());
    }

    @Test
    void existingPasswordUser_linksGoogleIdentity() {
        User existing = new User();
        existing.setEmail("existing@example.com");
        existing.setPasswordHash(passwordEncoder.encode("Password1!"));
        existing.setRole(UserRole.INVESTOR);
        userRepository.save(existing);

        User resolved = resolver.resolve("sub-existing-1", "existing@example.com", "Existing");

        assertEquals(existing.getId(), resolved.getId());
        assertEquals(1, userRepository.count());
        assertEquals(1, oauthRepository.count());
        assertNotNull(resolved.getPasswordHash());
    }

    @Test
    void repeatLogin_noDuplicateRecords() {
        resolver.resolve("sub-repeat-1", "repeat@example.com", "Repeat");
        resolver.resolve("sub-repeat-1", "repeat@example.com", "Repeat");

        assertEquals(1, userRepository.count());
        assertEquals(1, oauthRepository.count());
    }

    @Test
    void existingAdmin_rolePreservedAfterLinking() {
        User admin = new User();
        admin.setEmail("admin@example.com");
        admin.setPasswordHash(passwordEncoder.encode("AdminPass1!"));
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);

        User resolved = resolver.resolve("sub-admin-1", "admin@example.com", "Admin");

        assertEquals(UserRole.ADMIN, resolved.getRole());
        assertEquals(admin.getId(), resolved.getId());
    }
}
