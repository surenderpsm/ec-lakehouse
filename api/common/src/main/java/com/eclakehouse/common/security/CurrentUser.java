package com.eclakehouse.common.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.eclakehouse.common.web.ApiException;

/** Static helpers to read the caller's identity from the validated JWT. */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** The caller's user id (JWT {@code sub}). Throws 401 if the request is anonymous. */
    public static UUID id() {
        return jwtAuth().map(a -> UUID.fromString(a.getName()))
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
    }

    public static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    public static boolean isAdmin() {
        return hasRole(Roles.ADMIN);
    }

    /** The raw bearer token, used to call other services on the caller's behalf. */
    public static Optional<String> token() {
        return jwtAuth().map(a -> a.getToken().getTokenValue());
    }

    private static Optional<JwtAuthenticationToken> jwtAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken jwt ? Optional.of(jwt) : Optional.empty();
    }
}
