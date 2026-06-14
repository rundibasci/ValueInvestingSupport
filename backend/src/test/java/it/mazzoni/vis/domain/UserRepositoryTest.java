package it.mazzoni.vis.domain;

import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.UserRole;
import it.mazzoni.vis.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository repository;

    @Test
    void savesAndFindsUserByEmail() {
        User user = new User();
        user.setEmail("alice@example.com");
        user.setPasswordHash("$2a$10$hashed");
        user.setRole(UserRole.INVESTOR);
        repository.save(user);

        Optional<User> found = repository.findByEmail("alice@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getRole()).isEqualTo(UserRole.INVESTOR);
        assertThat(found.get().isActive()).isTrue();
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void rejectsUserWithNullPasswordHash() {
        User user = new User();
        user.setEmail("bad@example.com");
        user.setRole(UserRole.ADVISOR);
        // passwordHash intentionally left null

        assertThatThrownBy(() -> repository.saveAndFlush(user))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateEmail() {
        User a = new User();
        a.setEmail("dup@example.com");
        a.setPasswordHash("hash1");
        a.setRole(UserRole.INVESTOR);
        repository.saveAndFlush(a);

        User b = new User();
        b.setEmail("dup@example.com");
        b.setPasswordHash("hash2");
        b.setRole(UserRole.INVESTOR);

        assertThatThrownBy(() -> repository.saveAndFlush(b))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByEmailWorks() {
        User user = new User();
        user.setEmail("bob@example.com");
        user.setPasswordHash("hash");
        user.setRole(UserRole.ADMIN);
        repository.save(user);

        assertThat(repository.existsByEmail("bob@example.com")).isTrue();
        assertThat(repository.existsByEmail("nobody@example.com")).isFalse();
    }
}
