package com.eclakehouse.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param jwt            token settings
 * @param bootstrapAdmin optional first admin account, created on startup if it does not exist yet
 */
@ConfigurationProperties("ec.auth")
public record AuthProperties(Jwt jwt, BootstrapAdmin bootstrapAdmin) {

    /**
     * @param issuer          value of the {@code iss} claim
     * @param accessTokenTtl  lifetime of access tokens
     * @param refreshTokenTtl lifetime of refresh tokens
     * @param privateKey      PKCS#8 PEM private key; if blank a key pair is generated at startup
     * @param publicKey       X.509 PEM public key matching {@code privateKey}
     */
    public record Jwt(@DefaultValue("http://auth-service") String issuer,
            @DefaultValue("15m") Duration accessTokenTtl,
            @DefaultValue("7d") Duration refreshTokenTtl,
            String privateKey,
            String publicKey) {
    }

    public record BootstrapAdmin(String email, String password) {
    }
}
