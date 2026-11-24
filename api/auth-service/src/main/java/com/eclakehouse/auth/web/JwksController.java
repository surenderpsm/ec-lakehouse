package com.eclakehouse.auth.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;

/** Public signing key, used by every other service to verify access tokens. */
@RestController
public class JwksController {

    private final JWKSet publicKeys;

    public JwksController(RSAKey rsaKey) {
        this.publicKeys = new JWKSet(rsaKey.toPublicJWK());
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return publicKeys.toJSONObject();
    }
}
