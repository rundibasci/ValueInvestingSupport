package it.mazzoni.vis.admin;

import it.mazzoni.vis.admin.dto.CreateUserRequest;
import it.mazzoni.vis.admin.dto.UserResponse;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.UserRole;
import it.mazzoni.vis.domain.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class AdminUserLifecycleService {
    static final int MAX_PAGE_SIZE = 100;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AdminUserLifecycleService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(int page, int size) {
        if (page < 0 || size < 1) throw new UserLifecycleException("INVALID_PAGINATION", "Page must be non-negative and size must be positive", HttpStatus.BAD_REQUEST);
        int boundedSize = Math.min(size, MAX_PAGE_SIZE);
        return users.findAll(PageRequest.of(page, boundedSize, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))))
                .map(this::response);
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) conflict("EMAIL_ALREADY_REGISTERED", "Email already registered");
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        try {
            user.setRole(UserRole.valueOf(request.role().trim().toUpperCase(Locale.ROOT)));
            return response(users.saveAndFlush(user));
        } catch (IllegalArgumentException exception) {
            throw new UserLifecycleException("INVALID_ROLE", "Role is invalid", HttpStatus.BAD_REQUEST);
        } catch (DataIntegrityViolationException exception) {
            conflict("EMAIL_ALREADY_REGISTERED", "Email already registered");
            throw exception;
        }
    }

    @Transactional
    public UserResponse setActive(String actorEmail, UUID targetId, boolean active) {
        User target = users.findById(targetId).orElseThrow(() -> new UserLifecycleException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
        if (target.isActive() == active) return response(target);
        if (!active && normalize(actorEmail).equals(normalize(target.getEmail()))) {
            conflict("SELF_DISABLE_NOT_ALLOWED", "You cannot disable your own account");
        }
        if (!active && target.getRole() == UserRole.ADMIN) {
            var activeAdmins = users.findActiveByRoleForUpdate(UserRole.ADMIN);
            if (activeAdmins.size() <= 1) conflict("LAST_ACTIVE_ADMIN", "The final active ADMIN cannot be disabled");
        }
        target.setActive(active);
        return response(users.save(target));
    }

    private UserResponse response(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole().name(), user.isActive(), user.getCreatedAt());
    }

    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    private void conflict(String code, String message) {
        throw new UserLifecycleException(code, message, HttpStatus.CONFLICT);
    }
}
