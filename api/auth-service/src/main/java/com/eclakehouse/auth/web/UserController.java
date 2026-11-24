package com.eclakehouse.auth.web;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.eclakehouse.auth.user.AuthService;
import com.eclakehouse.auth.user.Role;
import com.eclakehouse.auth.web.AuthDtos.RoleRequest;
import com.eclakehouse.auth.web.AuthDtos.UserResponse;
import com.eclakehouse.common.security.CurrentUser;
import com.eclakehouse.common.web.PageResponse;

import jakarta.validation.Valid;

@RestController
public class UserController {

    private final AuthService auth;

    public UserController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/api/v1/users/me")
    public UserResponse me() {
        return UserResponse.of(auth.get(CurrentUser.id()));
    }

    @GetMapping("/api/v1/admin/users")
    public PageResponse<UserResponse> list(Pageable pageable) {
        return PageResponse.of(auth.list(pageable), UserResponse::of);
    }

    @GetMapping("/api/v1/admin/users/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return UserResponse.of(auth.get(id));
    }

    @PostMapping("/api/v1/admin/users/{id}/roles")
    public UserResponse grant(@PathVariable UUID id, @Valid @RequestBody RoleRequest req) {
        return UserResponse.of(auth.grant(id, req.role()));
    }

    @DeleteMapping("/api/v1/admin/users/{id}/roles/{role}")
    public UserResponse revoke(@PathVariable UUID id, @PathVariable Role role) {
        return UserResponse.of(auth.revoke(id, role));
    }
}
