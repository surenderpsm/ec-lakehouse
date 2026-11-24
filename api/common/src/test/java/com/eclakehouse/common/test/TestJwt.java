package com.eclakehouse.common.test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** MockMvc helper: act as a signed-in user with the given roles, without a real token. */
public final class TestJwt {

    private TestJwt() {
    }

    public static RequestPostProcessor user(UUID id, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        return jwt().jwt(j -> j.subject(id.toString()).claim("roles", List.of(roles))).authorities(authorities);
    }
}
