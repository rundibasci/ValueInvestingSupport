package it.mazzoni.vis.auth;

import it.mazzoni.vis.auth.dto.LoginRequest;
import it.mazzoni.vis.auth.dto.LoginResponse;
import it.mazzoni.vis.auth.dto.RefreshResponse;
import it.mazzoni.vis.domain.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
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

    private static final String REFRESH_COOKIE = "vis_refresh";

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

        String refreshToken = jwtService.issueRefreshToken(email);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken, false).toString())
                .body(new LoginResponse(jwtService.issueAccessToken(email, role), 900));
    }

    @PostMapping("/refresh")
    ResponseEntity<RefreshResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        String email = jwtService.validateRefreshToken(refreshToken)
                .orElseThrow(() -> new AuthException("Invalid or expired refresh token"));

        String role = userRepository.findByEmail(email)
                .filter(it.mazzoni.vis.domain.entity.User::isActive)
                .map(u -> u.getRole().name())
                .orElseThrow(() -> new AuthException("Invalid or expired refresh token"));

        return ResponseEntity.ok(new RefreshResponse(
                jwtService.issueAccessToken(email, role),
                900
        ));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken != null) {
            jwtService.revokeRefreshToken(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", true).toString())
                .build();
    }

    private ResponseCookie refreshCookie(String value, boolean clear) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/auth")
                .maxAge(clear ? 0 : 7 * 24 * 60 * 60)
                .build();
    }
}
