package com.eclakehouse.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtRolesConverterTest {

    @Test
    void mapsRolesClaimToAuthorities() {
        String sub = UUID.randomUUID().toString();
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject(sub)
                .claim("roles", List.of("CUSTOMER", "VENDOR"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

        var auth = new JwtRolesConverter().convert(jwt);

        assertThat(auth.getName()).isEqualTo(sub);
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_VENDOR");
    }

    @Test
    void noRolesClaimMeansNoAuthorities() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject(UUID.randomUUID().toString()).build();
        assertThat(new JwtRolesConverter().convert(jwt).getAuthorities()).isEmpty();
    }
}
