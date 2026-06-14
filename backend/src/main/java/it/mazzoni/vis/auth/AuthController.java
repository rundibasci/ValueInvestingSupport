package it.mazzoni.vis.auth;

import it.mazzoni.vis.auth.dto.LoginRequest;
import it.mazzoni.vis.auth.dto.LoginResponse;
import it.mazzoni.vis.auth.dto.LogoutRequest;
import it.mazzoni.vis.auth.dto.RefreshRequest;
import it.mazzoni.vis.auth.dto.RefreshResponse;
import it.mazzoni.vis.domain.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Profile("!demo")
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AuthController(AuthenticationManager authManager,
                          JwtService jwtService,
                          UserRepository userRepository) {
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication auth;
        try {
            auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (BadCredentialsException e) {
            throw new AuthException("Invalid credentials");
        }

        String email = auth.getName();
        String role = auth.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replace("ROLE_", ""))
                .orElse("INVESTOR");

        return ResponseEntity.ok(new LoginResponse(
                jwtService.issueAccessToken(email, role),
                jwtService.issueRefreshToken(email),
                900
        ));
    }

    @PostMapping("/refresh")
    ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        String email = jwtService.validateRefreshToken(request.refreshToken())
                .orElseThrow(() -> new AuthException("Invalid or expired refresh token"));

        String role = userRepository.findByEmail(email)
                .map(u -> u.getRole().name())
                .orElseThrow(() -> new AuthException("User not found"));

        return ResponseEntity.ok(new RefreshResponse(
                jwtService.issueAccessToken(email, role),
                900
        ));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        jwtService.revokeRefreshToken(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
