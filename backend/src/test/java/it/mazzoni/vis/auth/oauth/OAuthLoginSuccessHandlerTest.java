package it.mazzoni.vis.auth.oauth;

import it.mazzoni.vis.auth.JwtService;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.UserRole;
import it.mazzoni.vis.domain.repository.OAuthIdentityRepository;
import it.mazzoni.vis.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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

@SpringBootTest(properties = "google.oauth2.frontend-callback=http://localhost:5173/auth/oauth2/callback")
@ActiveProfiles("test")
class OAuthLoginSuccessHandlerTest {

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
    OAuthLoginSuccessHandler successHandler;

    @Autowired
    JwtService jwtService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    OAuthIdentityRepository oauthRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

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
    void verifiedGoogleCallbackIssuesPlatformSessionAndRedirectHandoff() throws Exception {
        OidcUser oidcUser = oidcUser("google-sub-success", "oauth-user@example.com", true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                new TestingAuthenticationToken(oidcUser, null));

        assertEquals(302, response.getStatus());
        assertTrue(response.getRedirectedUrl().startsWith("http://localhost:5173/auth/oauth2/callback?code="));
        assertTrue(response.getHeader(HttpHeaders.SET_COOKIE).contains("vis_refresh="));
        assertTrue(response.getHeader(HttpHeaders.SET_COOKIE).contains("HttpOnly"));
        assertEquals(1, userRepository.count());
        assertEquals(1, oauthRepository.count());

        String code = response.getRedirectedUrl().substring(response.getRedirectedUrl().indexOf("code=") + 5);
        String accessToken = store.get("oauth_handoff:" + code);
        assertNotNull(accessToken);
        assertEquals("oauth-user@example.com", jwtService.validateAccessToken(accessToken).getSubject());
    }

    @Test
    void unverifiedGoogleEmailIsRejectedWithoutAccountCreation() throws Exception {
        OidcUser oidcUser = oidcUser("google-sub-unverified", "unverified@example.com", false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                new TestingAuthenticationToken(oidcUser, null));

        assertEquals(403, response.getStatus());
        assertEquals(0, userRepository.count());
        assertEquals(0, oauthRepository.count());
        assertTrue(store.isEmpty());
    }

    @Test
    void existingElevatedUserKeepsRoleAfterGoogleCallback() throws Exception {
        User admin = new User();
        admin.setEmail("admin@example.com");
        admin.setPasswordHash(passwordEncoder.encode("AdminPass1!"));
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);

        OidcUser oidcUser = oidcUser("google-sub-admin-callback", "admin@example.com", true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                new TestingAuthenticationToken(oidcUser, null));

        User resolved = userRepository.findByEmail("admin@example.com").orElseThrow();
        assertEquals(UserRole.ADMIN, resolved.getRole());
        assertEquals(admin.getId(), resolved.getId());
    }

    @Test
    void inactiveExistingUserReceivesNoOAuthCredentials() throws Exception {
        User user = new User();
        user.setEmail("inactive@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password1!"));
        user.setRole(UserRole.INVESTOR);
        user.setActive(false);
        userRepository.save(user);

        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                new TestingAuthenticationToken(oidcUser("inactive-sub", "inactive@example.com", true), null));

        assertEquals(403, response.getStatus());
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
        assertTrue(store.isEmpty());
    }

    private OidcUser oidcUser(String subject, String email, boolean verified) {
        OidcUser oidcUser = Mockito.mock(OidcUser.class);
        Mockito.when(oidcUser.getSubject()).thenReturn(subject);
        Mockito.when(oidcUser.getEmail()).thenReturn(email);
        Mockito.when(oidcUser.getEmailVerified()).thenReturn(verified);
        Mockito.when(oidcUser.getFullName()).thenReturn("OAuth User");
        return oidcUser;
    }
}
