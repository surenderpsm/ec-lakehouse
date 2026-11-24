package com.eclakehouse.auth.config;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

/**
 * RSA signing key for access tokens. Other services verify tokens with the public half,
 * served at {@code /.well-known/jwks.json}.
 */
@Configuration
public class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

    @Bean
    RSAKey rsaKey(AuthProperties props) throws NoSuchAlgorithmException {
        AuthProperties.Jwt jwt = props.jwt();
        RSAPublicKey publicKey;
        RSAPrivateKey privateKey;
        if (StringUtils.hasText(jwt.privateKey()) && StringUtils.hasText(jwt.publicKey())) {
            privateKey = RsaKeyConverters.pkcs8().convert(stream(jwt.privateKey()));
            publicKey = RsaKeyConverters.x509().convert(stream(jwt.publicKey()));
        } else {
            log.warn("No JWT key pair configured (ec.auth.jwt.private-key / public-key). Generating a temporary one: "
                    + "tokens will stop working when this service restarts.");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            publicKey = (RSAPublicKey) pair.getPublic();
            privateKey = (RSAPrivateKey) pair.getPrivate();
        }
        return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(UUID.randomUUID().toString()).build();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey rsaKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
    }

    /** auth-service validates its own tokens directly with the public key. */
    @Bean
    JwtDecoder jwtDecoder(RSAKey rsaKey) throws Exception {
        return NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
    }

    private static ByteArrayInputStream stream(String pem) {
        return new ByteArrayInputStream(pem.replace("\\n", "\n").getBytes(StandardCharsets.UTF_8));
    }
}
