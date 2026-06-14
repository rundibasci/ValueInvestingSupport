package it.mazzoni.vis.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jwt")
public record JwtProperties(
        String privateKey,
        String publicKey,
        long accessTokenExpirySeconds,
        long refreshTokenExpirySeconds
) {}
