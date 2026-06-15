package it.mazzoni.vis.localstack;

import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.UserRole;
import it.mazzoni.vis.domain.repository.UserRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("localstack")
public class DemoDataSeeder {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (userRepository.existsByEmail("admin@localstack.local")) {
            return;
        }
        User admin = new User();
        admin.setEmail("admin@localstack.local");
        admin.setPasswordHash(passwordEncoder.encode("admin"));
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);
    }
}
