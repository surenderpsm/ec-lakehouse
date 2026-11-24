package com.eclakehouse.auth.web;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.eclakehouse.auth.user.Role;
import com.eclakehouse.auth.user.User;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(@NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 200) String fullName) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record RoleRequest(@NotNull Role role) {
    }

    public record UserResponse(UUID id, String email, String fullName, Set<Role> roles, Instant createdAt) {
        public static UserResponse of(User u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), Set.copyOf(u.getRoles()),
                    u.getCreatedAt());
        }
    }
}
