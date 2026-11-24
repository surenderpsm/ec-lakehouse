package com.eclakehouse.auth.user;

import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eclakehouse.auth.token.RefreshToken;
import com.eclakehouse.auth.token.RefreshTokenRepository;
import com.eclakehouse.auth.token.TokenService;
import com.eclakehouse.auth.web.TokenResponse;
import com.eclakehouse.common.web.ApiException;

@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final TokenService tokens;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, TokenService tokens,
            PasswordEncoder passwordEncoder) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(String email, String password, String fullName) {
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        return users.save(new User(email, passwordEncoder.encode(password), fullName, EnumSet.of(Role.CUSTOMER)));
    }

    @Transactional
    public TokenResponse login(String email, String password) {
        User user = users.findByEmailIgnoreCase(email)
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        return tokens.issue(user);
    }

    /** Rotates the refresh token: the old one is revoked and a new pair is returned with current roles. */
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        Instant now = tokens.now();
        RefreshToken stored = refreshTokens.findByTokenHash(TokenService.hash(refreshToken))
                .filter(t -> t.isActive(now))
                .orElseThrow(() -> ApiException.unauthorized("Refresh token is invalid or expired"));
        stored.revoke(now);
        User user = users.findById(stored.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("Refresh token is invalid or expired"));
        return tokens.issue(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByTokenHash(TokenService.hash(refreshToken)).ifPresent(t -> t.revoke(tokens.now()));
    }

    @Transactional(readOnly = true)
    public User get(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    @Transactional(readOnly = true)
    public Page<User> list(Pageable pageable) {
        return users.findAll(pageable);
    }

    @Transactional
    public User grant(UUID userId, Role role) {
        User user = get(userId);
        user.grant(role);
        return user;
    }

    @Transactional
    public User revoke(UUID userId, Role role) {
        User user = get(userId);
        user.revoke(role);
        return user;
    }
}
