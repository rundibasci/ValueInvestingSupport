package it.mazzoni.vis.auth.oauth;

import it.mazzoni.vis.auth.JwtService;
import it.mazzoni.vis.domain.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Profile("!demo")
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final String HANDOFF_PREFIX = "oauth_handoff:";
    private static final Duration HANDOFF_TTL = Duration.ofSeconds(60);
    private static final String REFRESH_COOKIE = "vis_refresh";

    private final OAuthAccountResolver accountResolver;
    private final JwtService jwtService;
    private final StringRedisTemplate redis;
    private final String frontendCallbackUrl;
    private final ConcurrentMap<String, String> localHandoffStore = new ConcurrentHashMap<>();

    public OAuthLoginSuccessHandler(OAuthAccountResolver accountResolver,
                                    JwtService jwtService,
                                    StringRedisTemplate redis,
                                    @Value("${google.oauth2.frontend-callback:http://localhost:5173/auth/oauth2/callback}") String frontendCallbackUrl) {
        this.accountResolver = accountResolver;
        this.jwtService = jwtService;
        this.redis = redis;
        this.frontendCallbackUrl = frontendCallbackUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();

        Boolean emailVerified = oidcUser.getEmailVerified();
        if (emailVerified == null || !emailVerified) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Email not verified by Google");
            return;
        }

        String sub = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        String name = oidcUser.getFullName();

        User user = accountResolver.resolve(sub, email, name);

        String accessToken = jwtService.issueAccessToken(user.getEmail(), user.getRole().name());
        String refreshToken = jwtService.issueRefreshToken(user.getEmail());

        String handoffCode = UUID.randomUUID().toString();
        storeHandoffCode(handoffCode, accessToken);

        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/auth")
                .maxAge(7 * 24 * 60 * 60)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        response.sendRedirect(frontendCallbackUrl + "?code=" + handoffCode);
    }

    private void storeHandoffCode(String code, String accessToken) {
        try {
            redis.opsForValue().set(HANDOFF_PREFIX + code, accessToken, HANDOFF_TTL);
        } catch (RedisConnectionFailureException ex) {
            localHandoffStore.put(code, accessToken);
        }
    }

    String consumeHandoffCode(String code) {
        try {
            String token = redis.opsForValue().get(HANDOFF_PREFIX + code);
            if (token != null) {
                redis.delete(HANDOFF_PREFIX + code);
            }
            return token;
        } catch (RedisConnectionFailureException ex) {
            return localHandoffStore.remove(code);
        }
    }
}
