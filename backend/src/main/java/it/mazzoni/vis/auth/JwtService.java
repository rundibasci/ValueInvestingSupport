package it.mazzoni.vis.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
@Profile("!demo")
public class JwtService {

    private static final String REFRESH_PREFIX = "refresh:";

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final long accessTokenExpiryMs;
    private final Duration refreshTokenTtl;
    private final StringRedisTemplate redis;

    public JwtService(JwtProperties props, StringRedisTemplate redis) {
        this.privateKey = parsePrivateKey(props.privateKey());
        this.publicKey = parsePublicKey(props.publicKey());
        this.accessTokenExpiryMs = props.accessTokenExpirySeconds() * 1000;
        this.refreshTokenTtl = Duration.ofSeconds(props.refreshTokenExpirySeconds());
        this.redis = redis;
    }

    public String issueAccessToken(String email, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiryMs))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public String issueRefreshToken(String email) {
        String tokenId = UUID.randomUUID().toString();
        redis.opsForValue().set(REFRESH_PREFIX + tokenId, email, refreshTokenTtl);
        return tokenId;
    }

    public Claims validateAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Optional<String> validateRefreshToken(String tokenId) {
        return Optional.ofNullable(redis.opsForValue().get(REFRESH_PREFIX + tokenId));
    }

    public void revokeRefreshToken(String tokenId) {
        redis.delete(REFRESH_PREFIX + tokenId);
    }

    private static PrivateKey parsePrivateKey(String pem) {
        try {
            String stripped = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(stripped);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse JWT private key", e);
        }
    }

    private static PublicKey parsePublicKey(String pem) {
        try {
            String stripped = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(stripped);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse JWT public key", e);
        }
    }
}
