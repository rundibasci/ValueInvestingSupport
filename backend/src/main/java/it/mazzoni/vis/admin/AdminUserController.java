package it.mazzoni.vis.admin;

import it.mazzoni.vis.admin.dto.CreateUserRequest;
import it.mazzoni.vis.admin.dto.SetUserActiveRequest;
import it.mazzoni.vis.admin.dto.UserResponse;
import it.mazzoni.vis.admin.dto.UserPageResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@Profile("!demo")
public class AdminUserController {
    private final AdminUserLifecycleService service;

    public AdminUserController(AdminUserLifecycleService service) { this.service = service; }

    @GetMapping
    UserPageResponse list(@RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return UserPageResponse.from(service.list(page, size));
    }

    @PostMapping
    ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PatchMapping("/{id}/active")
    UserResponse setActive(Authentication authentication, @PathVariable UUID id,
                           @Valid @RequestBody SetUserActiveRequest request) {
        return service.setActive(authentication.getName(), id, request.active());
    }
}
