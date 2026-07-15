package it.mazzoni.vis.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.mazzoni.vis.admin.dto.CreateUserRequest;
import it.mazzoni.vis.auth.dto.LoginRequest;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.UserRole;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserIntegrationTest {

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
    PasswordEncoder passwordEncoder;

    final Map<String, String> tokenStore = new ConcurrentHashMap<>();
    String adminToken;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        tokenStore.clear();
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

        adminToken = login("admin@example.com", "Admin1234!");
    }

    @Test
    void createUser_asAdmin_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateUserRequest("new@example.com", "Password1!", "INVESTOR"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.role").value("INVESTOR"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void createUser_withDuplicateEmail_returns409() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateUserRequest("admin@example.com", "Password1!", "INVESTOR"))))
                .andExpect(status().isConflict());
    }

    @Test
    void createUser_asInvestor_returns403() throws Exception {
        String investorToken = login("investor@example.com", "Investor1!");

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + investorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateUserRequest("new2@example.com", "Password1!", "INVESTOR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUser_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateUserRequest("new3@example.com", "Password1!", "INVESTOR"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listAndLifecycle_asAdmin_preservesUserAndBlocksLoginAndRefresh() throws Exception {
        User investor = userRepository.findByEmail("investor@example.com").orElseThrow();
        mockMvc.perform(get("/api/v1/admin/users?page=0&size=20")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[?(@.email == 'investor@example.com')].active").value(true));

        mockMvc.perform(patch("/api/v1/admin/users/{id}/active", investor.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("investor@example.com", "Investor1!"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/admin/users/{id}/active", investor.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        org.junit.jupiter.api.Assertions.assertEquals(UserRole.INVESTOR, userRepository.findById(investor.getId()).orElseThrow().getRole());
    }

    @Test
    void lifecycle_rejectsSelfDisableAndFinalAdminWithStableCodes() throws Exception {
        User admin = userRepository.findByEmail("admin@example.com").orElseThrow();
        mockMvc.perform(patch("/api/v1/admin/users/{id}/active", admin.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_DISABLE_NOT_ALLOWED"));

        User secondAdmin = new User();
        secondAdmin.setEmail("second-admin@example.com");
        secondAdmin.setPasswordHash(passwordEncoder.encode("Admin1234!"));
        secondAdmin.setRole(UserRole.ADMIN);
        secondAdmin = userRepository.save(secondAdmin);
        String secondToken = login("second-admin@example.com", "Admin1234!");
        mockMvc.perform(patch("/api/v1/admin/users/{id}/active", secondAdmin.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/admin/users/{id}/active", admin.getId())
                        .header("Authorization", "Bearer " + secondToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ACTIVE_ADMIN"));
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
