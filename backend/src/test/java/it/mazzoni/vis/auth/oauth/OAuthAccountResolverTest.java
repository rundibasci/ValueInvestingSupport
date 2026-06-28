package it.mazzoni.vis.auth.oauth;

import it.mazzoni.vis.domain.entity.OAuthIdentity;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.UserRole;
import it.mazzoni.vis.domain.repository.OAuthIdentityRepository;
import it.mazzoni.vis.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class OAuthAccountResolverTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    OAuthIdentityRepository oauthRepository;

    OAuthAccountResolver resolver;

    @BeforeEach
    void setUp() {
        oauthRepository.deleteAll();
        userRepository.deleteAll();
        resolver = new OAuthAccountResolver(oauthRepository, userRepository);
    }

    @Test
    void newGoogleUser_createsInvestorAndOAuthIdentity() {
        User user = resolver.resolve("google-sub-123", "new@example.com", "New User");

        assertNotNull(user.getId());
        assertEquals("new@example.com", user.getEmail());
        assertEquals(UserRole.INVESTOR, user.getRole());
        assertNull(user.getPasswordHash());

        var identity = oauthRepository.findByProviderAndProviderSubject("GOOGLE", "google-sub-123");
        assertTrue(identity.isPresent());
        assertEquals(user.getId(), identity.get().getUser().getId());
        assertEquals("new@example.com", identity.get().getProviderEmail());
    }

    @Test
    void existingPasswordUser_linksWithoutDuplicate() {
        User existing = new User();
        existing.setEmail("existing@example.com");
        existing.setPasswordHash("$2a$10$fakehash");
        existing.setRole(UserRole.INVESTOR);
        userRepository.save(existing);

        User resolved = resolver.resolve("google-sub-456", "existing@example.com", "Existing User");

        assertEquals(existing.getId(), resolved.getId());
        assertEquals("$2a$10$fakehash", resolved.getPasswordHash());
        assertEquals(1, userRepository.count());

        var identity = oauthRepository.findByProviderAndProviderSubject("GOOGLE", "google-sub-456");
        assertTrue(identity.isPresent());
    }

    @Test
    void repeatGoogleLogin_returnsSameUser() {
        User first = resolver.resolve("google-sub-789", "repeat@example.com", "Repeat User");
        User second = resolver.resolve("google-sub-789", "repeat@example.com", "Repeat User");

        assertEquals(first.getId(), second.getId());
        assertEquals(1, userRepository.count());
        assertEquals(1, oauthRepository.count());
    }

    @Test
    void existingAdminUser_linksWithoutChangingRole() {
        User admin = new User();
        admin.setEmail("admin@example.com");
        admin.setPasswordHash("$2a$10$adminhash");
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);

        User resolved = resolver.resolve("google-sub-admin", "admin@example.com", "Admin User");

        assertEquals(admin.getId(), resolved.getId());
        assertEquals(UserRole.ADMIN, resolved.getRole());
    }

    @Test
    void existingAdvisorUser_linksWithoutChangingRole() {
        User advisor = new User();
        advisor.setEmail("advisor@example.com");
        advisor.setPasswordHash("$2a$10$advisorhash");
        advisor.setRole(UserRole.ADVISOR);
        userRepository.save(advisor);

        User resolved = resolver.resolve("google-sub-advisor", "advisor@example.com", "Advisor User");

        assertEquals(advisor.getId(), resolved.getId());
        assertEquals(UserRole.ADVISOR, resolved.getRole());
    }

    @Test
    void nullEmail_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("google-sub-null", null, "No Email"));
    }

    @Test
    void blankEmail_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("google-sub-blank", "  ", "Blank Email"));
    }
}
